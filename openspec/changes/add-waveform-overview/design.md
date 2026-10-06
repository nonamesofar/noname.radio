# Design

## Context

See proposal.md for the motivation and specs/ for the required behavior. This section covers only what shapes the implementation.

**Backend**
- The backend is Spring Boot 3.5 on Java 25. `MusicCollection` holds `List<TrackInfo>`; a track's id is its index in that list, and `TrackInfo.getPath()` is the absolute file path. The other controllers use `@Controller` with `@ResponseBody` and don't validate ids, so a bad id surfaces as a 500 from `IndexOutOfBoundsException`.
- There are no tests and no `src/test` folder, though `spring-boot-starter-test` is already a test dependency.
- `application.properties` has an uncommitted local edit to `archive.dir`. Preserve it.

**Frontend**
- `templates/playerV2.html` with `static/js/bar-ui.js`, a vendored and locally modified SM2 Bar UI.
- In `bar-ui.js`, a track enters the playlist through `loadTrack(index)`, which calls `/trackInfo` synchronously and then `addTrackToPlaylist(song)`, which builds `<li><a href=...>`.
- Every time the selected track changes, `setTitle(item)` is called with the `<li>`. The call sites are init, `playLink`, `onfinish`, and the next/prev actions.
- Progress is drawn in the `whileplaying` callback inside `makeSound` (every 50 ms, from `html5PollingInterval`) as a fraction `position / durationEstimate`.
- Seeking is `handleMouse`, bound to `mousedown`/`touchstart` on `dom.progressTrack` (`.sm2-progress-track`). It calls `whileplaying` by hand after `setPosition`.
- In `bar-ui.css`, `.sm2-progress-track`, `.sm2-progress-bar` and `.sm2-progress-ball` are all 0.65em tall in one shared rule. The ball is a circle that holds the buffering spinner (`.icon-overlay`).

## Goals / Non-Goals

**Goals:**
- Server-side waveform computation that never blocks startup and is cached across restarts.
- The smallest frontend change that turns the progress track into a waveform without touching SM2's seek logic.
- A response format that can later grow extra fields (finer resolution, frequency bands) without breaking clients.

**Non-Goals:**
- Precomputing waveforms during the startup scan or in a background job.
- A binary response format, HTTP caching or ETags.
- Drawing smoothly with `requestAnimationFrame`. At 1000 bins over a few minutes, the 50 ms `whileplaying` updates already move the playhead by less than a pixel each time.
- Cleaning the cache of entries for deleted or changed files. Old entries are about 4 KB each and harmless.

## Decisions

### 1. Decode with JLayer, not ffmpeg
Add `com.googlecode.soundlibs:jlayer:1.0.1.4`, a pure-Java MP3 decoder with no other dependencies.
- **Alternative:** spawn `ffmpeg`. It's faster and handles any format, but every machine would need it on its PATH, which adds a way for this "point at a folder and run" app to fail.
- **Fallback:** if this artifact doesn't resolve, use `javazoom:jlayer:1.0.1`. It has the same API in the `javazoom.jl.decoder` package.

### 2. Measure one RMS level per MP3 frame, then reduce to 1000 bins
Decode in a single pass. For each frame:
- `Bitstream.readFrame()` returns `null` at end of file.
- `(SampleBuffer) decoder.decodeFrame(header, bitstream)` decodes the frame.
- Compute `rms = sqrt(sum(s*s) / n)` over `buffer.getBuffer()[0 .. getBufferLength())`. The samples are interleaved channels; mixing them together is fine for an overview.
- Append the result to a growable `float[]`.
- Call `bitstream.closeFrame()`.

A frame is 1152 samples, about 26 ms, so a 5-minute track gives about 11,500 values. That's tiny, and it means we don't need to know the length before decoding.

**Error handling during decode:**
- If `decodeFrame` throws `DecoderException`, skip that frame (still call `closeFrame()`) and keep going. Corrupt frames are common in real MP3 collections.
- If `readFrame` throws `BitstreamException`, stop decoding and keep the frames read so far.
- If zero frames were read, the decode failed. Throw so the controller returns a 500.

`Bitstream` skips the ID3v2 tag at the start by itself.

**Reduce to bins** with a pure static method, so it can be unit-tested without an MP3. With `F` levels and `B = 1000` bins, for each `i`:
- `start = floor(i*F/B)` and `end = max(start+1, floor((i+1)*F/B))`
- `bin[i] = max(levels[start..end))`

