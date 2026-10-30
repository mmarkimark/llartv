Objective: issue #6 (v1.3) — configure the wallpaper and night mode from LlarTV's settings side panel.
Branch: feat/v1-3-wallpaper-night (from main 9943d1d). Delivery: local work-unit commits; push/PR need owner approval.
Tests: JUnit (`./gradlew testDebugUnitTest`, JAVA_HOME=~/.local/jdk17), checks on the MiTV-MSSP3 and AVD `tv11`.

## Specs

- S1 Issue #6: "Expose the existing plain background, newest/fixed-image behavior and wallpaper rotation interval,
  plus night mode on/off and time range, through the native menu. Reuse current wallpaper and TimeBands behavior
  instead of adding new providers or a theme engine." and "A fixed-photo mode must not silently imply zero network
  access."
- S2 Owner decision (2026-10-30) on "Fixed": "La més nova, sense rotar (recomanat)" — show the newest Bing photo
  without rotating (`wallpaper_minutes=0`); it changes once a day and keeps downloading. No new key.
- S3 Issue #6: "Plain, non-rotating and rotating choices persist and produce their documented behavior without
  needless transitions." README documents `wallpaper=off` as "a plain background". Today `off` only stops the
  downloads: Home (HomeActivity.loadWallpaper) and the screensaver (BingDream) still show cached photos.
- S4 Issue #6: "Night mode can be disabled or scheduled, including a range crossing midnight; preserve current
  defaults and time-band precedence." and "Apply existing night behavior consistently to Home and screensaver
  without changing TV system settings."
- S5 Issue #6: "Document the exact fixed-image semantics before implementation and preserve existing wallpaper
  URL/script configurations." A custom image URL or custom values set by scripts must survive the menu.
- S6 Issue #6: "Verify remote navigation, default behavior and offline cached backgrounds; repeat the shared
  performance checks and update docs."

## Tasks

- T1 [S3] fix: `wallpaper=off` shows a plain background on Home and on the screensaver (a pushed wallpaper.jpg/png
  still wins, as documented), with JVM tests — DONE, route: inline; commit: d6d9fcc
- T2 [S1, S2, S4, S5] feat: menu rows "Fons de pantalla" (Bing / llis / custom URL shown and protected),
  "Canvi de foto" (15 min, 30 min, 1 h, 3 h, fixa = newest; custom minutes kept) and "Mode nit" (off and preset
  ranges incl. crossing midnight; custom range kept), with JVM tests and docs — DONE, route: inline; commit: 2f50ccf
- T3 [S6] verify on the TV and the emulator (navigation, defaults, offline cached background, night on Home and
  screensaver), measure.sh, restore home.conf — DONE (L6), route: inline + native review; commit: n/a
- T4 [S6] the delivery: the v1.3 release — DONE: merged in #29 and released as v1.3 (tag v1.3, main 1906033); the
  release notes and the TV measurements are in docs/performance/baseline-v1.0.md

## Log

- L1 User: "Continuem" after the v1.3 order #21 → #6 → #7 → #8 was fixed in roadmap #10.
- L2 Asked what "Fixa" means. User selected "La més nova, sense rotar (recomanat)" (value: newest).
- L3 Found while exploring: HomeActivity.java:1005 and BingDream.java:153 ignore `wallpaper=off` and keep showing
  cached photos; the TV was offline (`adb: device offline`), so it is not yet reproduced on a device.
- L4 T1 committed d6d9fcc (DailyWallpaper.shown/slides(source, dir); 4 RED-then-GREEN tests). Emulator tv11: with
  wallpaper=off chosen in the panel, Home shows the plain dark background and the screensaver only the clock.
- L5 T2: Appearance (presets, custom values kept and selected, night parsed like TimeBands; 7 tests written first and
  observed failing), three rows after Rellotge, per-row custom label/tip (news and image URL). 139 tests pass.
  Emulator: rows, plain choice persisted as wallpaper=off, Mode nit list with 7 presets. A custom night range adds an
  8th choice that overflowed the panel, so the value list now scrolls to the focused choice (SidePanel.scrollFor);
  emulator check with night=22:30-07:30 shows it selected and visible. Emulator home.conf restored exactly.
  T3 blocked: the TV does not answer (adb connect hangs).
- L6 Native review review-8223bb335a03eb53 (readability, reliability) on 9943d1d..2f50ccf: approved and
  acknowledged; 7 non-blocking notes (night presets comment, inline "Cada N" labels, empty-means-off rule in two
  places, no wiring test for the rows, minutes label for negatives). User: "tv encesa". T3 on the MiTV-MSSP3 with a
  clean build (139 tests, APK 4,037,947 B): panel rows; Fons llis written as wallpaper=off and Home plain; Bing back
  as wallpaper=bing and the cached photo shown at once; Mode nit list. measure.sh: cold 1328 ms, Home 176 ms,
  PSS 61,985 KB (settings panel build: 1539/177/62,137). home.conf restored exactly. Offline cached background not
  re-checked with Wi-Fi off; the Bing path to the cached file is unchanged and covered by the JVM tests.

- L7 Delivery: PR #29 (Closes #6) with d6d9fcc, 2f50ccf and f6b8cac, CI green, merged into main as 21f4a27; native
  review review-8223bb335a03eb53 approved and acknowledged. The v1.3 release (tag v1.3, main 1906033, LlarTV.apk
  4,050,747 B, sha256 72c77bcd...) carries this feature; the published APK is installed on the MiTV as 1.3/4.
