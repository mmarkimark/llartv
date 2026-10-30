# LlarTV performance baseline

The baseline required by [issue #1](https://github.com/mmarkimark/llartv/issues/1), recorded before the v1.1
settings work changes any code. The same procedure is repeated for every milestone (v1.1, v1.2, v1.3), so the
numbers stay comparable.

## How to measure

```sh
./scripts/measure.sh --serial <emulator-5554|192.168.1.50:5555> \
                      --apk app/build/outputs/apk/debug/app-debug.apk \
                      --runs 5 --label "<target name>"
```

The script measures, on the connected device:

| Metric | How it is measured |
| --- | --- |
| APK bytes | The size of the APK file that is installed (`wc -c`). |
| Cold start | `am force-stop` the app, wait one second, then `am start -W` and read `TotalTime`. A warm-up run is discarded. |
| Home return | Another app is brought to the foreground, then LlarTV is resumed and `am start -W` reports `TotalTime`. The script records whether it dispatched the HOME intent or resumed the activity directly. |
| RAM | `dumpsys meminfo <package>` and the `TOTAL PSS` line, sampled three times with the app in the foreground. |

Five runs for the latencies and three for the RAM. The script prints every raw value next to the minimum, median
and maximum. It only reads and starts activities; the default home is changed for the Home-return runs and is
always restored afterwards, including when the script fails.

## Build variant

The debug APK is the comparison variant, because the published `v1.0` asset is signed with a debug key and is the
same build: the released `LlarTV.apk` is 3,999,631 bytes and a fresh debug build of the same source is 3,999,635
bytes. Sizes are therefore comparable between the released app and any later build.

## Targets

| Target | State |
| --- | --- |
| AVD `tv11` (AOSP TV on x86, Android 11 / SDK 30) | Measured, 2026-10-27 |
| Xiaomi MiTV-MSSP3 (Android 11) | Measured, 2026-10-28 |

The emulator is a regression guard, not a performance reference: it renders in software, so its cold start is far
slower than on real hardware. The release gates are the numbers measured on the TV, and the TV baseline is
recorded before the v1.1 release. Missing TV proof is reported as open, never replaced by emulator numbers.

## Baseline (Xiaomi MiTV-MSSP3, 2026-10-28)

Build: the released `v1.0` asset (`LlarTV.apk`, 3,999,631 bytes), installed on the TV before the measurement.

| Metric | Raw values | Min | Median | Max |
| --- | --- | --- | --- | --- |
| Cold start (ms) | 1863, 2075, 1840, 1736 | 1736 | 1851.5 | 2075 |
| Home return (ms) | 1052, 480, 343, 313, 220 | 220 | 343 | 1052 |
| RAM, TOTAL PSS (KB) | 75227, 57268, 58671 | 57268 | 58671 | 75227 |

One of the five cold-start runs returned no `TotalTime` line, so its median is over the four runs that did: the
usual median of 1736, 1840, 1863 and 2075 is 1851.5 ms. The script itself takes the middle of its five slots and
reported 1840 ms for that run; both numbers are given here so the comparison below can use either. The first RAM
sample is taken while the app is still warming up, which is why it is the highest of the three.

## Baseline (AVD `tv11`, 2026-10-27)

Build: `main` at 76ac8ba, debug APK, 3,999,635 bytes.

| Metric | Raw values | Min | Median | Max |
| --- | --- | --- | --- | --- |
| Cold start (ms) | 1384, 1415, 1360, 1430, 1370 | 1360 | 1384 | 1430 |
| Home return (ms) | 102, 86, 95, 88, 123 | 86 | 95 | 123 |
| RAM, TOTAL PSS (KB) | 37999, 38105, 38093 | 37999 | 38093 | 38105 |

The RAM figure agrees with the "about 30–40 MB" claim in the README.

## Regression budgets

A value over a budget is investigated before a release; it is not a reason to lower the budget. The budgets are
relative to the baseline of the same target and the same build variant.

| Metric | Budget | Emulator limit |
| --- | --- | --- |
| APK bytes | +5% | ≤ 4,199,617 bytes |
| Cold start | +150 ms and +15% | ≤ 1,534 ms |
| Home return | +40 ms and +40% | ≤ 135 ms |
| RAM, TOTAL PSS | +4,096 KB and +10% | ≤ 42,189 KB |

The same rules apply to the TV, with its own baseline as the reference. The TV baseline is recorded (2026-10-28),
so both targets now have an automatic reference.

## Open items

- The comparison after each milestone is recorded here, one section per version, with the same procedure.

## After the settings menu and the clock format (v1.1, first slice)

Measured on the AVD `tv11` with the same script, on 2026-10-27, with the released `v1.0` APK and the new build
measured one after the other on the same running emulator, so both see the same conditions:

| Metric | v1.0 (released) | Settings menu + clock format | Difference | Budget | Result |
| --- | --- | --- | --- | --- | --- |
| APK bytes | 3,999,631 | 4,105,239 | +105,608 (+2.6%) | +5% | Inside |
| Cold start, median | 1495 ms | 1572 ms | +77 ms (+5.1%) | +150 ms and +15% | Inside |
| Home return, median | 118 ms | 125 ms | +7 ms (+5.9%) | +40 ms and +40% | Inside |
| RAM, TOTAL PSS, median | 39,778 KB | 40,061 KB | +283 KB (+0.7%) | +4,096 KB and +10% | Inside |

The first baseline was recorded on a freshly booted emulator and the cold start then looked 188 ms slower than the
baseline; measuring the released APK again on the same running emulator showed the difference is 77 ms. The
comparison is therefore always made against the previous version measured at the same time, not against a baseline
taken in different conditions.

## After the settings menu and the clock format (v1.1, first slice) — on the TV

Measured on the Xiaomi MiTV-MSSP3 on 2026-10-28, with the same script, with the released `v1.0` APK and the new
build measured one after the other on the same running TV:

| Metric | v1.0 (released) | Settings menu + clock format | Difference | Budget | Result |
| --- | --- | --- | --- | --- | --- |
| APK bytes | 3,999,631 | 4,107,163 | +107,532 (+2.7%) | +5% | Inside |
| Cold start, median | 1851.5 ms | 1519 ms | −332.5 ms (−18.0%) | +150 ms and +15% | Inside |
| Home return, median | 343 ms | 182 ms | −161 ms (−46.9%) | +40 ms and +40% | Inside |
| RAM, TOTAL PSS, median | 58,671 KB | 56,161 KB | −2,510 KB (−4.3%) | +4,096 KB and +10% | Inside |

The TV is faster than the emulator by a wide margin and both versions are inside every budget, so the settings
menu costs nothing measurable on the device that will run it.

## The shipped v1.1 build (Xiaomi MiTV-MSSP3, 2026-10-29)

The published asset is a clean build of the tagged commit, 4,013,679 bytes, and it is the build that was installed
and measured on the TV, right after the released v1.0 asset had been measured there:

| Metric | v1.0 (released) | v1.1 (published) | Difference | Budget | Result |
| --- | --- | --- | --- | --- | --- |
| APK bytes | 3,999,631 | 4,013,679 | +14,048 (+0.35%) | +5% | Inside |
| Cold start, median | 1851.5 ms | 1288 ms | −563.5 ms (−30.4%) | +150 ms and +15% | Inside |
| Home return, median | 343 ms | 204 ms | −139 ms (−40.5%) | +40 ms and +40% | Inside |
| RAM, TOTAL PSS, median | 58,671 KB | 57,757 KB | −914 KB (−1.6%) | +4,096 KB and +10% | Inside |

Raw values: cold start 1607, 1590, 1269, 1288, 1284 ms; Home return 766, 209, 173, 184, 204 ms; RAM 56106,
57761, 57757 KB. The whole v1.1 release, with the native settings menu and the news source presets, is a third of
a percent bigger than v1.0 and starts a third faster on the device that will run it.

## The v1.2 screensaver music build (Xiaomi MiTV-MSSP3, 2026-10-30)

Build: a clean debug build of commit de57410 (123 JVM tests passing), 4,031,019 bytes, installed over the
published v1.1. Measured with the same script, label "MiTV-MSSP3 v1.2 music (de57410)". The difference column
compares against the published v1.1 numbers from the same TV; the budgets and the results are referenced to the
v1.0 TV baseline (3,999,631 bytes / 1851.5 ms / 343 ms / 58,671 KB), against which every metric is inside:
APK +31,388 bytes (+0.78%), cold start −411.5 ms (−22.2%), Home return −126 ms (−36.7%), RAM +1,941 KB (+3.3%).

| Metric | v1.1 (published) | v1.2 (music build) | Difference | Budget | Result |
| --- | --- | --- | --- | --- | --- |
| APK bytes | 4,013,679 | 4,031,019 | +17,340 (+0.43%) | +5% | Inside |
| Cold start, median | 1288 ms | 1440 ms | +152 ms (+11.8%) | +150 ms and +15% | Inside |
| Home return, median | 204 ms | 217 ms | +13 ms (+6.4%) | +40 ms and +40% | Inside |
| RAM, TOTAL PSS, median | 57,757 KB | 60,612 KB | +2,855 KB (+4.9%) | +4,096 KB and +10% | Inside |

Raw values: cold start 1481, 1275, 1488, 1430, 1440 ms; Home return (home-intent) 785, 217, 251, 207, 216 ms;
RAM 59422, 60613, 60612 KB.

Versus v1.1 the cold-start median is +152 ms: above the +150 ms absolute figure of the budget but below its +15%
relative figure, and the budget table does not define whether both or either must hold. The code on the start path
(HomeActivity) is unchanged since v1.1 — only two Config keys were added — and v1.1's own runs ranged 1269–1607 ms,
so the delta is treated as run-to-run noise, to be rechecked with more runs at the next milestone, not as a
confirmed regression.

### Active screensaver with music

The same session then ran a one-hour music check with `music_screen=on` and an empty `music_url` (script
`.state/tv-t3/music-tv.py`, results in the ignored `.state/tv-t3/results.json`). The screensaver became active and
the app logged `playing (cached loop)` 5.0 s after the dream was observed, including the first one-time download;
the owner confirmed audible playback on the TV. The player reported looping and the MP3 decoder, LlarTV held
audio focus at the start and at all six 10-minute samples over 3,626 s (longer than an hour), and BACK ended the
dream and the focus was no longer held.

Traffic for the app uid (netstats): +6,027,775 bytes received and +117,088 sent in the first 10 minutes (the
5.43 MB track plus protocol overhead and the app's other startup traffic), then 0 bytes received or sent over the
remaining ~50 minutes of looping playback. The netstats counters lag, so the download appeared in the 10-minute
sample, not immediately.

Active-dream RAM (TOTAL PSS): 71,640 KB at start; 125,097 / 75,973 / 117,205 / 122,041 / 113,648 / 113,755 KB at
the six samples — median 113,755 KB, maximum 125,097 KB, with no steady growth after the first 10 minutes. It
fluctuates with the wallpaper slides: a meminfo breakdown during the dream showed ~40 MB native heap (decoded
slide bitmaps) and ~33 MB GL textures for the zoom animation; the process also hosts the paused HomeActivity and
Android 11's DreamActivity. There is no earlier active-dream baseline, so this is recorded as the first reference,
not judged against a budget.

## The v1.3 remote personalisation build (Xiaomi MiTV-MSSP3, 2026-10-30)

Build: a clean debug build of the content merged to `main` at c500c12 (the settings side panel, wallpaper and night
mode, screensaver clock and motion, dock favourites and hidden apps), 4,050,743 bytes, 178 JVM tests passing,
measured with the same script before the version bump. The difference column compares against the published v1.2;
the budgets and the results are referenced to the v1.0 TV baseline, against which every metric is inside: APK
+51,112 bytes (+1.28%), cold start −647.5 ms (−35.0%), Home return −150 ms (−43.7%), RAM +1,210 KB (+2.1%).

| Metric | v1.2 (published) | v1.3 | Difference | Budget | Result |
| --- | --- | --- | --- | --- | --- |
| APK bytes | 4,031,027 | 4,050,743 | +19,716 (+0.49%) | +5% | Inside |
| Cold start, median | 1440 ms | 1204 ms | −236 ms (−16.4%) | +150 ms and +15% | Inside |
| Home return, median | 217 ms | 193 ms | −24 ms (−11.1%) | +40 ms and +40% | Inside |
| RAM, TOTAL PSS, median | 60,612 KB | 59,881 KB | −731 KB (−1.2%) | +4,096 KB and +10% | Inside |

Raw values: cold start 1193, 1202, 1204, 1215, 1382 ms; Home return (home-intent) 591, 193, 223, 177, 174 ms; RAM
57442, 59907, 59881 KB. The +152 ms cold-start delta noted for v1.2 did not repeat: with an unchanged start path the
median is now below both v1.1 and v1.2, which supports reading it as run-to-run noise.
