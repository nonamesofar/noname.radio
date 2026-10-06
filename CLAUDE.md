# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

noname.fm: a small Spring Boot 3.5 / Java 25 (Maven) app that scans a local folder of MP3s and streams them to a browser player (SoundManager 2 + bar-ui). Tests (JUnit 5) live in `src/test`.

## Commands

- Build: `mvn clean install`
- Run: `mvn spring-boot:run`, then open `http://localhost:8080/` (login page; any username/password is accepted) or `/player` directly.
- Run tests: `mvn test`; a single test class: `mvn test -Dtest=WaveformServiceBinsTest`.

## Configuration

`src/main/resources/application.properties` holds machine-specific values that must be edited before running:
- `archive.dir` — root folder scanned recursively for MP3s (currently a hardcoded Windows path; startup fails if it doesn't exist).
- `default.cover` — fallback artwork file path, relative to the working directory.
- The app uses root-relative URLs (`/listen`, `/cover`, `/waveform`, `/trackInfo`, `/nextTrack`), so no host/port configuration is needed.
- `waveform.cache.dir` — folder for cached waveform JSON (default `waveform-cache`, relative to the working directory; git-ignored, safe to delete).

## Architecture

- `utils/MusicCollection` (`@Service`) is the core. On `@PostConstruct` it recursively scans `archive.dir`, reads ID3 tags with jaudiotagger, and builds in-memory `TrackInfo` and artwork lists. A track's **id is its index in that list**; all endpoints refer to tracks by this index, so ids are only stable until the next restart/rescan. No database.
- Controllers (`controller/`) are thin HTTP layers over `MusicCollection`:
  - `StreamController` `/listen?id=` — streams audio via `utils/MultipartFileSender` (HTTP Range support for seeking). `ThrottledInputStream`, `LimitedInputStream`, `CombinedInputStream` are stream helpers in `utils/`.
  - `TrackInfoController` `/nextTrack` (random id) and `/trackInfo?id=` (JSON with metadata plus root-relative `audioTrack`, `picture` and `waveform` URLs).
  - `ArtworkController` `/cover?id=` — embedded artwork as JPEG.
  - `WaveformController` `/waveform?id=` — `{"bins":[1000 ints 0-255]}` loudness overview, computed by `utils/WaveformService` (JLayer decode, disk-cached by file path + mtime, single-flight).
  - `MusicCollectionController` `/getMusicCollection` — full track list as JSON.
  - `WebController` — serves Thymeleaf templates (`login`, `playerV2`).
- Frontend lives in `src/main/resources/static` (js/css, vendored `soundmanager2.js`, `bar-ui.js`, jQuery 1.11, font-awesome) and `templates/`. The active UI is `templates/playerV2.html` (SoundManager 2 bar-ui); `player.html` and `js/player.js` are the older player and `demo.*` are leftovers from SM2's demo.
- Login is a stub: `POST /` ignores the credentials and returns the player.
