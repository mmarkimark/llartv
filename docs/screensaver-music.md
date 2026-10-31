# Screensaver music

Switch on *Configuració* → *Música al protector* to play **"Dancing" by Holizna** while the screensaver is showing.
Music is **off by default**. The app downloads this CC0 track once to private app storage, outside the APK, verifies
it and plays the local file in a loop. An owner-provided HTTPS URL can override it.

## Settings

```sh
./scripts/config.sh --tv <TV-IP> music_screen=on music_url=
```

| Setting | Default | Meaning |
| --- | --- | --- |
| `music_screen` | `off` | Enables music for the next screensaver session. Off means no player or music download. |
| `music_url` | empty | Empty selects the cached Holizna track. A nonempty HTTPS audio URL overrides it. |

Settings persist in `home.conf`. Turning music off does not erase the URL or cached track. The remote toggles music
but does not provide a URL editor. An invalid custom URL stays silent rather than unexpectedly using Holizna.

## Default track and cache

- **Source:** [Holizna — Chillout Instrumentals (Collection)](https://opengameart.org/content/chillout-instrumentals-collection).
- **Track:** [Dancing, original MP3](https://opengameart.org/sites/default/files/04_holiznacc0_-_dancing.mp3).
- **Licence:** the author's collection page publishes it under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
  Credit is not required by CC0; these links preserve the source and acknowledge Holizna.
- **Download:** 5,429,965 bytes (about 5.43 MB), outside the APK. The observed MP3 lasts 135.72 seconds.
- **Integrity pin:** SHA-256 `c974c57cf242be09806002a236a4d97fad80e015c1f36535c800a782f9396228`.

The first enabled screensaver downloads and validates the track on a separate worker; the visual screensaver does
not wait for it. Subsequent sessions verify and reuse the file without contacting the source, so it also works
offline. Missing/corrupt content can be downloaded again; a changed upstream file is rejected rather than trusted
silently. Partial downloads are removed and never played. The initial download has a 120-second body budget,
8-second connection/read timeouts, a byte cap and no automatic retry loop. Until validation finishes the first
screensaver remains silent. The worker can take until its network timeout to notice cancellation, but it cannot
start audio after exit.

No music is bundled in the APK. Clearing app data or uninstalling removes the private cache; enabling music again
then needs another download. The cache is rebuildable and does not replace `home.conf`.

## Additional playlist sources prepared for v1.4

The v1.4 playlist catalogue retains Dancing's exact filename, bytes and checksum, so an existing verified
Dancing cache is reused. Two additional original MP3s were downloaded from the composer's official catalogue
and fully decoded with ffprobe/ffmpeg on 2026-10-31. Their licence is **CC BY 4.0, not CC0**:

| Track | Official recording | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Chill Wave | [Kevin MacLeod, USUAN1600048](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1600048) | 7,684,325 | `7f07fbf89860959ff527f7b74606146dd0b0de263739989a70ce712c7c361614` |
| Dream Culture | [Kevin MacLeod, USUAN1300046](https://incompetech.com/music/royalty-free/index.html?isrc=USUAN1300046) | 8,576,659 | `f12cbb52bd4b92bd6cf8a343645567c119627e9cbf98108a3eb6ac2cc275ee46` |

Official source files: [Chill Wave](https://incompetech.com/music/royalty-free/mp3-royaltyfree/Chill%20Wave.mp3)
and [Dream Culture](https://incompetech.com/music/royalty-free/mp3-royaltyfree/Dream%20Culture.mp3).
Observed durations: 240.065313 s and 214.360825 s respectively; both are MP3, 44.1 kHz, stereo.
The [official attribution instructions](https://incompetech.com/music/royalty-free/faq.html) and
[licence options](https://incompetech.com/music/royalty-free/licenses/) require discoverable credits:

> "Chill Wave" and "Dream Culture" — Kevin MacLeod (incompetech.com).
>
> Licensed under Creative Commons: By Attribution 4.0.
>
> https://creativecommons.org/licenses/by/4.0/

The original MP3s are not modified; playing an excerpt does not change the distributed file. The intended
distribution is **separate v1.4 GitHub release assets**, not app hotlinks to the composer and not APK resources:
`music-macleod-chill-wave-v1.mp3` and `music-macleod-dream-culture-v1.mp3`.
Asset publication and Android playlist acceptance are still pending at this implementation stage.

Each cache file has its own byte/hash pin and temporary-file validation. GitHub downloads may follow at most
two manually checked HTTPS redirects to the exact `release-assets.githubusercontent.com` host, without URL
credentials, fragments or nonstandard ports. Only these pinned release assets additionally allow
`application/octet-stream`; Dancing's original source still requires MP3 MIME and does not follow redirects.
A failed new-track download never replaces the already verified Dancing file. All three files total
21,690,949 bytes (about 21.69 MB) outside the APK.

## Playback and interruptions

- Exiting the screensaver releases the player and focus, including while downloading or preparing audio. Remote-key
  handling is unchanged; there is no music-specific volume-key mode. Cached playback loops until exit or interruption;
  gapless looping is not promised on every device.
- Playback starts only after preparation and a successful audio-focus request. Denial leaves the session silent.
  Temporary focus loss pauses and gain resumes; permanent loss stops the session without an automatic restart.
- Download/playback errors leave the visual screensaver running, without an error dialog or app-level retry loop.
- **Custom URLs retain their previous behavior:** play directly over HTTPS, without caching or forced looping.
  A finite custom file plays once, then releases focus. A continuous station plays until it ends, fails or the dream
  exits. Actual codec support depends on the device. Use a direct audio URL, not a web page, with terms that permit
  your use. The app keeps Android's default cleartext policy and does not accept HTTP music URLs.

App-level messages use the `LlarTV` tag and do not include custom source URLs. Cache failures show a controlled
reason (HTTP status, validation or storage category), or only the exception type for transport/system errors; raw
exception messages that might contain credentials or tokens are not logged:

```sh
adb -s <TV-IP>:5555 logcat -d -s LlarTV:I '*:S'
```

## Data use and verification

The default needs about **5.43 MB once**, plus protocol overhead; valid cached playback performs no further music
network requests, regardless of screensaver duration. This excludes unrelated app traffic. No new runtime library
is required. The file is streamed from disk by the platform player, not loaded wholesale into app memory.

Custom streams still consume data continuously. Estimates excluding overhead and reconnections:
`MB/hour = kbps × 3600 / 8 / 1000` (decimal MB): 64 kbps ≈ 28.8 MB/h; 128 ≈ 57.6 MB/h; 192 ≈ 86.4 MB/h.

**Verified on the reference TV (Xiaomi MiTV-MSSP3, Android 11, 2026-10-30); not yet released.** A clean debug
build of commit de57410 (123 JVM tests passing, APK 4,031,019 bytes, +0.43% versus v1.1) was installed over the
published v1.1. With `music_screen=on` and an empty `music_url`, the screensaver became active and the app logged
`playing (cached loop)` 5.0 s after the dream was observed, including the first one-time download; the owner
confirmed audible playback on the TV. The player reported looping and the MP3 decoder, LlarTV held audio focus at
the start and at all six 10-minute samples over 3,626 s (longer than an hour), and BACK ended the dream and the
focus was no longer held. Over that hour the app received +6,027,775 bytes and sent +117,088 in the first 10
minutes (the 5.43 MB track plus protocol overhead and startup traffic; the netstats counters lag, so the download
appears in the 10-minute sample), then 0 bytes received or sent for the remaining ~50 minutes of looping playback.
Active-dream RAM (TOTAL PSS) was 71,640 KB at start and sampled a median 113,755 KB (maximum 125,097 KB) with no
steady growth after the first 10 minutes; there is no earlier active-dream baseline, so this is the first
reference. The TV's `home.conf`, `screen_off_timeout`, `stay_on_while_plugged_in` and the screensaver component
were restored exactly and verified afterwards.

The earlier emulator evidence: default-off, unreachable-custom silence, custom Holizna HTTPS streaming and exit,
and the corrected cached playback with native MP3 looping, 140 seconds offline and focus release on BACK.
Independent broad source verification found no runtime blocker but returned partial after an accidental
live-directory Gradle invocation before its isolated checks; a later isolated, targeted timeout/diagnostic
recheck passed all 9 cache tests without rewriting that procedural partial. See
[the feature log](../odd/tasks/screensaver-music.md) for exact evidence and the separate local review units.

The APK, cold-start, Home-return and idle-RAM numbers of the same build on the same TV, against the v1.0/v1.1
references and the budgets, are recorded in [performance/baseline-v1.0.md](performance/baseline-v1.0.md).
