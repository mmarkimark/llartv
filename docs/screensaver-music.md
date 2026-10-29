# Screensaver music

LlarTV can play an owner-provided HTTPS audio stream while its screensaver is showing. Music is **off by default**
and no station, music file or subscription is included. Set the URL over ADB, then use *Configuració* →
*Música al protector* to switch music on or off.

## Set up a source

Replace the example URL with an actual audio stream you are permitted to use:

```sh
./scripts/config.sh --tv <TV-IP> music_url=https://example.org/lofi.mp3 music_screen=on
```

| Setting | Default | Meaning |
| --- | --- | --- |
| `music_screen` | `off` | Enables music for the next screensaver session. |
| `music_url` | empty | HTTPS audio URL. Empty means silence; the remote does not provide a URL editor. |

Settings persist in `home.conf`; turning music off does not erase the URL. An enabled toggle with an empty or
unsupported URL remains silent. Existing installations remain silent until both settings are configured.

## Playback and interruptions

- Music belongs to the screensaver session. Exiting the screensaver releases the player and its audio focus,
  including when the stream is still preparing. Remote-key handling is unchanged; there is no music-specific
  volume-key mode.
- Playback starts only after preparation **and a successful audio-focus request**. A denied request leaves this
  session silent. Temporary focus loss pauses playback; focus gain resumes it. Permanent loss stops the session
  and does not automatically restart it.
- An unavailable stream or playback error leaves the visual screensaver running, without an error dialog or
  app-level retry loop. Android's player may make bounded internal connection attempts before reporting failure.
- A finite audio file plays once, then releases its player and focus. A continuous station plays until it ends,
  fails, loses focus permanently or the screensaver closes. There is no automatic looping or reconnect.

App-level playback/error messages use the `LlarTV` log tag and do not include the source URL:

```sh
adb -s <TV-IP>:5555 logcat -d -s LlarTV:I '*:S'
```

## Source requirements and data use

Use a direct HTTPS audio URL, not a station's HTML page. The app keeps Android's default cleartext policy and does
not accept `http://` music URLs. A private server needs a certificate the TV trusts. Actual codec and stream support
also depends on the device's platform player; HTTPS alone does not guarantee that a source is playable.

Choose a source whose terms permit your intended use and record its URL, terms and any required attribution before
enabling it. A publicly accessible stream is not itself a licence. LlarTV does not bundle or endorse a third-party
music source, and the test URLs used during development are not defaults.

Bandwidth depends on the source bitrate, not whether it is stereo. The following are **estimates**, excluding
protocol overhead, buffering and reconnections: `MB/hour = kbps × 3600 / 8 / 1000` (decimal MB).

| Audio bitrate | Estimated data per hour |
| --- | --- |
| 64 kbps | 28.8 MB |
| 128 kbps | 57.6 MB |
| 192 kbps | 86.4 MB |

A real one-hour TV measurement for the selected source is still pending. A downloaded audio file may be fetched
in a burst rather than at a steady streaming rate.

## Implementation and verification status

Playback uses the platform `MediaPlayer` and adds no runtime dependency. `MusicPlayback` tests preparation,
focus denial/loss/resumption, completion/error cleanup and callbacks arriving after exit. `DreamMusic` connects
that lifecycle to Android on the main thread; `BingDream` starts and stops each session.

The recovery candidate passed a clean debug build, all **109 JVM tests**, ShellCheck and independent lifecycle
verification. Its clean APK is **4,022,131 bytes**, versus **4,013,679 bytes** for published v1.1: **+8,452 bytes
(+0.21%)**. This is an APK-size comparison, not a memory or latency measurement.

Real-TV playback, screensaver RAM and network consumption are **not yet verified** for this candidate. The previous
RAM comparison was discarded because no dream was active. Budget compliance must not be inferred from home-screen
RAM or the presence of unit tests. See [performance/baseline-v1.0.md](performance/baseline-v1.0.md) for the measurement
procedure and released v1.1 reference.
