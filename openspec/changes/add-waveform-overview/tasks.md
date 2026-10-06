# Tasks

## 1. Relative URLs and removing URL properties

- [x] 1.1 In `TrackInfoController`, remove the `playService` and `imgService` `@Value` fields. Build `audioTrack` as `"/listen?id=" + id` and `picture` as `"/cover?id=" + id`. Add a `waveform` field and getter to `TrackInfoUI`, set to `"/waveform?id=" + id`, and add the matching constructor parameter. Verify that `mvn -q compile` succeeds.
- [x] 1.2 In `WebController`, remove the `serviceLocation` field, its `@Value`, the `/servicelocation` method, and any imports that become unused. Verify that `mvn -q compile` succeeds.
- [x] 1.3 In `src/main/resources/application.properties`, delete the `service.location`, `play.service` and `img.service` lines. Keep the local `archive.dir` value exactly as it is, since it's an uncommitted user edit. Verify with `git diff` that only those three lines were removed.
- [x] 1.4 In `static/js/bar-ui.js`, change `getNextTrack()` to call `"/nextTrack"` and `loadTrack(index)` to call `"/trackInfo?id=" + index`. Verify with `grep -n "localhost" src/main/resources/static/js/bar-ui.js` that nothing matches.
- [x] 1.5 Update the Configuration section of `CLAUDE.md`: remove the bullet about `service.location`, `play.service` and `img.service`, and say the app uses relative URLs. Also correct the overview line to Spring Boot 3.5 / Java 25. Verify by re-reading the section.
- [x] 1.6 Run `mvn spring-boot:run` and open `http://localhost:8080/player`. Check that tracks load, play and show cover art, and that the browser's network tab shows `/trackInfo` returning `"audioTrack":"/listen?id=N"`, `"picture":"/cover?id=N"` and `"waveform":"/waveform?id=N"`. The waveform URL is a 404 for now. Also open the page as `http://127.0.0.1:8080/player` and check that it still works.

## 2. Waveform service (decode, bins, cache)

- [x] 2.1 Add `com.googlecode.soundlibs:jlayer:1.0.1.4` to `pom.xml`. If it doesn't resolve, use `javazoom:jlayer:1.0.1`; see design decision 1. Verify that `mvn -q dependency:resolve` succeeds and that `javazoom.jl.decoder.Bitstream` can be imported.
- [x] 2.2 Create `noname/fm/utils/WaveformService.java` (`@Service`) with a `public static int[] computeBins(float[] levels, int count, int binCount)` that implements the bin mapping, max and normalization from design decision 2. Add `src/test/java/noname/fm/utils/WaveformServiceBinsTest.java` (plain JUnit 5) covering:
  - 1000 bins out for 11,500 levels
  - 1000 bins out for 300 levels (fewer than bins)
  - all-zero levels give all zeros
  - the maximum output is 255
  - a quiet first half followed by a loud second half gives lower values in the first 500 bins

  Verify that `mvn test` passes.
