# Day modes: morning panel, night mode, photo-tinted dock, weather on the screensaver

Status: proposed (2026-10-25). Mockup the owner approved: https://claude.ai/artifact/P9ZcefCNsBeQQrRQ8rCyi2 (private).

## Intent

LlarTV runs on a Xiaomi MiTV-MSSP3 (Android TV 11, MediaTek, 1080p) in a bedroom.
The owner wants the home screen to fit the time of day without becoming busy: useful information in the
morning, nothing extra during the day, and a dark, warm screen at night that does not light up the bedroom. They
also want the dock to pick up the colours of the current Bing photo, and the screensaver (BingDream) to show the
weather next to the clock.

The owner values simplicity and speed above features. They rejected Projectivy (too much to configure) and
AT4K (slower), and asked that nothing reorders the dock. Everything runs on the TV itself: no processing on the
owner's Unraid server or any other machine.

## Goals

1. **Three time bands**, by local time on the TV:
   - **Morning, 08:00 to 11:00**: the home screen shows a panel on the right with two cards: the weather and
     the two top news headlines from 3Cat. (A third card with the family Google Calendar was dropped by the
     owner on 2026-10-25.)
   - **Day, 11:00 to 23:00**: the home screen as it is today. No panel.
   - **Night, 23:00 to 08:00 the next day**: the wallpaper is darkened and warmed (in the mockup: brightness
     about 38%, saturation about 55%, a sepia tint), the clock, date and greeting change from white to a soft
     cream, and dock tiles are slightly dimmed. Applies to the home screen and the screensaver.
   - The change between bands happens gradually (about one to two minutes), not as a jump.
2. **Photo-tinted dock band**: the dock band's background takes a dark, translucent colour derived from the
   current wallpaper (in the mockup: the average colour of a 32x18 downscale, scaled so its brightest channel is
   46/255, at about 86% opacity). The focused app's glow keeps using the app's own banner colour, as today.
3. **Weather on the screensaver**: BingDream shows the current temperature and a short condition text under the
   date (mockup: "16° · Assolellat").

## Data sources (decided by the owner)

- **Weather**: Open-Meteo forecast API, no key. Location: La Verneda, Barcelona (about 41.4235 N, 2.2010 E).
  Verified 2026-10-25: `https://api.open-meteo.com/v1/forecast?latitude=41.4235&longitude=2.2010&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=Europe/Madrid&forecast_days=1`
  returns current temperature, WMO weather code, and today's max/min.
- **News**: 3Cat's public RSS of front-page news. Verified 2026-10-25:
  `https://api.3cat.cat/noticies?_format=rss&origen=frontal&frontal=n324-portada-noticia&version=2.0`.
  The feed is encoded ISO-8859-1 (declared in the XML prolog); it must be decoded accordingly. Show the first
  two items: title, and "3Cat · <relative age>" from `pubDate`.
- **Calendar**: dropped by the owner on 2026-10-25 ("Passe'm del calendari").

## Configuration

All new settings live in the existing `home.conf` (see `Config.java`), with these defaults:

- `location=41.4235,2.2010` (latitude,longitude for the weather)
- `news_rss=` the 3Cat URL above
- `morning=08:00-11:00`, `night=23:00-08:00` (band boundaries; an empty value disables that band)
- `dock_tint=on`

A later, separate piece of work will let the owner edit these from a phone browser; this design does not cover it.

## Constraints

- Android 9+ (minSdk 28), Java, no new runtime dependencies; the project uses plain Android views (see
  `HomeActivity`, `BingDream`, `DailyWallpaper`).
- Keep LlarTV's rule of no polling: network fetches happen only while the home screen (or the screensaver)
  is showing, with minimum refresh intervals, and failures retry later (as `maybeFetchWallpaper` does).
- A failed or empty source hides its card; the home screen never shows an error or an empty card.
- The panel must not steal D-pad focus or change navigation: it is display only.
- Must stay light on the MediaTek SoC; no blur, no video.
- The dock order is never changed.
- **Language: everything in Catalan** (owner's decision, 2026-10-25), regardless of the TV's locale (the TV is
  set to Spanish). This covers the new texts (card headings, weather conditions, news age such as "fa 20 min",
  max/min) and also LlarTV's existing text: the greeting ("Bon dia", "Bona tarda", "Bon vespre", "Bona nit"),
  the top pills ("Entrades", "Wi-Fi", "Configuració"), the date on the home screen and the screensaver
  (e.g. "diumenge, 25 d'octubre"), and any other visible LlarTV string. Use the `ca` locale explicitly for
  date formatting rather than the system locale. App names and 3Cat headlines are shown as they come.

## Non-goals

- Self-update from GitHub (dropped by the owner).
- Phone-based configuration page (separate later work).
- A "continue watching" or recommendations row (ruled out earlier: most apps publish nothing).
- Any server-side processing.

## Anti-goals (would be a failure even if it technically works)

- The home screen feels slower or busier outside the morning band.
- The night mode is not dark enough to use in a bedroom, or text becomes hard to read.
- The dock band colour makes the app banners hard to see.
- Any change to dock order or to D-pad navigation.

## Calls made while writing this up (owner can override)

- **Football match card**: the owner has no team configured (`team_id` empty), so the card never shows on this
  TV. If a team is configured, the morning panel takes the match card's place from 08:00 to 11:00 and the card
  returns at 11:00.
- In night mode all on-screen text (hero name, pills, match card) turns cream, as in the mockup.
- Weather is fetched in every band (the screensaver shows it all day); news only in the morning band.
- With no wallpaper, the dock band keeps today's colour.
- BingDream reads the same cached weather that HomeActivity fetched; it does not fetch on its own.

## Left to the builder

- Refresh intervals (suggested: weather every 30 min, news every 30 min, all only while home
  or the screensaver is visible).
- How the gradual band transition is animated (colour filter on the wallpaper view, text colour animation).
- Mapping WMO weather codes to a short condition text.
- Tests: the time-band logic, config parsing, the dock tint maths, the RSS parsing should get JVM unit
  tests in `app/src/test`, like `DailyWallpaperTest`.

## Verification

- Unit tests as above (`./gradlew testDebugUnitTest`).
- On the Android TV 11 emulator (AVD `tv11`; note its shell cannot write `home.conf`, so test builds set defaults
  in code, never committed), and then on the real TV: screenshots of each band on the home screen and the
  screensaver, and a check that D-pad navigation and dock order are unchanged.
