#!/usr/bin/env bash
# shellcheck shell=bash
# Measures LlarTV's size, cold start, Home return and RAM on a connected TV or emulator.
# The same procedure is repeated for every roadmap milestone, so the numbers stay comparable.
#
# Usage: scripts/measure.sh [--serial <serial>] [--apk <file>] [--runs <n>] [--label <name>] [--out <file>]
set -euo pipefail

# shellcheck source=scripts/lib.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

SERIAL="" APK="" RUNS=5 LABEL="" OUT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2;;
    --apk)    APK="$2"; shift 2;;
    --runs)   RUNS="$2"; shift 2;;
    --label)  LABEL="$2"; shift 2;;
    --out)    OUT="$2"; shift 2;;
    -h|--help) sed -n '2,9p' "${BASH_SOURCE[0]}"; exit 0;;
    *) echo "!! unknown option: $1" >&2; exit 1;;
  esac
done

find_adb
if [ -z "$SERIAL" ]; then
  DEVICES=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
  COUNT=$(printf '%s\n' "$DEVICES" | grep -c . || true)
  if [ "$COUNT" -eq 1 ]; then SERIAL="$DEVICES"; else fail "Several devices are connected; pass --serial. Connected: $(echo "$DEVICES" | tr '\n' ' ')"; fi
fi
D="$SERIAL"
[ "$("$ADB" -s "$D" get-state 2>/dev/null)" = device ] || fail "$D is not reachable over ADB."
[ "$("$ADB" -s "$D" shell pm path "$PKG" 2>/dev/null | grep -c '^package:')" -ge 1 ] || fail "LlarTV is not installed on $D."

MODEL=$(A shell getprop ro.product.model | tr -d '\r')
RELEASE=$(A shell getprop ro.build.version.release | tr -d '\r')
SDK=$(A shell getprop ro.build.version.sdk | tr -d '\r')
ABI=$(A shell getprop ro.product.cpu.abi | tr -d '\r')
[ -n "$LABEL" ] || LABEL="$MODEL"

stats() {  # stats <name> <values...>
  local name="$1"; shift
  printf '%s\n' "$@" | sort -n | awk -v n="$name" -v count="$#" '
    {v[NR]=$1}
    END {if (count%2) med=v[(count+1)/2]; else med=(v[count/2]+v[count/2+1])/2;
         printf "  %s: min %s  median %.0f  max %s  (%d runs)\n", n, v[1], med, v[count], count}'
}

COLD=() HOMES=() RAMS=()
RESTORED=""

# The default home is changed for the Home-return runs, so it is always restored, even on an error.
restore_home() {
  if [ -n "${RESTORED:-}" ]; then
    A shell cmd package set-home-activity "$RESTORED" >/dev/null 2>&1 || true
  fi
}
trap restore_home EXIT

# A cold start is a start after a force-stop, so nothing of the previous run is still warm.
COLD_ONE() {
  A shell am force-stop "$PKG" >/dev/null
  sleep 1
  A shell am start -W -n "$HOME_ACT" | tr -d '\r' | sed -n 's/^TotalTime: //p'
}

# The Home return needs another app in the foreground, so LlarTV is really resumed instead of found on top.
BG=""
pick_background_app() {
  local c
  for c in com.android.settings/.Settings \
           com.android.tv.settings/.settings.MainSettings \
           com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity \
           com.android.vending/com.google.android.finsky.tvmainactivity.TvMainActivity; do
    A shell am start -n "$c" >/dev/null 2>&1 || continue
    sleep 1
    case "$(resumed || true)" in
      *"$PKG"*) ;;                       # LlarTV is still in front: this app did not come up
      *) BG="$c"; return 0;;
    esac
  done
  return 1
}

HOME_METHOD=""
HOME_ONE() {
  A shell am start -n "$BG" >/dev/null 2>&1
  sleep 1
  if [ "$HOME_METHOD" = "home-intent" ]; then
    A shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | sed -n 's/^TotalTime: //p'
  else
    A shell am start -W -n "$HOME_ACT" | tr -d '\r' | sed -n 's/^TotalTime: //p'
  fi
}

RAM_ONE() {
  A shell dumpsys meminfo "$PKG" | tr -d '\r' | sed -n 's/.*TOTAL PSS: *\([0-9]*\).*/\1/p'
}

say "Warming up on $LABEL..."
COLD_ONE >/dev/null || true

say "Cold start, $RUNS runs..."
for _ in $(seq 1 "$RUNS"); do COLD+=("$(COLD_ONE)"); done

# The Home key only returns to LlarTV when LlarTV is the default home, like install.sh sets it on the TV.
PREV_HOME=$(current_home)
if [ "$PREV_HOME" != "$HOME_ACT" ]; then
  A shell cmd package set-home-activity "$HOME_ACT" >/dev/null 2>&1 || true
  RESTORED="$PREV_HOME"
fi
if [ "$(current_home)" = "$HOME_ACT" ]; then HOME_METHOD="home-intent"; else HOME_METHOD="explicit-resume"; fi

if pick_background_app; then
  say "Home return ($HOME_METHOD), $RUNS runs..."
  for _ in $(seq 1 "$RUNS"); do HOMES+=("$(HOME_ONE)"); done
else
  say "Home return: no other app could be brought to the foreground; the metric is not measured."
fi
if [ -n "$RESTORED" ]; then
  A shell cmd package set-home-activity "$RESTORED" >/dev/null 2>&1 || true
fi

say "RAM, 3 samples..."
for _ in 1 2 3; do RAMS+=("$(RAM_ONE)"); sleep 1; done

REPORT=$(cat <<EOF
LlarTV performance measurement
label:      $LABEL
model:      $MODEL
android:    $RELEASE (SDK $SDK)
abi:        $ABI
runs:       $RUNS
EOF
)
if [ -n "$APK" ] && [ -f "$APK" ]; then
  REPORT="$REPORT
apk:        $APK ($(wc -c < "$APK" | tr -d ' ') bytes)"
fi
REPORT="$REPORT

cold start (force-stop, then am start -W): ${COLD[*]}
$(stats cold-start "${COLD[@]}")"
if [ ${#HOMES[@]} -gt 0 ]; then
  REPORT="$REPORT
home return ($HOME_METHOD, another app in front): ${HOMES[*]}
$(stats home-return "${HOMES[@]}")"
else
  REPORT="$REPORT
home return: not measured (no other app could be brought to the foreground)"
fi
REPORT="$REPORT
ram (TOTAL PSS, KB): ${RAMS[*]}
$(stats ram "${RAMS[@]}")"
if [ -n "$RESTORED" ]; then
  REPORT="$REPORT
note:       the default home was $RESTORED during the runs and was restored afterwards.
"
else
  REPORT="$REPORT
note:       LlarTV was already the default home on this device.
"
fi
printf '%s\n' "$REPORT"
if [ -n "$OUT" ]; then printf '%s\n' "$REPORT" > "$OUT"; say "Written to $OUT"; fi
