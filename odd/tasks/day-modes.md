Objective: time-of-day modes for LlarTV (morning panel, night mode), photo-tinted dock, weather on the screensaver, all UI in Catalan.
Branch: feat/day-modes (based on feat/screensaver). Delivery: ask-on-risk. Tests: JUnit via `./gradlew testDebugUnitTest`; device checks on AVD `tv11` and the MiTV-MSSP3.
Design: docs/design/day-modes.md (approved by the owner 2026-10-25) is the full specification; the S# below index it.

## Specs

- S1 Time bands by TV local time: "de 08:00 a 11:00h Matí, Després la part de vespre fins les 23:00h i a partir de les 23:00 i fins les 08:00 del dia següent". Configurable `morning=08:00-11:00`, `night=23:00-08:00`. Gradual transition (~1-2 min).
- S2 Morning band (home screen only): right-hand panel with weather and the two top 3Cat headlines. Display only; no focus or navigation change. Failed/empty sources hide their card. (Calendar dropped, see L5.)
- S3 Night band: wallpaper darkened and warmed, text white to cream, dock tiles slightly dimmed; home screen and screensaver.
- S4 Dock band tinted with a dark translucent colour from the current wallpaper; focused-app glow unchanged; `dock_tint=on`.
- S5 Weather on the screensaver under the date (e.g. "16° · Assolellat"), from the cache HomeActivity fills.
- S6 Data sources: Open-Meteo for La Verneda (`location=41.4235,2.2010`); 3Cat RSS (ISO-8859-1) `news_rss`. (Google Calendar dropped, see L5.)
- S7 Language: "Molaria tot en català" — every visible LlarTV string in Catalan (greeting, pills, dates, cards, weather, news age), `ca` locale for dates regardless of the TV locale.
- S8 Never reorder the dock ("No reordenis"); no processing off the TV ("No vull que es faci a l'unraid").

## Tasks

- T1 [S1, S7] TimeBands + Config keys + Catalan strings/dates in HomeActivity and BingDream — route: inline — status: done (736638a; TimeBandsTest, CaTest, ConfigTest green)
- T2 [S6] Data sources: Weather (Open-Meteo + WMO→Catalan text), News (RSS, ISO-8859-1, relative age in Catalan), ICal (VEVENT today, all-day, TZID/UTC), shared cache, JVM tests — route: delegated (writer reason: parallel unit with disjoint new files, launched together with T1) — status: done (WeatherTest 8, NewsTest 6, ICalTest 19 green; parent re-ran full suite 53/53)
- T3 [S3, S4] Night mode visuals (gradual) + photo-tinted dock band — route: inline — status: done (LookTest green; emulator screenshots day/night home and night screensaver)
- T4 [S2, S5] Morning panel UI + weather line on BingDream, wired to T2 — route: inline — status: done (emulator: panel with live Open-Meteo and 3Cat data, screensaver weather line)
- T5 [S1-S8] Emulator verification (screenshots per band, navigation and dock order unchanged), then TV when ADB is enabled — route: inline — status: done (TV 2026-10-25 19:2x, MiTV-MSSP3 / Android 11, LlarTV 1.0: day, morning and night bands all observed, dock order unchanged in all three; see L8)

## Log

- L1 (2026-10-25) User's request: "Sí, endavant" approving docs/design/day-modes.md, after choosing ideas "La 1, configurar-ho des del mòbil. La 2. La 5. La 6 i la 7", dropping 2 ("Passem de la 4"), approving the mockup ("M'agrada.") and asking for the morning band ("falta el mode de 08:00h a 11:00h amb les noticies, el temps,...").
- L2 Phone configuration page is separate later work, not in this feature.
- L3 (2026-10-25) Engram mirror pending: mem_save refused with ambiguous_project (two repos under the cwd).
- L4 T3: gradual change animates over TimeBands.TRANSITION_S when the band flips while home is showing; on resume it jumps to the right mode. nightAmount() was dropped as unused.
- L5 (2026-10-25) User: "Passe'm del calendari". Calendar card, ICal.java, ICalTest and `calendar_ical` removed. Emulator had shown that three cards do not fit above the dock anyway.
- L6 (2026-10-25) User: "La TV ja està en mode depuració, posa la nova versió". Installed feat/day-modes build on the TV (adb install -r, <tv-ip>). Screensaver switched from com.google.android.backdrop/.Backdrop to io.github.mmarkimark.llartv/.BingDream (restore: settings put secure screensaver_components com.google.android.backdrop/.Backdrop). Verified on TV: Catalan home (Bona tarda, date), dock order unchanged, BingDream with live Open-Meteo weather. BingDream title/caption not visible in the 14:04 capture (emulator showed them); check on the TV.
- L7 (2026-10-25 17:21) The TV is off the network: `adb connect <tv-ip>` times out and the host does not answer
  pings, so the morning and night bands remain unobserved on the TV. It needs the TV powered on and connected to
  the same network; the day band and the screensaver weather were already confirmed on it (L6).
- L8 (2026-10-25 19:2x) The TV is back on the network (ADB over Wi-Fi) with LlarTV 1.0 installed, and all three
  bands were observed on it, at TV local time 19:19. Day band (default settings): "Bon vespre", clock, "diumenge, 25
  d'octubre", the Entrades/Wi-Fi/Configuració bar, the dock in the configured order (Plex, YouTube, Netflix, Prime
  Video, HBO Max, Spotify) with the focused app name above it and the band tinted from the wallpaper. Morning band
  (`morning=00:00-23:59`, forced): the right-hand panel shows TEMPS (19°, "Ennuvolat", "Màx. 22° · Mín. 14°" from
  Open-Meteo) and NOTÍCIES (two 3Cat headlines with "fa 4 h" / "fa 11 h"); display only, the dock order and the
  focus are unchanged. Night band (`morning=off`, `night=00:00-23:59`, forced): the wallpaper is darkened and
  warmed, the text turns cream and the dock tiles dim, with no morning panel. No reordering of the dock in any band
  (S8). The forced band settings and the shortened display timeout are restored right after the check.