- [x] 2.3 Add a JSON builder in `WaveformService` that turns `int[]` into the bytes of `{"bins":[a,b,...]}` (UTF-8, no spaces). Add a test that parses its output with Jackson's `ObjectMapper` and checks there are 1000 values. Verify that `mvn test` passes.
- [x] 2.4 Implement `protected float[] decodeFrameLevels(File mp3)` (plus a count, for example through a small result record) with JLayer, following design decision 2: one RMS value per frame, skip a frame on `DecoderException`, stop on `BitstreamException`, throw `WaveformException` (a new `RuntimeException` in `noname.fm.utils`) when there are zero frames, and close the streams in try-with-resources. Log the file name, frame count and milliseconds at INFO. Check that a stereo frame gives `getBufferLength() == 2304`, and adjust if not (see Risks). Verify by temporarily calling it on one real MP3 from `archive.dir` and checking the log reports about 38 frames per second of audio. Remove that temporary call afterwards.
- [x] 2.5 Implement `public byte[] getWaveformJson(File mp3)` with the disk cache from design decision 3: `@Value("${waveform.cache.dir:waveform-cache}")`, a SHA-256 key over path, `lastModified` and `|v1`, folders created lazily, write to a temp file then atomic move, and a write failure only logged. Add the single-flight map from design decision 4. Add `src/test/java/noname/fm/utils/WaveformServiceCacheTest.java`. It should use a subclass that overrides `decodeFrameLevels` to return fixed levels and count calls, a `@TempDir` as the cache folder (set through a constructor or setter so the test doesn't need Spring), and a temp file standing in for the MP3. Cover:
  - a second call doesn't decode, and the bytes are equal
  - a new service instance on the same folder doesn't decode (restart)
  - changing the file's `lastModified` decodes again
  - a missing cache folder is created
  - two threads calling at once (use a `CountDownLatch` inside the fake decode) decode once and get equal bytes
  - a decode that throws isn't cached, so the next call decodes again

  Verify that `mvn test` passes.
- [x] 2.6 Add `waveform.cache.dir=waveform-cache` to `application.properties`. Create `.gitignore` at the repo root containing `waveform-cache/`. Add a `waveform.cache.dir` bullet to the Configuration section of `CLAUDE.md`. Verify with `git status` that the cache folder doesn't show up after a run.

## 3. Waveform endpoint

- [x] 3.1 Create `noname/fm/controller/WaveformController.java` with `GET /waveform?id=` following design decision 5:
  - 404 when `id < 0` or `id >= collection size`
  - 200 with `Content-Type: application/json`, `Cache-Control: no-cache` and the service's bytes
  - on `WaveformException`, log at WARN and return 500 with an empty body

  Verify that `mvn -q compile` succeeds.
- [x] 3.2 Add `src/test/java/noname/fm/controller/WaveformControllerTest.java` using `@WebMvcTest(WaveformController.class)`, with `@MockitoBean` for `MusicCollection` and `WaveformService`. Cover 404 for `id=-1`, 404 for `id=size`, 200 with the JSON content type and the body passed through, and 500 when the service throws. Verify that `mvn test` passes.
- [x] 3.3 Add `/waveform?id=` to the controller list in the Architecture section of `CLAUDE.md`, and remove its "There are no tests" statements. Verify by re-reading the section.
- [x] 3.4 Run the app and open `http://localhost:8080/waveform?id=0` twice. The first time should log a decode, and the second time shouldn't. Then restart the app and request it again: there should be no decode log, and a `.json` file should exist in `waveform-cache/`.

## 4. Waveform in the player

- [x] 4.1 In `templates/playerV2.html`, add `<canvas class="sm2-waveform"></canvas>` as the first child of `.sm2-progress-track`. Verify the page still renders and plays.
- [x] 4.2 In `static/css/bar-ui.css`, apply the CSS from design decision 7:
  - split the track and bar height (2em, radius 0.2em) out of the shared 0.65em rule
  - make the `.sm2-progress` row tall enough for the taller track
  - center the ball vertically on the track
  - add the `.sm2-waveform` absolute fill with `pointer-events: none`
  - add the `.has-waveform` rule that makes the progress bar's fill transparent

  Verify in the browser that the taller bar, with the ball centered, still fills as the track plays, and that the buffering spinner still shows on the ball.
- [x] 4.3 In `static/js/bar-ui.js`, make `addTrackToPlaylist` write `href="..."` and `data-waveform="..."`, both quoted, using `song.audioTrack` and `song.waveform`. Verify in the inspector that the new playlist `<a>` elements carry `data-waveform`.
- [x] 4.4 In the `Player` closure, add `waveformState`, `dom.waveform` (looked up in init), `loadWaveform(item)` (called at the end of `setTitle`, async `jQuery.ajax`, ignoring stale responses, and adding or removing `has-waveform`), and `drawWaveform()` (sized for `devicePixelRatio`, 2px bars with 1px gaps, a bright played part and a dim rest, as in design decision 7). In `whileplaying`, store the clamped fraction and call `drawWaveform()`. Add a `window` resize listener that redraws. Keep the file's ES5 style. Verify in the browser that the waveform appears within a few seconds of a new track starting, and that the bright part moves with playback.
- [x] 4.5 Check in the browser:
  - clicking 75% across the waveform seeks to about 75%, and dragging works
  - next, previous and end of track clear the waveform and load the new one
  - pressing next twice quickly ends with the waveform of the track that's actually playing
  - after a resize, the bars aren't blurry or stretched
  - with `/waveform` forced to fail (for example, temporarily point `archive.dir` at a folder containing a renamed non-MP3 `.mp3`, or block the URL in devtools), the plain progress bar shows and seeking works

## 5. Integration check

- [x] 5.1 Run `mvn clean install` and check it succeeds with all tests green.
- [x] 5.2 Start with `mvn spring-boot:run` and play three tracks end to end at `http://127.0.0.1:8080/player`. Each should show a waveform, there should be no console errors, and the network tab should show no requests to `localhost:8080`.