Then normalize: `out[i] = round(bin[i] / maxBin * 255)`. If `maxBin == 0`, every value is 0. When `F < B`, frames are repeated across bins, which is correct.

- **Why RMS rather than peak:** mastered tracks clip near full scale almost everywhere, so a peak waveform is a flat block. RMS shows the musical shape: intros, breakdowns, drops.
- **Why max of the frame RMS values per bin:** it keeps short loud hits visible, the way DJ overviews do.
- **Why normalize per track:** quiet recordings would otherwise look empty. Comparing loudness across tracks is a non-goal.

### 3. Cache: one JSON file per file version, written atomically
- **Key:** `sha256Hex(absolutePath + "|" + file.lastModified() + "|v1")`.
- **File:** `<waveform.cache.dir>/<key>.json`, holding exactly the response body bytes.
- **Folder:** `waveform.cache.dir` defaults to `waveform-cache`, resolved against the working directory. Create it with `Files.createDirectories` on first use, not at startup, so a bad path only breaks waveforms.
- **Version suffix:** the `|v1` lets a future format change (for example, adding bands) invalidate everything by bumping it.
- **On a hit:** return the bytes as they are, with no parsing.
- **On a miss:** compute, write to `<key>.json.tmp` in the same folder, then `Files.move(tmp, final, ATOMIC_MOVE, REPLACE_EXISTING)`. If `ATOMIC_MOVE` isn't supported, catch `AtomicMoveNotSupportedException` and move with only `REPLACE_EXISTING`.
- **If the cache write fails** (for example, a read-only folder): log a warning and still return the computed bytes.

**Response serialization:** build the JSON by hand: `{"bins":[` + comma-joined ints + `]}`. A Jackson DTO isn't needed for one int array, and hand-building keeps the cached bytes and the response byte-identical.

### 4. Single-flight decoding with `ConcurrentHashMap<String, CompletableFuture<byte[]>>`
In `WaveformService.getWaveformJson(File)`:
1. Check the disk cache.
2. On a miss, create a `CompletableFuture` and `putIfAbsent(key, future)` into the in-flight map.
   - If another future was already there, `join()` it.
   - Otherwise this thread decodes and caches, then completes the future normally or exceptionally. In a `finally`, remove the key.

Decoding runs on the request thread, so no executor is needed.

When a waiter's `join()` fails, it throws `CompletionException`. Unwrap it and rethrow as the service's own `WaveformException`, a `RuntimeException`.

### 5. Controller contract
A new `WaveformController` with `@GetMapping("/waveform") ResponseEntity<byte[]> waveform(@RequestParam int id)`:
- If `id < 0 || id >= mCollection.getCollection().size()`, return 404.
- Otherwise call `waveformService.getWaveformJson(new File(track.getPath()))` and return 200 with `Content-Type: application/json` and `Cache-Control: no-cache`.
  - **Why `no-cache`:** ids change when the collection is rebuilt, so the browser must not reuse a body for `?id=N` across restarts. The disk cache keeps repeat requests cheap anyway.
- On `WaveformException`, log at WARN with the path and return 500 with an empty body.

Classes:
- `noname/fm/utils/WaveformService.java`: an `@Service` with the decode, bins, cache and single-flight logic. Make `computeBins(float[] levels, int count, int binCount)` `static` and package-private (or public) for tests.
- `noname/fm/controller/WaveformController.java`.

Use SLF4J (`LoggerFactory.getLogger`). Log at INFO when a decode finishes, with the file name, frame count and milliseconds.

### 6. Relative URLs
- **`TrackInfoController`:** drop the `playService` and `imgService` `@Value` fields. Build `"/listen?id=" + id`, `"/cover?id=" + id` and `"/waveform?id=" + id`. Add a `waveform` field and getter to the inner `TrackInfoUI`; its constructor gains one parameter.
- **`WebController`:** remove the `serviceLocation` field, its `@Value`, and the `getServiceLocation()` endpoint. Remove the imports that become unused.
- **`bar-ui.js`:** change `getNextTrack` and `loadTrack` to use `"/nextTrack"` and `"/trackInfo?id=" + index`. `canPlayURL` (`link.includes("listen")`) still works with relative links. `showTrackArt` compares against `'assets/img/default.png'`, which never matched the server URLs anyway, so leave it alone.

### 7. Frontend: canvas inside the progress track, drawn from `whileplaying`
**Markup (`playerV2.html`):** add `<canvas class="sm2-waveform"></canvas>` as the first child of `.sm2-progress-track`. Because it sits inside the track, the existing `mousedown`/`touchstart` seek handler fires for clicks on it, and `handleMouse` measures against the track's width. Seeking needs no new code.

