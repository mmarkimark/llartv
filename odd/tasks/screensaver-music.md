Objective: let the screensaver play low-fi music while it is showing, with an option to turn it on and off in
LlarTV's own settings menu, using a once-downloaded CC0 Holizna track outside the APK, with custom streams retained.
Branch: feat/v1-2-screensaver-music (from main). Delivery: local work-unit commits; publishing needs separate
owner authorization. Delivery strategy: feature-branch-chain (owner-selected, no tracker PR created yet).
Tests: JUnit (`./gradlew testDebugUnitTest`), shellcheck over `scripts/*.sh`, functional checks
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
- S4 User: "Som-hi" accepts the proposal "Holizna, amb una descàrrega única fora de l’APK i reproducció local en
  bucle." Select Holizna's CC0 "Dancing" when music is enabled without a custom URL; download it once to app-private
  storage outside the APK and loop locally. Preserve owner-provided HTTPS streams as overrides and keep their
  existing playback behavior. Record the selected track's source and licence; no other station is bundled.
- S5 No new runtime dependency: the platform `MediaPlayer` is enough, and the release keeps the size and the RAM
  budgets of issue #1.
- S6 The option is off by default, so an existing installation keeps the current silent behaviour, and the option
  persists in `home.conf` like the other settings, so it survives an upgrade and can also be set from
  `scripts/config.sh`.
- S7 User selected "Diagnosi i correcció acotades (recomanat)": authorize one bounded Android downloader diagnosis,
  correction and check of the identified problem; do not blindly repeat the timed-out whole smoke test or publish.

## Tasks

- T1 [S1, S4, S6, S7] the music setting in the menu and in `home.conf`: a `music_screen` toggle (off by default) and a
  `music_url` with the stream, the row in the settings menu and the two keys in `scripts/config.sh`, with the
  decision (play or not) in a pure class that the JVM tests can pin — DONE (local implementation/functional proof),
  route: inline; prior commits: 45b498c, a97e690; cache foundation: 353068d; integration: 5982481.
  Corrected 120-second initial-download budget
  passes deterministic slow-transfer tests; clean 123-test suite/build (job-33) and corrected Android source/cache
  playback (job-34) pass. Default-off/custom-stream behavior preserved; real-TV evidence remains T3
- T2 [S3, S5] the playback in the screensaver: the platform player, the audio focus, the start when the dream
  starts and the stop when it ends, the silence on a failing stream, and no new dependency — DONE (local
  implementation/functional proof), route: inline + independent verification; prior commits: a97e690 and 4605d7f;
  new integration: 5982481. Clean 123-test build, Android native MP3 decoder/local looping, 140 seconds offline
  and focus release on BACK pass (jobs 33/34). Broad independent source check found no runtime blocker but remains
  procedurally PARTIAL (L15); targeted timeout/diagnostic correction recheck passes (L19). TV evidence remains T3
  Risk: item 4 (asynchronous preparation, focus changes and dream termination)
