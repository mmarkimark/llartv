Objective: issue #7 (v1.3) — choose the screensaver clock and motion from LlarTV's settings side panel.
Branch: feat/v1-3-screensaver-options (from main 21f4a27). Delivery: local work-unit commits; push/PR need owner approval.
Tests: JUnit (`./gradlew testDebugUnitTest`, JAVA_HOME=~/.local/jdk17), checks on AVD `tv11` and the MiTV-MSSP3.

## Specs

- S1 Issue #7: "Add persistent screensaver clock visibility, weather visibility and motion on/off controls." The
  screensaver weather already has its own row since v1.1 (*Temps al protector*, `weather_screen`), so this adds the
  clock and the motion.
- S2 Owner decision (2026-10-30) on motion off: "Sense zoom; les fotos canvien amb fos (recomanat)" — the photos
  stay still (no zoom or drift) but still change every 25 s with the usual cross-fade.
- S3 Issue #7: "Record motion-off semantics before implementation and verify the disabled animations actually stop
  rather than running invisibly." With motion off no drift animator is started at all.
- S4 Issue #7: "Clock formatting uses the shared LlarTV preference; hiding the clock does not silently hide
  unrelated date/credit content." Hiding the clock hides the time only; the date, the weather line and the photo
  title/credit stay.
- S5 Issue #7: "Preferences persist and defaults stay unchanged; preserve image titles/credits and remote
  dismissal." and "Screensaver remains network-free". New keys `clock_screen` and `motion_screen`, both on by
  default, also settable from `scripts/config.sh`.
- S6 Issue #7: "Verify on emulator and real TV where available; explicitly retain any unavailable real-TV cosmetic
  proof as open, and repeat the performance checks."

## Tasks

- T1 [S1-S5] feat: `clock_screen` and `motion_screen` keys, the two rows (*Rellotge al protector*, *Moviment al
  protector*), BingDream honouring them, config.sh keys and docs, with JVM tests — DONE, route: inline; commit: this feat commit
- T2 [S3, S6] verify on the emulator and the TV (clock hidden with date kept, motion off with no running drift
  animator and fades still cycling), measure.sh, restore home.conf — DONE (L4), route: inline; commit: n/a

## Log

- L1 User: "fes, seguim" after PR #29 (#6) was ready; the v1.3 order continues with #7.
- L2 Asked what "Moviment: No" does. User selected "Sense zoom; les fotos canvien amb fos (recomanat)" (value: no-zoom).
- L3 T1: Config.onOff + clock_screen/motion_screen (on by default), Drift.of (null when motion is off, so no
  animator starts), BingDream hides only the time, two rows before the music row, config.sh keys, README and
  CHANGELOG. ConfigTest (1) and DriftTest (2) written first and observed failing; 142 tests pass, shellcheck clean.
- L4 T2 with .state/screensaver-options/check.py on the MiTV-MSSP3 and AVD tv11 (clean build, APK 4,039,227 B):
  motion off -> two frames of the same slide 4 s apart are byte-identical; motion on -> they differ; clock off ->
  the time is gone and the date, weather and photo credits stay. home.conf and power settings restored on both.
  measure.sh on the TV: cold 1242 ms, Home 191 ms, PSS 60,380 KB, inside the budgets.
