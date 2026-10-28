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
  bytes; the MiTV-MSSP3 baseline was recorded on 2026-10-28 with the released v1.0 asset: cold start median
  1840 ms, Home return median 343 ms, RAM median 58671 KB, APK 3999631 bytes)
- T2 [S5, S8] #2 + #3: native remote-friendly settings menu (reusing `Config`/`home.conf`, keeping the Android
  settings reachable, preserving unknown keys and untouched values) with the clock format as its first control —
  route: inline — status: done (SettingsActivity + Clock; 58 JVM tests green, shellcheck clean; emulator `tv11`:
  the menu opens with the remote, the values persist in `home.conf` (`clock=24`, `clock=12` read back through
  the settings receiver), Home shows 21:01 and 9:02 PM, the 12-hour notation fits the layout; on the MiTV-MSSP3
  on 2026-10-28 the menu opens from the *Configuració* pill with the remote, right changes the value,
  `home.conf` reads `clock=12` and the home clock shows 6:41 p.m.; the screensaver clock is still open)
- T4 [S4] #4 + #5: three-state weather and news control per surface, with the request suppression and the unchanged
  defaults — route: inline — status: done (`ContentPolicy`, `weather_home`/`weather_screen`/`news_home`; 73 JVM
  tests green, shellcheck clean; emulator `tv11`: the TEMPS card disappears with `weather_home=never` and leaves
  no empty space, and a local proxy shows no request at all when everything is off, only the weather request when
  the screensaver still wants it, and no RSS request when the news are off; on the MiTV-MSSP3 on 2026-10-28, with
  the cache files deleted first, everything off writes neither `weather.cache` nor `news.cache` and shows no
  panel, everything on writes both and shows the live TEMPS (22°, "Poc ennuvolat") and NOTÍCIES cards, and with
  the weather off on the home screen but on in the screensaver the TEMPS card disappears without leaving a space
  while the screensaver keeps the weather request; the screensaver weather line is still open)
- T5 [S2] #12: news source presets (3Cat, BBC Mundo, BBC World) with attribution and article links, plus the
  recorded BBC reuse terms before the presets are enabled — route: inline — status: done (`NewsSource`,
  `news_source` in `home.conf` and in the settings menu, the credit line under the headlines, the article link in
  the cache and the 3Cat terms in docs/news-sources.md; 87 JVM tests green, shellcheck clean; on the emulator
  `tv11` the menu offers the four sources, BBC News Mundo shows its own headlines with "BBC News" next to each of
  them and "BBC News Mundo · bbc.co.uk/news" under them, and the 3Cat headlines are not shown under it; the real
  TV is still open)
- T6 [S6] Final before/after comparison (APK bytes, cold start, Home return, RAM) and the release notes — route:
  inline — status: pending (the TV comparison of the settings slice is recorded in
  docs/performance/baseline-v1.0.md; the comparison of the final build on the TV and the release notes are still
  open)

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
- L10 (2026-10-27) Performance after the settings menu and the clock format, measured with the same script on the
  AVD `tv11`, with the released `v1.0` APK and the new build measured one after the other on the same running
  emulator: APK 3999631 -> 4105239 bytes (+2.6%), cold start 1495 -> 1572 ms (+5.1%), Home return 118 -> 125 ms
  (+5.9%), RAM 39778 -> 40061 KB (+0.7%). All four are inside the budgets of issue #1. The first comparison against
  the freshly booted baseline looked 188 ms slower on the cold start; measuring the released APK again in the same
  conditions showed the real difference is 77 ms, so every comparison is made against the previous version measured
  at the same time.
- L11 (2026-10-27) Delivery strategy for the v1.1 slices: the work is committed in reviewable units of about 400
  authored lines. The menu and the clock format are one unit and it is bigger than that (a new activity, the clock
  preference and 18 new JVM tests): it is the foundation the next units reuse, and it is split from the
  documentation commit. The weather/news controls and the news presets stay inside the budget.
- L12 (2026-10-27) The reuse terms for the BBC feeds are recorded in docs/news-sources.md, checked against the
  BBC's own pages on 2026-10-27. In short: the feeds can be added to your own app with three conditions (do not
  change the feed, credit the BBC with the text and a hyperlink near the headlines, add no BBC branding), the
  article link has to be kept because the home screen is display-only, and any business or non-personal use needs a
  licence and may cost money. LlarTV is a personal project, which is the "for people" path; a wider distribution
  has to ask the BBC first. The terms of the default 3Cat feed are still open.
- L13 (2026-10-27) T4 evidence on the AVD `tv11`: with the morning band forced, the panel shows TEMPS and NOTÍCIES
  as before; with `weather_home=never` the TEMPS card disappears and the panel closes over the news card without
  an empty space; the request suppression was measured with a local proxy on the emulator (10.0.2.2:8899): with
  everything off there is no request at all, with the weather off on the home screen but on in the screensaver the
  weather request continues (the screensaver only reads the cache) and with the news off there is no RSS request.
  The JVM tests grew from 58 to 73 (`ContentPolicyTest` plus the new `Config` cases) and shellcheck is clean. Still
  open: the screensaver weather line, which cannot be captured over ADB, and the whole thing on the real TV.
