Objective: let the screensaver play low-fi music while it is showing, with an option to turn it on and off in
LlarTV's own settings menu, without adding a runtime dependency and without shipping anyone else's stream.
Branch: feat/v1-2-screensaver-music (from main). Delivery: local work-unit commits; publishing needs separate
owner authorization. Tests: JUnit (`./gradlew testDebugUnitTest`), shellcheck over `scripts/*.sh`, functional checks
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
  decision (play or not) in a pure class that the JVM tests can pin — DONE, route: inline, commits: 45b498c,
  a97e690 (HTTPS restriction); recovery clean build passes all 109 JVM tests
- T2 [S3, S5] the playback in the screensaver: the platform player, the audio focus, the start when the dream
  starts and the stop when it ends, the silence on a failing stream, and no new dependency — DONE (local
  implementation), route: inline + independent verification; commits: a97e690 and corrective 4605d7f.
  JVM, build, independent verification, emulator silence/error/exit smoke and native review passed; actual
  source/device playback and performance evidence belong to T3 and remain open.
  Risk: item 4 (asynchronous preparation, focus changes and dream termination)
- T3 [S2, S4, S5] the documentation and the measurement: the README, the CHANGELOG, the roadmap, the data use of a
  streaming hour, and the APK, RAM and latency numbers on the TV against the v1.1 baseline — BLOCKED on an
  accessible TV and a permitted owner-selected HTTPS source. Route: inline. Guide committed in 4605d7f;
  README/CHANGELOG and evidence checkpoint are ready; APK measured, TV audio/RAM/data/latency are unverified
- T4 [S1, S2] the delivery: local work-unit commits, then review slices, release and tag only if authorized —
  BLOCKED on publication authorization; route: inline; local implementation commits: 45b498c, a97e690, 4605d7f.
  Delivery boundaries (not published): slice A = 1793fe2..a97e690 (initial setting/playback, 225 authored lines);
  slice B = a97e690..4605d7f (one coherent lifecycle correction with tests/guide, 433 diff lines, 315 authored).
  Keep slice B separate for its concurrency risk; do not combine the whole feature into one accumulated review.
  A subsequent documentation-only checkpoint records these boundaries and the remaining TV/source blockers

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
  data use are measured before the release (T3). These were agent proposals, not the user's original wording.
- L5 (2026-10-29) User: "Revisa on s'ha parat l'agent per quota i revisa com continuar." Read-only reconciliation
  found two local implementation commits and drafted docs. Last dream RAM comparison is invalid: both runs had
  mCurrentDreamName=null. The previous emulator session demonstrated playback, failed-stream silence and cleanup
  on BACK, but not focus denial or permanent loss. requestAudioFocus's result is ignored and the numeric comparison
  excludes permanent LOSS. No new tests were run during the investigation.
- L6 (2026-10-29) User: "Continua" after the recovery proposal. Resume local corrections and documentation using
  an owner-provided HTTPS stream, no bundled source, and unchanged remote-key behavior. No publishing is implied.
  T2 will serialize preparation/focus/stop on the main thread, refuse playback without focus, release on completion
  or failure, ignore late callbacks, and avoid logging stream URLs. Add deterministic lifecycle regression tests.
  T3 must separate measured data from estimates; real-TV audio, dream RAM and bandwidth remain required evidence.
- L7 Recovery evidence: RED job-17 exited 1 (7/7 lifecycle regressions failed against extracted old behavior).
  GREEN job-18 exited 0; an independent verifier (mvtxw4pm-9-1863) found no T2 blockers and passed 109 JVM tests,
  debug assembly and ShellCheck in an isolated copy. Parent clean-build spot check job-19 exited 0 with 109 tests,
  no failures/errors, clean APK 4,022,131 bytes (+8,452 / +0.21% versus v1.1).
  Native assessment of the then-untracked candidate was unassessable, but explicit concurrency escalation required
  the independent verifier. No native review approval is claimed.
- L8 Emulator smoke job-20 exited 1 before any install/config mutation: direct adb pull of home.conf was denied
  by Android scoped storage. One tooling correction uses the repository's existing conf_get/conf_put receiver API
  instead of direct file access; no permission changes or security bypass. Corrected smoke job-21 exited 0:
  music off and unreachable HTTPS source both leave a live visual dream silent without held focus or crash;
  BACK exits both dreams. Original home.conf and screen timeout restored, plus pre-feature-test stay-on=1.
  Source-terms lookup for the previously used SoundHelix test URL returned HTTP 500; no fresh third-party source
  was selected or enabled. Fresh successful audio playback and TV/bandwidth proof remain open.
- L9 Corrective commit: 4605d7ff906c46b2bed1f06eeede771edf2e7986. Native review of this committed unit only
  (base a97e6907094a1262adcf6927e5be490e64de08a5) is approved: review-495c6b76ca1f4cf0. Four provider-owned
  reviewers were admitted; exact acknowledgement returned native-approved-acknowledgement-completed with
  authority=burned. This is not TV proof and grants no publishing permission. T2's local correction is complete;
  T3 remains blocked, not done. No push, PR, tag, release or roadmap completion update has been performed.
- L10 Recovery checkpoint: README and CHANGELOG now describe the optional stream, with the complete guide and
  honest measured-versus-pending evidence in docs/screensaver-music.md. Roadmap issue #17 remains open in v1.2;
  no completion state is published. T3 can resume when the TV is reachable and an owner-selected source's reuse
  terms are known. Check actual sound/focus, require an active dream before RAM samples, compare home latency to
  released v1.1, and measure source traffic over an hour. Restore test settings afterward. Build metadata stays
  at v1.1 until a future release is explicitly authorized.
