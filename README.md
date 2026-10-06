# noname.fm

A small Spring Boot app that scans a local folder of MP3s and streams them to a browser player
(SoundManager 2 + bar-ui). Seeking works through HTTP Range requests. There is no database.

## Requirements

- JDK 25 or newer (built and tested on JDK 27)
- Maven 3.9+
- Spring Boot 3.5 (managed by the parent POM)

## Configuration

Edit `src/main/resources/application.properties` before the first run:

| Property | Meaning |
|----------|---------|
| `archive.dir` | Root folder scanned recursively for MP3s. **Must exist**, or startup fails. |
| `default.cover` | Fallback artwork file, relative to the working directory. |
| `service.location`, `play.service`, `img.service` | Absolute `http://localhost:8080/...` URLs placed in responses. Change them if the host or port changes. |

Any property can also be overridden on the command line, e.g. `--archive.dir=D:\Music`.

## Build and run

```
mvn clean install
mvn spring-boot:run
```

Or run the packaged jar:

```
java -jar target/noname.fm-0.0.1-SNAPSHOT.jar --archive.dir=D:\Music
```

Then open <http://localhost:8080/>. The login page is a stub and accepts any username and password.
You can also go straight to <http://localhost:8080/player>.

If you use IntelliJ, import the project as a Maven project and run `StreamerApplication`.

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `/` | Login page (`POST /` ignores the credentials and returns the player) |
| `/player` | The player UI (`templates/playerV2.html`) |
| `/listen?id=` | Streams a track, with Range support |
| `/nextTrack` | Returns a random track id |
| `/trackInfo?id=` | JSON metadata plus absolute `audioTrack` and `picture` URLs |
| `/cover?id=` | Embedded artwork as JPEG |
| `/getMusicCollection` | Full track list as JSON |

A track's id is its index in the scanned list, so ids are only stable until the next restart or rescan.

## Project layout

- `utils/MusicCollection` scans `archive.dir` at startup and reads ID3 tags with jaudiotagger.
- `controller/` holds thin HTTP layers over `MusicCollection`.
- `utils/MultipartFileSender` and the stream helpers in `utils/` implement range streaming.
- `src/main/resources/static` and `templates/` hold the frontend. `player.html` and `js/player.js` are the older player.

There are no tests yet.