- T3 [S2, S4, S5] the documentation and the measurement: the README, the CHANGELOG, the roadmap, the data use of a
  streaming hour, and the APK, RAM and latency numbers on the TV against the v1.1 baseline — DONE (real-TV evidence
  on the MiTV-MSSP3, 2026-10-30; source resolved: Holizna CC0). Route: inline. Update guide/README/CHANGELOG
  alongside behavior; prior checkpoints: 4605d7f and f7c361a; this update: docs/performance/baseline-v1.0.md
  (new v1.2 section), docs/screensaver-music.md (real-TV result), CHANGELOG.md (Unreleased note), README.md.
  Clean de57410 debug build (123 JVM tests) = 4,031,019 B, installed over v1.1. measure.sh ×5, label
  "MiTV-MSSP3 v1.2 music (de57410)": cold 1481/1275/1488/1430/1440 → median 1440 ms (+152/+11.8% vs v1.1; vs the
  v1.0 TV budget reference −411.5 ms, inside; the +150 ms absolute figure is exceeded but not +15% and the table
  does not define both-or-either — HomeActivity unchanged since v1.1 (two Config keys added), v1.1 runs 1269–1607
  ms, treated as run-to-run noise to recheck with more runs at the next milestone, not a confirmed regression);
  Home (home-intent) 785/217/251/207/216 → median 217 ms (+13/+6.4%, inside); idle PSS 59422/60613/60612 → median
  60,612 KB (+2,855/+4.9%, inside); every metric inside budget vs the v1.0 reference. Music one-hour check
  (script .state/tv-t3/music-tv.py, results in ignored .state/tv-t3/results.json): dream active, `playing (cached
  loop)` 5.0 s later including the first one-time download; owner confirmed audible playback ("Si que sona
  música"); native player reported looping and the MP3 decoder; focus held at start and at all six 10-minute
  samples over 3,626 s; BACK ended the dream and the focus was no longer held; uid netstats +6,027,775 B received
  / +117,088 sent in the first 10 minutes (5.43 MB track plus protocol overhead and startup traffic; counters
  lag, so the download appears in that sample), then 0 B both ways over the remaining ~50 minutes; active-dream
  PSS 71,640 KB at start, samples 125,097/75,973/117,205/122,041/113,648/113,755 → median 113,755, max 125,097,
  no steady growth after the first 10 minutes (fluctuates with the slides: ~40 MB native heap of decoded bitmaps
  + ~33 MB GL textures, plus the paused HomeActivity and Android 11's DreamActivity; first reference, no prior
  active-dream baseline, not judged against a budget). home.conf, screen_off_timeout, stay_on_while_plugged_in
  and the screensaver component restored exactly and verified
- T4 [S1, S2] the delivery: local work-unit commits, then review slices, release and tag only if authorized — DONE: v1.2 released 2026-10-30 (main 6761bc7, tag v1.2, LlarTV.apk 4,031,027 B sha256 7a09e1e4…) —
  publication authorized (L27); the PR chain is open: tracker #22 (draft, Closes #17) with A #23, B #24, C #25
  (incl. f7c361a) and D #26, all CI green; slice E (de57410 + T3 evidence) and merge/release/tag remain.
  Route: inline; implementation commits: 45b498c, a97e690, 4605d7f, 353068d (cache C), 5982481 (integration D);
  this evidence-only checkpoint records the outcome.
  Delivery boundaries (not published): slice A = 1793fe2..a97e690 (initial setting/playback, 225 authored lines);
  slice B = a97e690..4605d7f (one coherent lifecycle correction with tests/guide, 433 diff lines, 315 authored).
  Keep slice B separate for its concurrency risk; do not combine the whole feature into one accumulated review.
  Slice C = f7c361a..353068d (290 added lines, cache foundation + tests); slice D = 353068d..5982481
  (259 diff lines, default/local-loop integration + tests/docs). Evidence-only checkpoint follows D; T3's
  real-TV evidence is recorded (L26/L30). Native D review approved/acknowledged, C assessed under budget with
  review not due.

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
- L11 User: "No se quina font utilitzar, fes tu una recerca". Research found Holizna's CC0-labelled collection:
  https://opengameart.org/content/chillout-instrumentals-collection . Its "Dancing" MP3 URL is
  https://opengameart.org/sites/default/files/04_holiznacc0_-_dancing.mp3 . HEAD returned HTTP 200, audio/mpeg,
  5,429,965 bytes, Accept-Ranges: bytes. CC0: https://creativecommons.org/publicdomain/zero/1.0/ . This was only
  metadata/source research, not playback proof or permission to configure a device.
- L12 User: "Som-hi" after the proposal "Holizna, amb una descàrrega única fora de l’APK i reproducció local en
  bucle." This authorizes local cache/loop implementation, tests and docs, not publishing. Reopen T1/T2 for S4;
  source selection unblocks that part of T3, but the TV is still unavailable. Implementation plan: empty music_url
  selects the built-in track only when music_screen is on; nonempty custom URLs keep streaming without caching
  or forced looping. Download/hash validation runs separately from image decoding and the main thread, with a
  byte cap, timeouts, partial-file cleanup and atomic installation; retain prior audio-focus cleanup.
  Forecast: roughly 400-500 additional diff lines for the cohesive cache/loop behavior, its tests and docs.
  Keep this work separate from the previous reviewed correction. No push/PR/tag/release authority is implied.
- L13 Source bytes downloaded (job-23 exit 0) and pinned: 5,429,965 bytes, SHA-256
  c974c57cf242be09806002a236a4d97fad80e015c1f36535c800a782f9396228. ffprobe reports 135.72 seconds.
  T1 RED job-24 exited 1: enabling with no URL failed as expected against existing selection logic. Cache API
  RED job-25 exited 1 at compilation because MusicCache did not exist (not a runtime failure). GREEN job-26
  exited 0: 11 source/cache tests pass, including once-only/concurrent reuse, offline reuse, corruption, truncation,
  oversize, connection failure and cancellation. The old 109-test result is not reused as new-build proof.
  MusicCache is independent of wallpaper storage/helpers; no existing wallpaper code or parser behavior changed.
- L14 T2 API RED job-27 exited 1 because sourceReady did not exist (compile-time, not runtime failure).
  Added session-guarded sourceReady, worker-only private-cache resolution, default-only setLooping(true), custom
  setLooping(false), and cancellation on release. GREEN job-28 exited 0: focused lifecycle tests then clean full
  suite (120 tests, zero failures/errors/skips), assembleDebug, ShellCheck and diff checks passed. Clean APK:
  4,029,779 bytes (+16,100 / 0.40% versus released v1.1). Emulator job-29 is running; results not yet claimed.
  Native ASSESS with escalation item 4 returned a mandatory independent verifier; its nativeRisk is unassessable
  because the new cache/test paths require explicit untracked scope. No native review started or approved for this
  unit. Independent verification must cover S1-S6 locally, and mark TV-only/publishing evidence pending, not pass.
- L15 Independent verifier mvtzzwhp-a-pf3l returned PARTIAL: no candidate runtime blocker found, isolated copy passed
  120 tests/build/ShellCheck and parent diff check, but it mistakenly ran Gradle in the live directory first (38 tasks
  up-to-date). No source edits/cleanup, no retry or additional verifier. Advisory stale Config.java example comment
  corrected; documentation-only readback, no additional functional test result claimed.
  Emulator job-29 exited 1: default-off/no-focus, unreachable-custom silence and successful custom Holizna HTTPS
  streaming/exit passed. Default cached playback did not start within 65 seconds. Existing app logs say only
  "screensaver music: cache unavailable, staying silent"; cause remains unknown. Local-loop/offline tests were NOT
  reached and no one-hour, music-on RAM, or latency result is claimed. Its finally block then failed at svc data
  enable (exit 20, "cmd: Can't find service: phone"), before the remaining restore. TV emulator has no phone service.
  Bounded restoration (not a test retry) used conf_put, restored timeout=3000/stay-on=1, left unchanged mobile_data=1,
  and verified original home.conf, timeout, stay-on, Wi-Fi=1 and mobile_data=1. Artifacts: ignored
  .state/music-cache-smoke/ and /tmp/gpZAQGdv/gentle-jobs-CN1oej/job-29.log. No auto-retry of the timed-out probe.
  Reopen T1; T2 stays pending. One Needs your decision: authorize one bounded Android downloader diagnosis and
  correction/recheck, or pause with local changes unpublished. TV proof and publication remain separately blocked.
- L16 User selected "Diagnosi i correcció acotades (recomanat)" (diagnose_once). Authorizes one bounded Android
  downloader diagnosis, correction and check of the identified problem, without publishing or blindly repeating
  the timed-out whole smoke test. T1 resumes. Add safe controlled failure categories, with exception class only
  for transport/system errors (never arbitrary exception messages/URLs), then probe only default-source preparation
  on the emulator with backed-up/restored configuration. Do not use the unavailable phone service.
- L17 Bounded diagnosis job-31 exited 0: safe-diagnostic test/build passed; active-dream default source failed with
  "music download deadline exceeded". Config/power settings restored and checked; network untouched. Cause is
  the 30-second absolute body budget, before player preparation. One correction: raise this budget to 120 seconds
  for the 5.43 MB initial download, retaining 8-second connect/read timeouts, byte/hash cap and cancellation. Inject
  a monotonic LongSupplier clock solely to deterministically test 100/exact-120-second accepted transfers and
  >120-second cleanup/rejection without real sleeps. Then one focused corrected cache/loop probe; do not rerun
  already-passed off/error/custom cases or use the missing phone service. TV proof/native approval still pending.
- L18 RED job-32 exited 1 with 9 tests/1 failure: a valid transfer taking 100/exact-120 simulated seconds fails under
  the 30-second budget. Changed only that budget to 120 seconds; kept 8-second I/O timeouts and integrity/cancellation.
  GREEN job-33 exited 0: 9 cache tests then clean full 123 JVM tests (zero failures/errors/skips), assembly, ShellCheck
  and diff checks pass. Clean APK 4,031,027 bytes (+17,348/+0.43% versus released v1.1). One corrected focused Android
  job-34 running (no result claimed): default source preparation then cached native MP3 loop with Wi-Fi off; no
  phone-service calls and no re-run of off/error/custom cases. Any further failure exhausts S7's correction/check
  and must become one decision result, not another retry or broad verifier sweep.
- L19 One targeted independent correction recheck mvu1571p-b-jtyy passed: no concrete timeout/diagnostic blocker,
  9 cache tests, zero failures/errors/skips, isolated scratch copy with cwd guard in the same command (exit 0).
  It checked only the recorded deadline/diagnostic correction, not a fresh broad sweep; earlier broad verification
  remains procedurally PARTIAL and is not rewritten. Harmless tar socket warning and javac deprecation note were
  reported. The verifier did not touch ADB and does not assert job-34/TV/native approval.
- L20 Corrected Android job-34 exited 0 after 3m32s: active visual dream; default cache prepares/plays; BACK releases
  focus; Wi-Fi disabled with "Active default network: none"; cached restart plays; 140 seconds offline surpass the
  full 135.72-second track; native player reports looping(true), atEOS(0), OMX.google.mp3.decoder; AudioFlinger has
  one active track for the app PID. Final BACK stops the dream/releases focus. Original home.conf, timeout=3000,
  stay-on=1, Wi-Fi=1 and unchanged mobile_data=1 all restored and verified. Artifacts: ignored
  .state/music-cache-check/. Readiness 4.22s after observing the dream is not a precise cold-download duration.
  Mark T1/T2 locally complete, not the overall feature. T3 TV checks, new native review if due and publication remain.
- L21 One honest slicing pass for the oversized feature: unit C adds the verified cache component and its 9 tests
  (MusicCache.java + MusicCacheTest.java, 290 added lines); it is independently callable/tested but is not yet used
  by the app at that commit, so existing streaming behavior remains unchanged. Unit D wires default selection,
  worker/session/loop integration with its tests and user-facing docs (roughly 270 diff lines); depends on C.
  A separate evidence-only checkpoint records task commit hashes and verification, without changing behavior.
  Keep prior lifecycle correction 4605d7f independent; no code/comments/tests/docs are compressed to meet budget.
  Choose future oversized delivery shape once before the next work-unit commit; that does not authorize publishing.
- L22 User selected "Cadena sobre una branca de funcionalitat" (feature-branch-chain). Use a feature/tracker chain,
  not stacked-to-main: future draft/no-merge tracker and child reviews before final integration. This chooses shape
  only, not push/PR/release authority. Continue local units C/D/evidence checkpoint on the current feature branch.
  Validate cache-only C against the previous committed boundary in isolated state before committing; retain
  integration changes unstaged until C has its own commit. Emulator job-5 ended normally after restored job-34;
  no restart or extra device test is needed for the already-recorded proof.
- L23 Cache-only C check job-35 failed before compilation because git archive omits ignored local.properties.
  One tooling correction copied the existing SDK location config into that isolated copy, with no source change:
  job-36 exited 0, full 118-test cache-only suite/build passed (base109 + cache9, no failures/errors/skips).
  Committed C as 353068d951343366c7aed3fe24cf7ec3d1d6187d, 290 added lines, independently buildable/callable
  without default playback adoption. Slice C = f7c361a..353068d; D starts at 353068d. Native ASSESS over committed C
  reports nativeRisk medium, reviewDue false/under_budget; named concurrency escalation still requires independent
  verification (already performed at this inline feature's end). No native START/approval is invented or needed.
  User-selected feature-branch-chain is applied before the next commit; prior slices stay separate.
- L24 Integration D committed as 5982481260b771591c4bbf74e08e95f39c7f5220, 259 diff lines with tests/user docs.
  Slice D = 353068d..5982481. Native committed-slice ASSESS reports high/reviewDue true (shell process signals from
  scripts/config.sh, whose changes are comments only); named concurrency verification already ran separately.
  INSPECT then provider-mediated START/consent created review-7544514877778c61 for committed D only, four pertinent
  lenses; source cache C is its base. Review is active, not yet approved; do not reuse prior burned review authority.
  Evidence-only checkpoint remains to record actual terminal result and commit hashes. No publishing performed.
- L25 Native D review review-7544514877778c61 completed all four provider-owned lenses and returned approved on
  the last admitted event. Bound STATUS reoffered the exact acknowledgement; acknowledge-approved returned
  native-approved-acknowledgement-completed, authority burned, mutation committed. No later STATUS/replay.
  This approval covers committed slice D only and does not authorize publishing or replace device checks.
  Local C/D implementation and evidence checkpoint are complete; T3 real-TV sound/focus, active-dream RAM,
  Home latency and one-hour traffic remain unavailable. T4's future feature/tracker chain awaits those checks
  and explicit publishing authorization. No tracker, push, PR, merge, tag or release created; version remains v1.1.
- L26 2026-10-30 T3 on the MiTV-MSSP3 (192.168.1.52): clean HEAD de57410 build with ~/.local/jdk17 (system JDK has
  no javac), 123 tests, APK 4,031,019 B, installed over v1.1. measure.sh: cold 1440 ms, Home 217 ms, PSS 60,612 KB
  (v1.1: 1288/204/57,757). Music job started 14:35: dream active, `playing (cached loop)` 5 s later.
  User: "Si que sona música". One-hour hold with focus/RAM/traffic samples running.
- L27 Assistant proposed: after T3 is documented, publish v1.2 (push the branch, tracker PR and child PRs for slices
  A-D, then release and tag) and create a GitHub issue for a right-side settings panel like the system one (v1.3,
  own branch). User: "Si". Publication and the new issue are authorized.
- L28 Created issue #21 "feat: show the settings as a side panel like the system one" (enhancement, milestone v1.3), read back and confirmed. Roadmap #10 not edited.
- L29 PR chain opened: tracker #22 (draft, Closes #17), A #23, B #24, C #25 (incl. f7c361a), D #26; all CI green; slice E (de57410 + T3 evidence) pending. No merge/tag/release yet.
- L30 2026-10-30 T3 one-hour result and docs: owner confirmed audible cached playback; focus held at start and at
  all six 10-minute samples over 3,626 s; BACK ends the dream and releases focus; uid traffic +6,027,775 B
  received / +117,088 sent in the first 10 minutes (netstats lag puts the 5.43 MB download in that sample), then
  0 B both ways for the remaining ~50 minutes; active-dream PSS 71,640 KB at start, samples median 113,755 /
  max 125,097 KB, no steady growth (~40 MB native heap + ~33 MB GL textures; first active-dream reference, no prior
  baseline). home.conf, screen_off_timeout, stay_on_while_plugged_in and the screensaver component restored
  exactly and verified. Docs updated: baseline-v1.0.md gained the v1.2 TV section (every metric inside budget vs
  the v1.0 reference; the +152 ms cold delta vs v1.1 recorded honestly as run-to-run noise to recheck),
  screensaver-music.md now states the verified TV result instead of pending, CHANGELOG Unreleased notes the TV
  verification, README no longer says validation is pending. T3 marked DONE; T4 now: PR chain #22-#26 open,
  slice E/merge/release remain. No commit, tag or version bump in this step.
- L31 Delivery: slice E #27 (de57410, 0ba345b evidence, 45943c8 release notes, e19e2a1 version 1.2/3) CI green;
  native ASSESS on 5982481..e19e2a1 medium, reviewDue false (under budget). Merged #23-#27 into tracker #22 with
  merge commits (tree == e19e2a1), #22 into main as 6761bc7 (closes #17). Clean worktree build of 6761bc7 with
  ~/.local/jdk17: 123 tests, APK 4,031,027 B, versionName 1.2/versionCode 3, sha256 7a09e1e4e14dbfa6ac2a40774
  0ec6224696dfd6156109ecfb99e917b8cf8111b. Annotated tag v1.2 pushed; GitHub release "LlarTV 1.2" (Latest) with
  LlarTV.apk; downloaded asset hash matches. Published APK installed on the MiTV (1.2/3), still default home and
  screensaver, home.conf unchanged. Follow-up: settings side panel #21 (v1.3).
