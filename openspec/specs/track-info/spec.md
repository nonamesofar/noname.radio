# Track Info Specification

## Purpose

Lets the player pick tracks and look up their metadata, with links to the audio stream, cover art and waveform for each one.

## Requirements

### Requirement: Track info response
`GET /trackInfo?id=N` SHALL return JSON with the fields `artist`, `album`, `title`, `audioTrack`, `picture` and `waveform`. `audioTrack` SHALL be `/listen?id=N`, `picture` SHALL be `/cover?id=N`, and `waveform` SHALL be `/waveform?id=N`.

#### Scenario: Track info links
- **WHEN** a client requests `GET /trackInfo?id=5`
- **THEN** the JSON contains `"audioTrack":"/listen?id=5"`, `"picture":"/cover?id=5"` and `"waveform":"/waveform?id=5"`, along with the track's artist, album and title

### Requirement: Links are root-relative
All links returned by the track metadata API SHALL be root-relative paths with no scheme, host or port. The player SHALL call `/nextTrack` and `/trackInfo` with root-relative URLs, so the app works on whatever host and port it is served from.

#### Scenario: Served on a different host and port
- **WHEN** the app runs on port 9090 and is opened at `http://192.168.1.10:9090/player`
- **THEN** tracks load, play and show cover art and waveform, with no requests going to `localhost:8080`

### Requirement: No URL configuration
The app SHALL NOT need the properties `service.location`, `play.service` or `img.service` to start or run.

#### Scenario: Properties absent
- **WHEN** `application.properties` contains none of `service.location`, `play.service` and `img.service`
- **THEN** the app starts, and `/trackInfo` returns the relative links described above

### Requirement: Random next track
`GET /nextTrack` SHALL return a random valid track id as a plain integer.

#### Scenario: Next track id
- **WHEN** a client requests `GET /nextTrack` and the collection has N tracks
- **THEN** the response body is an integer from 0 to N-1