- L14 (2026-10-28) The settings foundation proved on the real TV (MiTV-MSSP3, Android 11). The TV was reachable
  over ADB at 192.168.1.52:5555 and running the LlarTV screensaver, so the session started with the released v1.0
  asset installed. The menu opens from the *Configuració* pill with the remote (up, right, right, OK), the focus
  moves between the settings, right changes the value, and the change is written to `home.conf` by the app itself
  (`clock=12` read back through the settings receiver). The home clock then shows 6:41 p.m. instead of 18:41, so
  the clock format reaches the real TV. The content controls were proved with the cache files deleted first: with
  everything off the app writes neither `weather.cache` nor `news.cache` (the fetch is what writes them) and no
  panel is shown; with everything on it writes both and shows the live weather (22°, "Poc ennuvolat") and two 3Cat
  headlines with their age; with the weather off on the home screen but on in the screensaver the TEMPS card
  disappears and the news card closes over it, without an empty space, while the screensaver keeps the weather
  request, because the screensaver only reads the cache.
- L15 (2026-10-28) The missing TV baseline is recorded and the comparison is inside every budget. The released
  v1.0 asset (3,999,631 bytes) was installed on the TV and measured with scripts/measure.sh, then the branch build
  (4,107,163 bytes) was installed and measured right after, on the same running TV: cold start 1840 -> 1519 ms,
  Home return 343 -> 182 ms, RAM 58671 -> 56161 KB. The APK is +2.7% against a +5% budget and the other three
  metrics are better than the baseline. Both numbers are in docs/performance/baseline-v1.0.md. One of the five
  cold-start runs of the baseline returned no `TotalTime` line, so that median is over four runs, and this is
  said in the document instead of being hidden.
- L16 (2026-10-28) The LlarTV screensaver CAN be captured over ADB, which the earlier notes said was impossible.
  What fails is *starting* the dream, because that needs the display off, and the MiTV drops its Wi-Fi the moment
  the screen sleeps. But when the dream is already running and the display is on, `screencap` returns the
  screensaver: the clock, the Catalan date, the wallpaper title and its credit were all captured on the TV at
  18:35. The remaining proof of the screensaver (the clock format and the weather line there) needs the TV awake,
  because sending KEYCODE_SLEEP to start the dream takes the network down with it.
- L17 (2026-10-28) T5, the news source presets. The stored choice is `news_source` (automatic, 3cat, bbc-mundo,
  bbc-world); "automatic" follows the interface language and resolves to Catalan until the language selector of
  issue #11 lands in v1.2. A custom `news_rss` always wins over the preset and is never overwritten by one, and
  the menu says "Personalitzada" and refuses to change the preset while it is in effect, instead of pretending
  the preset is in use. Every source is credited next to each headline ("BBC News", "3Cat" or the host of a custom
  feed) and in one credit line under them ("BBC News Mundo · bbc.co.uk/news"), which is what the terms of those
  feeds ask for, and the article link is kept in the cache because the home screen is display-only. The cache now
  carries the source it belongs to and the article link, so the headlines of one source are never shown under
  another one and a cache from an older version is not reused at all. Two defects were found by running it and
  fixed: the action row of the settings menu had no value list and crashed the screen, and a source switch was
  blocked for ten minutes by the attempt of the previous source, so the retry window is now kept per source. The
  JVM tests grew from 73 to 87 and shellcheck is clean. The 3Cat terms are recorded in docs/news-sources.md:
  personal and private use only, so a public distribution of the app has to ask the CCMA first, exactly like the
  BBC feeds need a licence for anything wider than personal use.
- L18 (2026-10-28) The Catalan text of the app was reviewed. "Relotge" was a Spanishism: Catalan is "Rellotge".
  "Bon tarda" does not exist in Catalan either: the greeting is "bon dia" until 14:00 and "bon vespre" after it,
  which is what the app now says. Every other string was checked and left as it is. The slice boundary of the
  branch: the first slice is the settings foundation (the six commits up to 84d4e4b) and the second slice is the
  news source presets (838b2c4); the next commit starts a third slice, and the accumulated branch is not a review
  candidate.
- L19 (2026-10-29) The native review of the news presets slice (lineage review-e98af4ac9bb40ede, tier high, four
  lenses) was run because receipt-driven development is on and the assessment found the review due. The facade
  capture could not be driven from this host (the opaque collect bindings are rejected when they are relayed
  through it), so the reviewers ran against the provider's own materialized frozen candidate: the risk and the
  resilience lenses found nothing, the readability lens found two warnings (the custom-feed rule written twice,
  and a constructor of five same-typed parameters) plus two suggestions (the id normalized in two places, and the
  menu labels kept in a parallel array), and the reliability lens found one warning (the new per-source fetch
  gate has no automated test, and that very logic had already produced the ten-minute block) plus two
  suggestions (the guid fallback for the article link is untested, and the custom-feed menu guard is only
  verified by hand). No blocker and no critical finding, so the review closed as approved. The warnings were
  worth taking, so they are fixed in the next commit: the custom-feed rule and the id normalization are one rule
  each (Config.newsRssIsCustom and NewsSource.canonicalId), the source instances are only built by the factories,
  the menu labels are pinned to the preset order by a test, the fetch gate moved to its own class
  (NewsSchedule) with tests for the three rules, the guid fallback has a fixture, the custom cache key is
  written on one line, and the document states the median convention of the TV cold start exactly.
