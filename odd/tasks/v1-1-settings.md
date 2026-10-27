Objective: deliver LlarTV v1.1 as the lightweight settings foundation: a measured baseline, a native settings menu, the clock format, the weather/news controls and the news source presets.
Branch: feat/v1-1-settings (from main). Delivery: work-unit commits on the branch; push, pull request and merge stay the owner's decision. Tests: JUnit (`./gradlew testDebugUnitTest`), shellcheck over `scripts/*.sh`, functional checks on AVD `tv11` and on the MiTV-MSSP3 when it is reachable.
Roadmap: GitHub issue #10. v1.1 = #1, #2, #3, #4, #5, #12 (the interface-language issue #11 moved to v1.2).

## Specs

- S1 "revisa els issues que hi ha pendents i com afrontar-ho tenint en compte el roadmap": review the open issues
  against the roadmap before implementing anything, and record the outcome in the roadmap issue and in the affected
  issues. The review is a read-only step; no source change comes out of it.
- S2 "Segueix la interfície (recomanat)": the news source follows the interface language, with no separate
  language selector. Català uses 3Cat, Español BBC News Mundo, English BBC News World. A custom `news_rss` value
  is always preserved and is never overwritten by a preset or by a language change.
- S3 "Català sempre (recomanat)": new and reinstalled installations default to Catalan. A menu choice overrides
  it, and existing installations keep Catalan on upgrade. Language stays independent of the clock format, of the TV
  system language, of the timezone and of the time bands.
- S4 "Mai / Només matí / Tot el dia (recomanat)": weather and news get a three-state control per surface (Home and
  screensaver). The Home default stays `Morning only`, so current installations change nothing. The screensaver
  default stays `All day`, which preserves today's behaviour of showing the cached weather whenever it is fresh.
  `Never` on a surface means no request, no weather-only or news-only refresh work, no stale cache on that surface
  and no empty placeholder.
- S5 "Mover #11 a la v1.2 (recomanat)": the interface-language issue moves to the v1.2 milestone, so v1.1 ships the
  baseline, the settings foundation, the clock format, the content controls and the news presets.
- S6 "capture the baseline first; build the native settings foundation next" (issue #10, dependency order): the
  performance baseline is recorded before any feature code, and every later milestone repeats the same comparison.
- S7 "Record a pre-change baseline using a consistent build/signing variant and named target, without private device
  identifiers" (issue #1): the baseline records the build variant, the target, the Android version, the measurement
  procedure, the repetitions and the raw results, and sets the regression budgets used as release gates.
- S8 "no new runtime dependency; loading Home does not construct a closed settings screen" (issue #1): the settings
  menu adds no runtime dependency, is not built while Home starts, and the existing defaults and ADB configuration
  keep working.

## Tasks

- T1 [S1, S6, S7] #1: measurement script plus the recorded baseline (build variant, target, Android version,
  procedure, repetitions, raw results, budgets) and the real-TV proof kept explicitly open — route: inline —
  status: done (scripts/measure.sh, docs/performance/baseline-v1.0.md; shellcheck clean; emulator `tv11` measured
  on 2026-10-27: cold start median 1384 ms, Home return median 95 ms, RAM median 38093 KB, debug APK 3999635
  bytes; the MiTV-MSSP3 baseline is open, the TV was not reachable over ADB)
- T2 [S5, S8] #2 + #3: native remote-friendly settings menu (reusing `Config`/`home.conf`, keeping the Android
  settings reachable, preserving unknown keys and untouched values) with the clock format as its first control —
  route: inline — status: done (SettingsActivity + Clock; 58 JVM tests green, shellcheck clean; emulator `tv11`:
  the menu opens with the remote, the values persist in `home.conf` (`clock=24`, `clock=12` read back through
  the settings receiver), Home shows 21:01 and 9:02 PM, the 12-hour notation fits the layout; the screensaver
  clock and the real TV are still open)
- T4 [S4] #4 + #5: three-state weather and news control per surface, with the request suppression and the unchanged
  defaults — route: inline
- T5 [S2] #12: news source presets (3Cat, BBC Mundo, BBC World) with attribution and article links, plus the
  recorded BBC reuse terms before the presets are enabled — route: inline
- T6 [S6] Final before/after comparison (APK bytes, cold start, Home return, RAM) and the release notes — route:
  inline

## Log

