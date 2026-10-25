# shellcheck shell=bash disable=SC2034
# Shared helpers for the LlarTV scripts. Sourced, not run.
# Works on Linux, macOS and Windows (Git Bash).

PKG=io.github.mmarkimark.llartv
HOME_ACT=$PKG/.HomeActivity
DREAM=$PKG/.BingDream
REPO=mmarkimark/llartv
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_ROOT="$ROOT/.state"

say()  { echo "== $*"; }
fail() { echo "!! $*" >&2; exit 1; }

find_adb() {  # sets ADB, or explains how to install it
  ADB=$(command -v adb 2>/dev/null || command -v adb.exe 2>/dev/null || true)
  for p in "$HOME/Library/Android/sdk/platform-tools/adb" "$HOME/Android/Sdk/platform-tools/adb" \
           /opt/homebrew/bin/adb /usr/local/bin/adb \
           "${LOCALAPPDATA:-/nonexistent}/Android/Sdk/platform-tools/adb.exe" /c/platform-tools/adb.exe; do
    [ -z "$ADB" ] && [ -x "$p" ] && ADB="$p"
  done
  [ -n "$ADB" ] && return 0
  echo "!! adb was not found. Install it, open a new terminal and run this script again:"
  echo "   Linux:   sudo apt install adb   (or your distribution's android-tools package)"
  echo "   macOS:   brew install --cask android-platform-tools"
  echo "   Windows: winget install Google.PlatformTools"
  exit 1
}

debug_help() {
  cat <<'EOF'
   On the TV:
   1. Settings > Device Preferences > About: select "Build" 7 times until it says you are a developer.
   2. Settings > Device Preferences > Developer options: turn on "USB debugging"
      (and "Network debugging" or "ADB debugging" if your TV lists it).
   3. Settings > Network & Internet > your network: note the TV's IP address, for example 192.168.1.50.
EOF
}

# connect [ip]: uses the given IP, or the last one used, and waits for the TV to authorise this computer.
# Sets D (adb serial) and STATE (per-TV state folder).
connect() {
  find_adb
  local ip="${1:-$(cat "$STATE_ROOT/last-tv" 2>/dev/null)}"
  if [ -z "$ip" ]; then
    echo "!! Tell the script your TV's IP address, for example:  $0 192.168.1.50"
    debug_help; exit 1
  fi
  case "$ip" in *:*) D="$ip";; *) D="$ip:5555";; esac
  case "$ip" in emulator-*) D="$ip";; *) "$ADB" connect "$D" >/dev/null 2>&1;; esac
  if [ "$("$ADB" -s "$D" get-state 2>/dev/null)" != device ]; then
    echo ">>> Look at the TV: if it asks \"Allow USB debugging?\", tick \"Always allow\" and choose OK."
    for _ in $(seq 1 30); do
      sleep 2
      [ "$("$ADB" -s "$D" get-state 2>/dev/null)" = device ] && break
      case "$D" in emulator-*) ;; *) "$ADB" connect "$D" >/dev/null 2>&1;; esac
    done
  fi
  if [ "$("$ADB" -s "$D" get-state 2>/dev/null)" != device ]; then
    echo "!! Could not connect to the TV at $D."
    debug_help; exit 1
  fi
  STATE="$STATE_ROOT/$(echo "$D" | tr ':/' '__')"
  mkdir -p "$STATE"
  echo "$ip" > "$STATE_ROOT/last-tv"
}

A() { "$ADB" -s "$D" "$@"; }
S() { "$ADB" -s "$D" shell "$@" </dev/null | tr -d '\r'; }
installed() { S pm path "$PKG" 2>/dev/null | grep -q '^package:'; }
resumed() { S dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' | grep -o '[^ ]*/[^ ]*' | head -1; }
current_home() { S cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; }

# home.conf goes through the app (SettingsReceiver): Android 11+ TVs may not let adb write the app's files folder.
conf_broadcast() {  # conf_broadcast [base64]: prints the "Broadcast completed" line
  S am broadcast -f 32 -n "$PKG/.SettingsReceiver" ${1:+--es conf64 "$1"} | grep 'Broadcast completed'
}
conf_get() {  # prints the TV's home.conf
  local line; line=$(conf_broadcast)
  case "$line" in *result=-1*) ;; *) fail "LlarTV did not answer ($line). Is it installed and up to date?";; esac
  echo "$line" | sed -n 's/.*data="\([^"]*\)".*/\1/p' | base64 --decode
}
conf_put() {  # conf_put <file>: replaces the TV's home.conf with the file
  local line; line=$(conf_broadcast "$(base64 < "$1" | tr -d '\n')")
  case "$line" in *result=-1*) ;; *) fail "LlarTV did not accept the settings ($line).";; esac
}
