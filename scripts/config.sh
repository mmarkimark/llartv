#!/bin/bash
# Change LlarTV's settings (home.conf) and restart it.
# Usage: ./scripts/config.sh [--tv <TV-IP>] key=value [key=value ...]   set one or more settings
#        ./scripts/config.sh [--tv <TV-IP>] --dock                      pick the dock apps from a list
#        ./scripts/config.sh [--tv <TV-IP>] --show                      show the current settings
# Keys: columns dock hidden wallpaper wallpaper_minutes morning night location news_rss dock_tint
#       team_id team_name. An empty value (key=) removes the setting and restores its default.
set -u
# shellcheck source=scripts/lib.sh
source "$(dirname "$0")/lib.sh"
KEYS=" columns dock hidden wallpaper wallpaper_minutes morning night location news_rss dock_tint team_id team_name "

IP=""; SHOW=0; PICK=0; PAIRS=()
while [ $# -gt 0 ]; do case "$1" in
  --tv) IP="${2:-}"; shift 2;;
  --show) SHOW=1; shift;;
  --dock) PICK=1; shift;;
  -h|--help) sed -n 2,7p "$0" | sed 's/^# \{0,1\}//'; exit 0;;
  *=*) k="${1%%=*}"; case "$KEYS" in *" $k "*) PAIRS+=("$1");; *) fail "Unknown setting '$k'. Valid keys:$KEYS";; esac; shift;;
  *) fail "Unexpected '$1' (see --help)";;
esac; done
[ "$SHOW" = 1 ] || [ "$PICK" = 1 ] || [ ${#PAIRS[@]} -gt 0 ] || { sed -n 2,7p "$0" | sed 's/^# \{0,1\}//'; exit 1; }

connect "$IP"
installed || fail "LlarTV is not installed on this TV. Run ./scripts/install.sh first."
CONF="$STATE/home.conf"
conf_get > "$CONF"

if [ "$SHOW" = 1 ]; then
  say "Settings on the TV (anything not listed uses its default):"
  grep -v '^[[:space:]]*\(#\|$\)' "$CONF" | sed 's/^/   /'
  [ "$PICK" = 0 ] && [ ${#PAIRS[@]} -eq 0 ] && exit 0
fi

if [ "$PICK" = 1 ]; then
  APPS=$(S cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER |
         grep -o 'packageName=[^ ]*' | cut -d= -f2 | sort -u | grep -vx "$PKG")
  say "Apps on the TV:"
  i=0; for a in $APPS; do i=$((i+1)); printf "   %2d) %s\n" "$i" "$a"; done
  read -r -p "Dock apps by number, left to right (for example: 3 1 7 2): " NUMS
  DOCK=""
  for n in $NUMS; do a=$(echo "$APPS" | sed -n "${n}p"); [ -n "$a" ] && DOCK="${DOCK:+$DOCK,}$a"; done
  [ -n "$DOCK" ] || fail "No apps chosen; nothing changed."
  PAIRS+=("dock=$DOCK")
fi

for kv in ${PAIRS[@]+"${PAIRS[@]}"}; do
  k="${kv%%=*}"
  grep -v "^[[:space:]]*${k}[[:space:]]*=" "$CONF" > "$CONF.new"; mv "$CONF.new" "$CONF"
  [ -n "${kv#*=}" ] && echo "$kv" >> "$CONF"
done

# Keep a header line, so the file is never empty (an empty message would only read the settings back).
grep -q '^# LlarTV settings' "$CONF" || { echo "# LlarTV settings - see https://github.com/$REPO#configuration"; cat "$CONF"; } > "$CONF.new"
[ -f "$CONF.new" ] && mv "$CONF.new" "$CONF"

say "New settings:"
grep -v '^[[:space:]]*\(#\|$\)' "$CONF" | sed 's/^/   /'
conf_put "$CONF"
S am force-stop "$PKG"; S input keyevent KEYCODE_HOME
say "Done. LlarTV restarted with the new settings."