- L1 (2026-10-27) User's request: "revisa els issues que hi ha pendents i com afrontar-ho tenint en compte el roadmap"
- L2 (2026-10-27) Decisions taken by the owner for the open questions, quoted verbatim: "Segueix la interfície
  (recomanat)"; "Català sempre (recomanat)"; "Mai / Només matí / Tot el dia (recomanat)"; "Mover #11 a la v1.2
  (recomanat)".
- L3 (2026-10-27) Repository state when the review started: `main` clean, no open pull request, 12 open issues
  (#1-#12), milestones v1.1 with 7 issues, v1.2 with 2, v1.3 with 2, no due dates. `./gradlew testDebugUnitTest
  assembleDebug` is green (40 JVM tests). No ADB device was reachable, so the MiTV-MSSP3 proof is not available
  yet; the AVD `tv11` is.
- L4 (2026-10-27) Roadmap recorded: issue #10 body updated with the resolved decisions, the new v1.1/v1.2 split and
  the still-open questions; issue #11 moved to milestone v1.2; the decisions were also posted as comments on #4,
  #5, #11 and #12. The still-open questions are the fixed-image semantics (#6), the motion-off semantics (#7) and
  the BBC RSS reuse terms, attribution, article links and cache conditions (#12).
- L5 (2026-10-27) Sequencing bridge for S2 and S5, so that #12 in v1.1 and #11 in v1.2 fit together: the stored
  news source is `automatic` by default and resolves to the interface language when the language selector exists
  (v1.2). In v1.1, without a language selector, the settings menu offers the three sources explicitly and
  `automatic` resolves to Catalan. An explicit source choice survives a language change, and a custom `news_rss`
  value always wins over any preset.
- L6 (2026-10-27) T1 evidence. `scripts/measure.sh` measures the APK bytes, the cold start (`am force-stop` then
  `am start -W`), the Home return (another app in front, then the resume, with the HOME intent used when LlarTV is
  the default home) and the RAM (`dumpsys meminfo`, `TOTAL PSS`), with five latency runs and three RAM samples. It
  restores the default home even when it fails, and it prints whether it measured the HOME intent or an explicit
  resume. The first attempt at the Home return reported 0 ms because the app was still in front; the background app
  is now checked with the resumed activity, not with the exit status of a pipeline (`head -1` closing the pipe
  makes `pipefail` report SIGPIPE). Baseline recorded on AVD `tv11`: cold start 1384 ms median, Home return 95 ms
  median, RAM 38093 KB median, debug APK 3999635 bytes; the released `v1.0` asset is 3999631 bytes, so the debug
  build is the comparable variant. Budgets: APK +5%, cold start +150 ms and +15%, Home return +40 ms and +40%,
  RAM +4096 KB and +10%. The MiTV-MSSP3 baseline is open and must be recorded before the v1.1 release gate.
- L7 (2026-10-27) T2 and T3, the settings menu and the clock format. The menu is a new `SettingsActivity`, so
  Home never builds it and the Home path stays as it was; it writes to the same `home.conf` through the same atomic
  write, replacing only the line of the key it changes (`Config.setInText`), so unknown keys, comments and the
  untouched values survive. The D-pad model: up/down move between the settings, left/right change the value of the
  focused one, OK opens its list of values, Back closes that list and then the screen. The clock format is a new
  `clock` key (`system`, `24`, `12`) applied to the big clock, the screensaver clock and the next-match card;
  `system` keeps the exact behaviour of each surface, so an existing installation does not change, and the explicit
  12-hour format shows AM/PM. `scripts/config.sh` learned the `clock` key; its help text and its unknown-key error
  are the only outputs that changed, checked against the previous version.
- L8 (2026-10-27) T2 and T3 evidence on the AVD `tv11` (Android 11): the menu opens from the *Configuració* pill
  with the remote, the value list opens with OK and applies with OK, `home.conf` holds `clock=24` and then
  `clock=12` (read back through the settings receiver), Home shows 21:01 with the 24-hour format and 9:02 PM with
  the 12-hour one, and the AM/PM text fits the layout. The JVM tests grew from 40 to 58 (`ClockTest`, plus the
  `clock` key and `Config.setInText` cases) and shellcheck is clean. Still open: the screensaver clock, which
  cannot be captured over ADB (the dream needs the display off), and the whole thing on the real TV.
- L9 (2026-10-27) The settings screen is not exported, so the adb shell cannot start it directly; it is reached
  from Home, which is the only path it needs. The column has a fixed width (52% of the screen) so the values line
  up on the right; the first version let the column wrap to its content and glued the label to the value.
