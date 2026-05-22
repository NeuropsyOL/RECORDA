# Changelog

All notable changes to RECORDA will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.4] – 2026-05-22

### Added
- **Dark theme support** – RECORDA now follows the system-wide dark/light mode setting. Dark-mode colours are aligned with the companion app [SENDA](https://github.com/NeuropsyOL/SENDA).
- **Configurable save location** – users can now choose the destination folder for recorded XDF files via a folder picker in the Settings dialog. The chosen location is persisted across app restarts.
- **In-app tutorial** – a step-by-step guide is available via the new tutorial button in the main toolbar, helping new users get started quickly.

### Fixed
- App crash caused by a missing runtime permission request.
- Release APK signing now uses GitHub repository secrets for secure, reproducible builds.

### Changed
- GitHub Actions CI workflows refactored: reduced code duplication, fixed branch name references, and bumped all action versions to their latest supported releases.

---

## [1.3] – 2023-12-12

### Added
- Quality monitoring for recorded LSL streams (good / laggy / bad states with colour highlighting and heads-up notifications).
- Support for both regular and irregular LSL streams in quality checks.
- Configurable thresholds for laggy/bad detection (timeout and sampling-rate deviation).

### Fixed
- Incorrect stream-list indexing when scrolling (quality-indicator updates).

---

## [1.2] – 2023

### Added
- Settings dialog with optional custom recording name.
- Automatic unique file-name generation if no name is provided.

---

## [1.1] – 2022

### Added
- Initial public release of RECORDA.
- Detect and record LSL streams to XDF format on Android 8.0+.
- Refresh button to rescan the network for available streams.
- Start/Stop recording with on-device file-finalisation notification.

