Objective: publish LlarTV as a clean 1.0 that a newcomer can install and configure with the repository's own scripts.
Branch: feat/llartv-1-0, then a fresh history and a recreated repository. Delivery: single-pr. Tests: JUnit
(`./gradlew testDebugUnitTest`), shellcheck, end-to-end runs on AVD `tv11` and the MiTV-MSSP3.

## Specs

- S1 "comencem de 0. Si cal repo nou. La idea és entrar i que la versió 1.0 sigui LlarTV": version 1.0
  (versionCode 1), fresh history and repository; no TvHome 1.0/2.0 releases or tags.
- S2 "Res de riscos, res de migracions": no upgrade or migration instructions; TvHome is mentioned only as the
  origin in the acknowledgements.
- S3 "els scripts han d'estar actualitzats": the repository ships its own scripts for installing, configuring and
  removing LlarTV, using the package `io.github.mmarkimark.llartv`.
- S4 "Hauria d'arribar qualsevol que no tingués ni idea i tot funcionés": the README walks a newcomer from
  enabling debugging on the TV to a working home screen and screensaver; defaults work without a `home.conf`.

## Tasks

- T1 [S3, S4] scripts/install.sh, config.sh, uninstall.sh + shared helpers; SettingsReceiver so config works where adb cannot write the app folder — route: inline — status: done (shellcheck clean; ConfigFileTest, TimeBandsTest off case)
- T2 [S1, S2, S4] Version 1.0, Bing wallpaper by default, README/CHANGELOG/CONTRIBUTING/templates/design docs — route: inline — status: done (40 JVM tests green)
- T3 [S3, S4] End-to-end: emulator (install, uninstall) and TV (install, config, screensaver) — route: inline — status: done (emulator wiped: install disabled tvlauncher, config set/show/reset/dock picker, uninstall restored tvlauncher and Backdrop; TV: install + config with the owner's dock)
- T4 [S1] Fresh history, recreated repository, release v1.0 with `LlarTV.apk` — route: inline — status: done (single commit 54da47f, tag v1.0; the repository is now public and the download path is verified, see L7)

## Log

- L1 (2026-10-25) User's request: "A veure, adapta-ho tot. I comencem de 0. Si cal repo nou. La idea és entrar i
  que la versió 1.0 sigui LlarTV. Res de riscos, res de migracions, els scripts han d'estar actualitzats. Hauria
  d'arribar qualsevol que no tingués ni idea i tot funcionés."
- L2 android-tv-debloat belongs to other authors (fork of amirulmzlan/android-tv-debloat); LlarTV becomes
  self-contained instead of editing it.
- L3 Emulator showed `adb push` into /sdcard/Android/data is denied on some Android 11 builds, so config.sh talks
  to the app through SettingsReceiver (protected by android.permission.DUMP, held by the adb shell only).
- L4 Defaults changed so a newcomer needs no settings: wallpaper=bing, wallpaper_minutes=30; `off` disables the
  wallpaper and the time bands.
- L5 TV state seeded in .state/ for uninstall.sh: previous home com.google.android.tvlauncher/.MainActivity,
  previous screensaver com.google.android.backdrop/.Backdrop.
- L6 Independent security check of SettingsReceiver: no blockers (DUMP cannot be obtained by apps without adb);
  advisories only (no size cap, a failed read returns an empty file).
- L7 (2026-10-25) The repository is now public. Verified: the release URL used by install.sh
  (`releases/latest/download/LlarTV.apk`) resolves to the published asset (sha256 ead11383…9a3), and a clean
  clone builds green with the 40 JVM tests and shellcheck. The published dex dump is identical to a fresh build
  of this source (only the dex padding differs). No local state (`.state/`, `local.properties`, `*.apk`) is tracked.
- L8 (2026-10-25) The public-review pass over this work (CI workflow plus these task logs) came back approved with
  no blockers. Its four advisories are informational: the CI actions are pinned to version tags rather than commit
  SHAs, the Android SDK step and the shellcheck glob could be tightened, and the claim wording in L7 needed to be
  exact — corrected above. They are separate later work, not a reason to redo this review.
