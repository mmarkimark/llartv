#!/bin/bash
# Install LlarTV on an Android TV and make it the home screen and the screensaver.
# Usage: ./scripts/install.sh <TV-IP> [--apk LlarTV.apk] [--no-screensaver]
# Without --apk, the latest release is downloaded from GitHub. Undo everything with ./scripts/uninstall.sh.
set -u
# shellcheck source=scripts/lib.sh
source "$(dirname "$0")/lib.sh"

IP=""; APK=""; DREAM_TOO=1
while [ $# -gt 0 ]; do case "$1" in
  --apk) APK="${2:-}"; shift 2;;
  --no-screensaver) DREAM_TOO=0; shift;;
  -h|--help) sed -n 2,4p "$0" | sed 's/^# \{0,1\}//'; exit 0;;
  -*) fail "Unknown option $1 (see --help)";;
  *) IP="$1"; shift;;
esac; done

connect "$IP"
say "Connected to $D ($(S getprop ro.product.model), Android $(S getprop ro.build.version.release))"
[ "$(S getprop ro.build.version.sdk)" -ge 28 ] 2>/dev/null || fail "LlarTV needs Android 9 or newer."

# Remember what to restore on uninstall (only the first time, so a re-install keeps the original values).
[ -f "$STATE/previous-home" ] || current_home > "$STATE/previous-home"
[ -f "$STATE/previous-screensaver" ] || S settings get secure screensaver_components > "$STATE/previous-screensaver"
touch "$STATE/disabled"

if [ -z "$APK" ]; then
  APK="$STATE/LlarTV.apk"
  say "Downloading the latest LlarTV from github.com/$REPO"
  curl -fsSL -o "$APK" "https://github.com/$REPO/releases/latest/download/LlarTV.apk" \
    || fail "Download failed. Check your internet connection, or pass a downloaded file with --apk."
fi
[ -f "$APK" ] || fail "APK not found: $APK"

say "Installing (if nothing happens for a while, look at the TV for a confirmation)"
OUT=$(A install -r "$APK" 2>&1); echo "   $(echo "$OUT" | tail -1)"
case "$OUT" in
  *VERSION_DOWNGRADE*) fail "A newer LlarTV is already installed. Run ./scripts/uninstall.sh first.";;
  *UPDATE_INCOMPATIBLE*) fail "A LlarTV signed with another key is installed. Run ./scripts/uninstall.sh first.";;
esac
installed || fail "The TV refused the app. Some TVs do not allow other home screens."
S pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null   # shows the Wi-Fi name on Android 9

# Start it once so it creates its settings folder, then make it the home screen.
S am start -W -n "$HOME_ACT" >/dev/null; sleep 2

# Stock launchers often declare a higher priority and win over the default choice: disable those.
WIFI_OWNERS=$(S dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u)
S cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME |
  awk '/priority=/{match($0,/priority=-?[0-9]+/); pr=substr($0,RSTART+9,RLENGTH-9); want=1}
       /^ *packageName=/{ if(want){ sub(/.*packageName=/,""); print pr" "$0; want=0 } }' | sort -u |
  while read -r pr p; do
    case "$p" in ""|"$PKG"|com.android.tv.settings|com.android.settings) continue;; esac
    [ "$pr" -gt 0 ] 2>/dev/null || continue
    if [ "$p" = com.google.android.tungsten.setupwraith ] && echo "$WIFI_OWNERS" | grep -q setupwraith; then
      echo "   keeping $p: it owns the saved Wi-Fi network"; continue
    fi
    echo "   disabling $p (it would take over the Home button): $(S pm disable-user --user 0 "$p")"
    echo "$p" >> "$STATE/disabled"
  done
sort -u -o "$STATE/disabled" "$STATE/disabled"

say "Setting LlarTV as the home screen: $(S cmd package set-home-activity "$HOME_ACT")"
if [ "$DREAM_TOO" = 1 ]; then
  S settings put secure screensaver_components "$DREAM"
  say "Setting LlarTV as the screensaver"
fi

S input keyevent KEYCODE_HOME; sleep 4
if [ "$(resumed)" = "$HOME_ACT" ] && [ "$(current_home)" = "$HOME_ACT" ]; then
  say "Done. Press Home on the remote: you should see LlarTV."
  echo "   Change the dock, the time bands or the weather location with ./scripts/config.sh"
else
  fail "LlarTV is installed but the TV still opens $(resumed). Run ./scripts/uninstall.sh to undo."
fi
