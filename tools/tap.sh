#!/usr/bin/env bash
# Real taps through `adb shell input` (window px), never a debug command (profile D43). The window's Compose gets them as
# pointer events. The system TabBar is its own small window at [0,0]-[704,176] on top of the main one in input order, so
#   tools/tap.sh NAME        taps a probed control of the window (`bounds`)
#   tools/tap.sh tab N       taps TabBar item N (1 lessons, 2 levels, 4 settings; 0 home and 3 review are covered, see below)
# Waits WAIT s (default 5), prints the state.
DEV=${DEV:-emulator-5554}; D="$(cd "$(dirname "$0")" && pwd)/dev.sh"
if [ "$1" = "tab" ]; then
  # systemui windows cover 主页 (16-144) and 错题本 (424-552) in adb coordinates (QA round 3): those two can't be reached here
  case "$2" in 1) x=258 ;; 2) x=352 ;; 4) x=624 ;; *) echo "tab $2 is covered in adb coordinates (device checklist)"; exit 3 ;; esac
  adb -s $DEV shell input tap $x 88; name="tab$2"; xy="tabbar"
else
  "$D" cmd bounds >/dev/null
  line=$(adb -s $DEV logcat -d -s TrafficSignals:V | grep "BOUNDS [^ ]* $1 " | tail -1)
  [ -z "$line" ] && { echo "no control $1"; exit 2; }
  x=$(echo "$line" | sed -E 's/.*BOUNDS [^ ]+ [^ ]+ ([0-9-]+) ([0-9-]+) .*/\1/'); y=$(echo "$line" | sed -E 's/.*BOUNDS [^ ]+ [^ ]+ ([0-9-]+) ([0-9-]+) .*/\2/')
  adb -s $DEV shell input tap "$x" "$y"; name="$1"; xy="$x,$y"
fi
sleep "${WAIT:-5}"
echo "tap $name ($xy) -> $(adb -s $DEV logcat -d -s TrafficSignals:V | grep -E 'STATE ' | tail -1 | sed -E 's/.*STATE ([^ ]+) why=([^ ]+).*/\1 why=\2/')"
