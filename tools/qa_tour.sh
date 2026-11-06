#!/usr/bin/env bash
# Screenshot tour of the design states (AGENTS.md "Screenshot list"). Output: $OUT/<name>.png + tour.txt
# Usage: tools/qa_tour.sh [OUT_DIR]   (the app must be on S2; ~10 s per shot because the emulator renders at 1-4 fps)
OUT="${1:-../../spatial-app-factory/apps/traffic-signals/build/states}"; mkdir -p "$OUT"
D="$(cd "$(dirname "$0")" && pwd)/dev.sh"
WAIT=${WAIT:-9}
c() { "$D" cmd "$1" >/dev/null; }
shot() { sleep "$WAIT"; "$D" shot "$1" "$OUT" >/dev/null; sleep 2; "$D" shot "$1" "$OUT" >/dev/null; st=$(adb -s emulator-5554 logcat -d -s TrafficSignals:V | grep "STATE " | tail -1 | sed -E 's/.*STATE ([^ ]+).*/\1/'); echo "$1 -> $st" | tee -a "$OUT/tour.txt"; }
: > "$OUT/tour.txt"
c 'home'; c 'lang zh'; shot S2
c 'state S16'; shot S16
c 'state S17'; shot S17
c 'dialog close'; c 'clear'; c 'state S11'; shot S11_empty
c 'seedMistakes 6'; c 'state S11'; shot S11_rows
c 'state SET'; shot SET
c 'dialog exit'; shot SET_exit_dialog; c 'dialog close'
c 'lesson 3'; shot S3_P4
c 'step 1'; shot S4_P4
c 'lesson 0'; shot S3_lesson0
c 'panels 0'; c 'yaw -40'; c 'recenter'; c 'lesson 2 step 2'; shot S4_officer_front
c 'stick 1'; sleep 3; c 'stick off'; shot S4_stick_turned
c 'yaw off'; c 'recenter'; c 'panels 1'; sleep 8; c 'level 4'; shot S5
c 'q straight,EAST,STRAIGHT'; sleep 4; shot S6
c 'answer go'; shot S7_P3
c 'switchLane'; sleep 6; shot S9_lane_switched
c 'q straight,EAST,LEFT'; sleep 4; c 'answer go'; shot S8_P3
c 'q leftWait,WEST,LEFT'; sleep 4; c 'hint'; shot S6_hint
c 'answer wait'; shot S7_waitbox
c 'q laneChange,SOUTH,STRAIGHT'; sleep 4; c 'answer lane'; shot S7_lanechange
c 'breakdown'; shot S7_breakdown; c 'back'
c 'state S10'; shot S10
c 'state S12'; shot S12
c 'dialog close'; c 'state S14'; shot S14
c 'home'; c 'dialog confirm'; c 'background'; shot S18; c 'resume'
c 'lang en'; c 'home'; shot S15_S2
c 'state S16'; shot S15_S16
c 'state S17'; shot S15_S17
c 'state SET'; shot S15_SET
c 'dialog exit'; shot S15_SET_exit_dialog; c 'dialog close'; c 'dialog reset'; shot S15_SET_reset_dialog; c 'dialog close'
for l in 4 5 6; do c "level $l"; shot S15_S5_level$l; c 'start'; shot S15_S6_level$l; done
c 'q right,EAST,RIGHT'; sleep 4; shot S15_S6
c 'answer go'; shot S15_S7_P3
c 'q straight,EAST,LEFT'; sleep 4; c 'answer go'; shot S15_S8_P3
c 'lesson 5 step 1'; shot S15_S4_P4
c 'state S14'; shot S15_S14
c 'state S10'; shot S15_S10
c 'lang zh'; c 'home'
c 'panels 0'; for f in SOUTH WEST NORTH EAST; do c "facing $f"; c 'freeze 0'; shot read_$f; done; c 'freeze off'; c 'panels 1'
c 'home'
c 'block'; shot S13
