# Proposal

## Why

The player only shows a thin progress bar, so a listener can't see the shape of a track: where the breakdown is, where the drop hits, how far away the next loud section is. DJ software such as Serato and Traktor solves this with a waveform of the whole track. We want the same thing, starting with the simplest useful version: a single-color overview waveform that replaces the progress bar.

Separately, the server builds absolute `http://localhost:8080/...` URLs from properties, and the frontend hardcodes them too. Nothing needs them, since the page, audio and API are all on one origin. They break as soon as the app is opened on another host or port, and they would make the waveform work cross-origin for no reason. They are removed in the same change because the waveform touches the same code paths (`/trackInfo` and `bar-ui.js`).

## What Changes

- **New endpoint `GET /waveform?id=N`** returns `{"bins":[...]}`: 1000 integers from 0 to 255 describing the loudness envelope of the whole track.
  - The server decodes the MP3 with JLayer, a pure-Java decoder (new Maven dependency).
  - It computes the envelope the first time a track is requested.
  - The result is cached on disk, keyed by file path and modified time, so it survives restarts and the rescans that renumber ids.
- **`/trackInfo` gains a `waveform` field** holding the waveform URL for that track.
- **The bar-ui progress track becomes a waveform.** A `<canvas>` draws the bins as bars. The played part is brighter than the rest, and the existing click and drag seek on the track keeps working. The track gets taller to fit the waveform. If the waveform can't be loaded, the player falls back to the plain progress bar.
- **BREAKING (internal): relative URLs only.**
  - `/trackInfo` returns `audioTrack`, `picture` and `waveform` as root-relative paths (`/listen?id=N`, `/cover?id=N`, `/waveform?id=N`).
  - The properties `service.location`, `play.service` and `img.service` are removed.
  - The unused (and broken) `/servicelocation` endpoint is removed.
  - `bar-ui.js` calls `/nextTrack` and `/trackInfo` with relative URLs.
- **New property `waveform.cache.dir`** (default `waveform-cache`, relative to the working directory).

Out of scope:
- A scrolling or zoomed deck view.
- Coloring the waveform by frequency band.
- Changes to the legacy `player.html` and `player.js`, which keep their hardcoded URLs.

## Capabilities

### New Capabilities

- `track-waveform`: Computing, caching and serving a track's overview waveform, and showing it in the player as a seekable progress display.
- `track-info`: The track metadata API (`/nextTrack`, `/trackInfo`) and the shape of the links it returns.

### Modified Capabilities

None. There are no existing specs.

## Impact

- **Backend:**
  - New `WaveformService` (decode, bin, cache) and `WaveformController`.
  - `TrackInfoController` changes to relative links plus the `waveform` field.
  - `WebController` loses `serviceLocation` and `/servicelocation`.
- **Dependencies:** adds `com.googlecode.soundlibs:jlayer:1.0.1.4` (LGPL, pure Java).
- **Config:**
  - `application.properties` loses three URL properties and gains `waveform.cache.dir`.
  - A new `.gitignore` ignores the cache folder.
- **Frontend:** `bar-ui.js` (relative URLs, waveform loading and drawing) and `bar-ui.css` (taller track, waveform styling).
- **Docs:** the Configuration section of `CLAUDE.md`.
- **Runtime cost:** decoding takes roughly 1–3 seconds of CPU the first time each track is played. After that it's a disk read of about 4 KB per track.
