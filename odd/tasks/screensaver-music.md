Objective: let the screensaver play low-fi music while it is showing, with an option to turn it on and off in
LlarTV's own settings menu, without adding a runtime dependency and without shipping anyone else's stream.
Branch: feat/v1-2-screensaver-music (from main). Delivery: work-unit commits, then a chain of review slices and a
release, as in v1.1. Tests: JUnit (`./gradlew testDebugUnitTest`), shellcheck over `scripts/*.sh`, functional checks
on the AVD `tv11` and on the MiTV-MSSP3.
Roadmap: GitHub issue #17 (v1.2 milestone), linked from issue #10.

## Specs

- S1 "M'agradaria que posessis com a issue l'opció de posar música Lowfi al salvapantalles. HA de ser una opció
  activable o no al menú dòpcions": the screensaver can play low-fi music, and it is an option that can be turned
  on and off in the options menu.
- S2 "Afegeix-lo al roadmap": the feature belongs to the roadmap, in v1.2 next to the other screensaver options.
- S3 The music plays only while the screensaver is showing and stops with it: no sound is left in the background
  and the audio focus is released. A stream that fails, is unreachable or has no connection leaves the screensaver
  silent and quiet about it: no error dialog, no crash and no retry storm.
- S4 The source of the music is the owner's: the app ships no stream of its own and takes the URL from the
  configuration, so no reuse terms are baked into the release. The reuse terms of whatever stream is used are
  recorded before it is enabled.
- S5 No new runtime dependency: the platform `MediaPlayer` is enough, and the release keeps the size and the RAM
  budgets of issue #1.
- S6 The option is off by default, so an existing installation keeps the current silent behaviour, and the option
  persists in `home.conf` like the other settings, so it survives an upgrade and can also be set from
  `scripts/config.sh`.

## Tasks

- T1 [S1, S4, S6] the music setting in the menu and in `home.conf`: a `music_screen` toggle (off by default) and a
  `music_url` with the stream, the row in the settings menu and the two keys in `scripts/config.sh`, with the
  decision (play or not) in a pure class that the JVM tests can pin — route: inline
- T2 [S3, S5] the playback in the screensaver: the platform player, the audio focus, the start when the dream
  starts and the stop when it ends, the silence on a failing stream, and no new dependency — route: inline
- T3 [S2, S4, S5] the documentation and the measurement: the README, the CHANGELOG, the roadmap, the data use of a
  streaming hour, and the APK, RAM and latency numbers on the TV against the v1.1 baseline — route: inline
- T4 [S1, S2] the delivery: the work-unit commits, the chain of review slices, the release and the tag — route:
  inline

## Log

- L1 (2026-10-28) User's request: "M'agradaria que posessis com a issue l'opció de posar música Lowfi al
  salvapantalles. HA de ser una opció activable o no al menú dòpcions"
- L2 (2026-10-28) The user asked to add it to the roadmap: "Afegeix-lo al roadmap". The issue is #17 and it
  sits in the v1.2 milestone, next to the other screensaver options (#7).
- L3 (2026-10-29) The user chose this feature as the next work unit, over the interface languages (#11) and the
  appearance and screensaver options (#6 and #7).
- L4 (2026-10-29) The open decisions of the issue and how they are settled here: the source of the music is the
  owner's and the app ships none, so the URL comes from the configuration (S4); the volume keys are not a special
  case, because any key already closes the screensaver and the music stops with it (S3); the size, the RAM and the
  data use are measured before the release (T3).
