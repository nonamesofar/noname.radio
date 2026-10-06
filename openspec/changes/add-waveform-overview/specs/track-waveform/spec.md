# Spec Delta

## Purpose

Shows the loudness shape of the whole track in the player, like the overview waveform in DJ software, so listeners can see where the track is going and seek by clicking it.

## ADDED Requirements

### Requirement: Waveform endpoint
The system SHALL serve `GET /waveform?id=N` for any valid track id. It SHALL respond with HTTP 200, `Content-Type: application/json`, and the body `{"bins":[...]}`, where `bins` holds exactly 1000 integers from 0 to 255 in playback order. Bin `i` covers the slice of the track from `i/1000` to `(i+1)/1000` of its duration.

#### Scenario: Waveform for a valid track
- **WHEN** a client requests `GET /waveform?id=3` and track 3 is a readable MP3
- **THEN** the response is 200 with JSON `{"bins":[...]}`, and `bins` has length 1000 with every value an integer between 0 and 255

#### Scenario: Short track
- **WHEN** the track is shorter than 1000 decoded MP3 frames (about 26 seconds)
- **THEN** the response still contains exactly 1000 bins

### Requirement: Waveform values are normalized loudness
Each bin SHALL represent the loudest short-term RMS level (about 26 ms windows) inside its slice. The values SHALL be scaled so that the loudest window in the track maps to 255. A track that is entirely silent SHALL return 1000 zeros.

#### Scenario: Loudest part reaches the top
- **WHEN** the waveform of a track with any non-silent audio is requested
- **THEN** the maximum value in `bins` is 255

#### Scenario: Quiet intro is lower than the main section
- **WHEN** a track has a quiet intro followed by a loud section
- **THEN** the bins covering the intro have lower values than the bins covering the loud section

#### Scenario: Silent track
- **WHEN** every decoded sample of the track is zero
- **THEN** `bins` contains 1000 zeros

### Requirement: Waveform errors
The system SHALL respond with HTTP 404 when the id is not a valid index into the current collection. It SHALL respond with HTTP 500 when the file cannot be decoded, and SHALL NOT cache that failure.

#### Scenario: Unknown id
- **WHEN** a client requests `GET /waveform?id=999999` and the collection has fewer tracks
- **THEN** the response is 404

#### Scenario: Negative id
- **WHEN** a client requests `GET /waveform?id=-1`
- **THEN** the response is 404

#### Scenario: Undecodable file
- **WHEN** the track's file cannot be decoded as MP3 audio, or yields zero frames
- **THEN** the response is 500, and a later request tries to decode the file again

### Requirement: Waveform cache persists across restarts
The system SHALL compute a track's waveform at most once per version of its file. It SHALL store the result in the folder named by the `waveform.cache.dir` property (default `waveform-cache`). Cache entries SHALL be keyed by the file's absolute path and last-modified time, not by track id.

#### Scenario: Second request is served from cache
- **WHEN** the waveform for a track is requested twice
- **THEN** the audio is decoded only for the first request, and both responses have identical bodies

#### Scenario: Restart with a renumbered collection
- **WHEN** the app restarts and a track ends up with a different id
- **THEN** requesting the waveform under its new id returns the cached result without decoding

#### Scenario: File was replaced
- **WHEN** a track's file is modified after its waveform was cached
- **THEN** the next request decodes the new file and caches a new result

#### Scenario: Cache folder does not exist
- **WHEN** the folder named by `waveform.cache.dir` does not exist
- **THEN** the system creates it on the first waveform request

### Requirement: Concurrent requests decode once
When several requests for the same uncached track arrive at the same time, the system SHALL decode the file once and return the same result to all of them.

#### Scenario: Two simultaneous first requests
- **WHEN** two requests for the same uncached track arrive at the same time
- **THEN** the file is decoded once and both requests receive identical 200 responses

### Requirement: Player shows the waveform in place of the progress bar
While a track is selected, the player SHALL draw its waveform across the full width of the progress track, as vertical bars centered on the track's horizontal midline. Bars before the playback position SHALL be drawn in a brighter color than bars after it. The display SHALL update as playback advances.

#### Scenario: Waveform appears for the current track
- **WHEN** a track starts playing and its waveform loads successfully
- **THEN** the progress track shows that track's waveform, and the part already played is brighter than the rest

#### Scenario: Waveform follows playback
- **WHEN** playback advances
- **THEN** the boundary between bright and dim bars moves with the playback position

#### Scenario: Track changes
- **WHEN** the player moves to another track (next, previous, end of track, or a playlist click)
- **THEN** the old waveform is cleared right away and the new track's waveform is shown once it loads

#### Scenario: Late response for an old track
- **WHEN** a waveform response arrives after the player has already moved on to another track
- **THEN** that response is ignored and does not replace the current track's display

#### Scenario: Window resize
- **WHEN** the browser window is resized
- **THEN** the waveform is redrawn to fit the new width without blur

### Requirement: Seeking on the waveform
Clicking or dragging on the waveform SHALL seek playback to the matching position, exactly as clicking or dragging the progress track did before.

#### Scenario: Click to seek
- **WHEN** the user clicks the waveform 75% of the way across while a track is playing
- **THEN** playback jumps to 75% of the track's duration, and the bright part of the waveform extends to that point

### Requirement: Fallback without a waveform
If the waveform request fails, or the response is not a valid bins array, the player SHALL keep working with a plain progress bar and SHALL NOT show an error to the user.

#### Scenario: Waveform request fails
- **WHEN** `/waveform` returns an error for the current track
- **THEN** the player shows the standard filled progress bar, and playback and seeking work normally
