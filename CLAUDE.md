# SCS2 Fork — Claude Code Guidelines

## Project Overview

This is the **seabasstea fork** of [ihmcrobotics/simulation-construction-set-2](https://github.com/ihmcrobotics/simulation-construction-set-2), a JavaFX-based robot data visualizer. The fork adds Windows MSI packaging, auto-update, and UI enhancements on top of upstream.

## Repository Structure

- This repo (`simulation-construction-set-2/`) is a **separate git repo** nested inside `repository-group/`.
- `repository-group/` uses Gradle composite builds to include this project — it is NOT a git submodule.
- **Origin:** `seabasstea/simulation-construction-set-2` (fork)
- **Upstream:** `ihmcrobotics/simulation-construction-set-2`

## Versioning

Fork uses a **4-segment version** derived from upstream: `17-<upstream>.<fork-build>`

- Example: upstream is `17-0.32.1`, fork becomes `17-0.32.1.1`, `17-0.32.1.2`, etc.
- After syncing with a new upstream release (e.g. `17-0.33.0`), reset to `17-0.33.0.1`.
- The 4th segment ensures: fork > upstream base, but fork < next upstream release.
- Version lives in `group.gradle.properties`.
- The auto-update feature (`SCS2VersionChecker`) uses numeric comparison across all segments, so this scheme works without code changes.

## Upstream Sync

- Upstream remote is already configured: `git remote -v` shows `upstream` pointing to `ihmcrobotics/simulation-construction-set-2`.
- To sync: `git fetch upstream && git merge upstream/develop`
- Conflicts in `group.gradle.properties` are expected — always keep the fork version and bump the 4th segment.

## Build

- From `repository-group/`: `./gradlew compileJava`
- Module-specific: `./gradlew :simulation-construction-set-2:scs2-session-visualizer-jfx:compileJava`
- MSI installer: `./gradlew buildWindowsMsiPackage` (requires Windows + WiX Toolset 3.x on PATH)

## Key Conventions

- FXML URLs are registered as static constants in `SessionVisualizerIOTools.java`.
- Controllers implement `VisualizerController` and are initialized with `SessionVisualizerWindowToolkit`.
- The `SCS2VersionChecker` class manages version checking, GitHub API calls, and release asset URLs — all version/update logic goes through it.
- OkHttp and Gson are already dependencies in `scs2-session-visualizer-jfx`.

## Project Plan

Track ongoing work in `SCS2-Claude-Project-Plan.md` at the repo root.
