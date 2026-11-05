#!/usr/bin/env bash
# Real taps through `adb shell input` (window px), never a debug command (profile D43). The window's Compose gets them as
# pointer events. The system TabBar is its own small window at [0,0]-[704,176] on top of the main one in input order, so
#   tools/tap.sh NAME        taps a probed control of the window (`bounds`)
#   tools/tap.sh tab N       taps TabBar item N (0 home, 1 lessons, 2 levels, 3 review, 4 settings)
# Waits WAIT s (default 5), prints the state.
DEV=${DEV:-emulator-5554}; D="$(cd "$(dirname "$0")" && pwd)/dev.sh"
if [ "$1" = "tab" ]; then
  adb -s $DEV shell input tap $(( ($2 * 2 + 1) * 704 / 10 )) 88; name="tab$2"; xy="tabbar"
else
  "$D" cmd bounds >/dev/null
  line=$(adb -s $DEV logcat -d -s TrafficSignals:V | grep "BOUNDS [^ ]* $1 " | tail -1)
  [ -z "$line" ] && { echo "no control $1"; exit 2; }
  x=$(echo "$line" | sed -E 's/.*BOUNDS [^ ]+ [^ ]+ ([0-9-]+) ([0-9-]+) .*/\1/'); y=$(echo "$line" | sed -E 's/.*BOUNDS [^ ]+ [^ ]+ ([0-9-]+) ([0-9-]+) .*/\2/')
  adb -s $DEV shell input tap "$x" "$y"; name="$1"; xy="$x,$y"
fi
sleep "${WAIT:-5}"
echo "tap $name ($xy) -> $(adb -s $DEV logcat -d -s TrafficSignals:V | grep -E 'STATE ' | tail -1 | sed -E 's/.*STATE ([^ ]+) why=([^ ]+).*/\1 why=\2/')"
