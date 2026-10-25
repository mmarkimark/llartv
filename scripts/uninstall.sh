#!/bin/bash
# Remove LlarTV and put back the TV's own home screen and screensaver.
# Usage: ./scripts/uninstall.sh [TV-IP]
set -u
# shellcheck source=scripts/lib.sh
source "$(dirname "$0")/lib.sh"
[ "${1:-}" = -h ] || [ "${1:-}" = --help ] && { sed -n 2,3p "$0" | sed 's/^# \{0,1\}//'; exit 0; }

connect "${1:-}"

while read -r p; do
  [ -n "$p" ] && echo "   re-enabling $p: $(S pm enable --user 0 "$p" </dev/null)"
done < <(cat "$STATE/disabled" 2>/dev/null)
: > "$STATE/disabled"

PREV_DREAM=$(cat "$STATE/previous-screensaver" 2>/dev/null)
if [ "$(S settings get secure screensaver_components)" = "$DREAM" ]; then
  if [ -n "$PREV_DREAM" ] && [ "$PREV_DREAM" != null ] && [ "$PREV_DREAM" != "$DREAM" ]; then
    S settings put secure screensaver_components "$PREV_DREAM"
  else
    S settings delete secure screensaver_components >/dev/null
  fi
  say "Screensaver restored"
fi

PREV_HOME=$(cat "$STATE/previous-home" 2>/dev/null)
if installed; then
  say "Removing LlarTV: $(A uninstall "$PKG" 2>&1 | tail -1)"
else
  say "LlarTV was not installed"
fi
case "$PREV_HOME" in */*) [ "$PREV_HOME" != "$HOME_ACT" ] && S cmd package set-home-activity "$PREV_HOME" >/dev/null;; esac
rm -f "$STATE/previous-home" "$STATE/previous-screensaver" "$STATE/home.conf"

S input keyevent KEYCODE_HOME; sleep 3
say "Done. The Home button now opens $(current_home)."
