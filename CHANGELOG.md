# Changelog

All notable changes to LlarTV are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions are tagged `vMAJOR.MINOR`.

## [Unreleased]

## [1.1] - 2026-10-29

The lightweight settings release: LlarTV gains its own settings menu, and the weather, the news and the clock
become the owner's choice instead of the TV's.

### Added

- LlarTV settings on the TV: a native menu in the top bar (*Configuració*), navigable with the remote, that
  writes to the same `home.conf` as the ADB scripts. The Android/TV settings stay reachable from it.
- Clock format preference (`clock`): follow the TV, 24-hour or 12-hour with AM/PM, applied to the home screen,
  the screensaver and the next-match card.
- Weather and news controls (`weather_home`, `weather_screen`, `news_home`): never, only in the morning band or all
  day, for the home screen and for the screensaver. What is switched off on every surface is not downloaded any
  more, and a hidden card leaves no empty space.
- News source presets (`news_source`): 3Cat (Catalan), BBC News Mundo (Spanish) and BBC News World (English),
  plus `automatic` (the default), which follows the interface language and resolves to 3Cat until the language
  selector lands in v1.2. The source is credited next to the headlines and the article link is kept in the
  cache.
- The reuse terms of the 3Cat feed recorded in `docs/news-sources.md`, next to the BBC ones: personal and
  private use only, so a wider distribution of the app has to ask the CCMA first.

### Fixed

- Catalan fixes: the settings menu row for the clock is spelled "Rellotge", and the afternoon greeting is the
  Catalan "bon vespre" from 14:00 instead of the Spanishism "bon tarda".

## [1.0] - 2026-10-25

First release.

### Added

- Home screen with a greeting, a large clock and the date, a dock of favourite apps, a full app grid, and
  shortcuts to the TV inputs, the Wi-Fi settings and the system settings, all with predictable D-pad navigation.
- Daily wallpapers from Bing (the last eight images, rotating every 30 minutes by default) or from any image URL.
- Screensaver: the wallpapers with a slow zoom and cross-fade, the clock, the date, the weather and each image's
  title and credit.
- Time-of-day bands:
  - a morning panel (default 08:00–11:00) with the current weather from Open-Meteo and the latest RSS headlines
    (default: 3Cat front page);
  - a night mode (default 23:00–08:00) with a dim, warm palette on the home screen and the screensaver, applied
    gradually when the band changes.
- Dock band tinted with a dark colour taken from the current wallpaper.
- Optional next-match card for a football club.
- Catalan interface text and dates, regardless of the system language.
- Scripts to install, configure and uninstall LlarTV over ADB: `scripts/install.sh`, `scripts/config.sh` and
  `scripts/uninstall.sh`.

[Unreleased]: https://github.com/mmarkimark/llartv/compare/v1.0...HEAD
[1.0]: https://github.com/mmarkimark/llartv/releases/tag/v1.0