**CSS (`bar-ui.css`):**
- Give `.sm2-progress-track` and `.sm2-progress-bar` a height of `2em` and a border-radius of `0.2em`. Split them out of the shared 0.65em rule; the ball keeps its own size.
- `.sm2-progress` (the row) needs enough height for this. The player bar gets about 1em taller, which is accepted.
- Center the ball vertically on the track: `top: 50%` plus the matching negative `margin-top` (half the ball's height). It stays the playhead marker and keeps the buffering spinner.
- `.sm2-waveform`: `position: absolute; left: 0; top: 0; width: 100%; height: 100%; display: block; pointer-events: none;`
- `.sm2-bar-ui.has-waveform .sm2-progress .sm2-progress-bar { background-color: transparent; }`. With a waveform, the canvas shows played and unplayed, so the dark fill must not cover it. Without one, the fill shows as before.

**JS (`bar-ui.js`):** all of this lives inside the `Player` closure, next to the other `dom` and `soundObject` state.
- **Storing the URL:** `addTrackToPlaylist` writes the URL on the link: `<a href="..." data-waveform="...">`. Quote both attributes.
- **State:** `waveformState = { url: null, bins: null, fraction: 0 }`. Add `dom.waveform` (the canvas), looked up in init next to `dom.progressTrack`.
- **`loadWaveform(item)`**, called at the end of `setTitle(item)`, which already runs on every track change:
  - Read `data-waveform` from the item's first `<a>`.
  - Set `waveformState.url` to it, `bins = null`, `fraction = 0`. Remove `has-waveform` from `dom.o` and clear the canvas.
  - If there's a URL, fetch it with `jQuery.ajax({ url, dataType: 'json' })`. Make it async, unlike the existing calls, because a decode can take seconds.
  - On success, drop the response if `waveformState.url !== url` (a stale response). Otherwise check that it's an array of length > 0, store the bins, add `has-waveform`, and draw.
  - On error, do nothing. The plain bar stays.
- **Following playback:** in `makeSound`'s `whileplaying`, after the existing progress code, set `waveformState.fraction` to the same fraction (`position / durationEstimate`, clamped to 0–1) and call `drawWaveform()`.
- **`drawWaveform()`:**
  - If there are no bins, return.
  - Size the backing store to `cssWidth * devicePixelRatio` by `cssHeight * devicePixelRatio`, only when it changed, and scale the context to match.
  - Clear. Draw `barCount = floor(cssWidth / 3)` bars, each 2 CSS pixels wide with a 1 pixel gap.
  - Bar `k` takes the max of bins `floor(k*n/barCount) .. max(start+1, floor((k+1)*n/barCount))`.
  - Height is `max(1, value/255 * cssHeight * 0.9)`, centered vertically.
  - Fill `rgba(255,255,255,0.95)` if the bar's left edge is before `fraction * cssWidth`, otherwise `rgba(255,255,255,0.35)`.
- **Resize:** `window` `resize` calls `drawWaveform()`.
- Keep to the file's existing ES5 style (`var`, function declarations).

- **Alternative considered:** replacing SM2's progress elements with a separate waveform widget. Rejected because it would mean rewriting seek, drag and buffering handling that already works.

## Risks / Trade-offs

- **[JLayer is old and unmaintained]** It's frozen, but MP3 is a frozen format too. A file JLayer can't decode gets a 500 and the plain bar.
- **[First play of a track waits on a 1–3 s decode]** The request is async and the bar works meanwhile. The waveform appears when ready.
- **[Many people hitting new tracks at once tie up Tomcat threads while decoding]** This is a personal, single-user app, so it's accepted.
- **[JLayer's `SampleBuffer.getBufferLength()` semantics]** It returns the count of interleaved samples written for the frame. The implementer should check that a stereo frame gives 2304 (2 × 1152). If it gives 1152, sum over `channels × length` instead.
- **[VBR or odd files make frame count differ from duration slightly]** The overview is approximate by nature, and small drift is invisible at 1000 bins.
- **[Changing `TrackInfoUI`'s JSON]** Adding a field is backward compatible. Switching to relative URLs only affects `bar-ui.js`, which is updated in the same change. The legacy `player.js` builds its own URLs and doesn't read these fields.

## Migration Plan

- Remove the three URL properties from `application.properties`, add `waveform.cache.dir=waveform-cache`, and add `.gitignore` with `waveform-cache/`.
- Rollback is reverting the change. The cache folder can be deleted at any time.
