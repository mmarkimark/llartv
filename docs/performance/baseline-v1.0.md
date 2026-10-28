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
| Cold start (ms) | 1863, 2075, 1840, 1736, (one run returned no `TotalTime` line) | 1736 | 1840 | 2075 |
| Home return (ms) | 1052, 480, 343, 313, 220 | 220 | 343 | 1052 |
| RAM, TOTAL PSS (KB) | 75227, 57268, 58671 | 57268 | 58671 | 75227 |

The first run of the cold start returned no `TotalTime` line, so the median is over the four runs that did. The
first RAM sample is taken while the app is still warming up, which is why it is the highest of the three.

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
| Cold start, median | 1840 ms | 1519 ms | −321 ms (−17.4%) | +150 ms and +15% | Inside |
| Home return, median | 343 ms | 182 ms | −161 ms (−46.9%) | +40 ms and +40% | Inside |
| RAM, TOTAL PSS, median | 58,671 KB | 56,161 KB | −2,510 KB (−4.3%) | +4,096 KB and +10% | Inside |

The TV is faster than the emulator by a wide margin and both versions are inside every budget, so the settings
menu costs nothing measurable on the device that will run it.
