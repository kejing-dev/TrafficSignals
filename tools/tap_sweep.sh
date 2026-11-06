#!/usr/bin/env bash
# Every key of every page gets one real tap at its centre (q3-3): tools/tap_sweep.sh. A tap passes when a new EVENT line shows
# up in the log; COVERED = centre inside the corner the TabBar / system windows take in adb coordinates (x<704, y<208 px).
D="$(cd "$(dirname "$0")" && pwd)/dev.sh"; DEV=${DEV:-emulator-5554}; pass=0; fail=0
events() { adb -s $DEV logcat -d -s TrafficSignals:V | grep -cE 'EVENT |TAB |WINDOW ask'; }
covered() { "$D" cmd bounds >/dev/null; sleep 1; adb -s $DEV logcat -d -s TrafficSignals:V | grep COVERED | tail -40 | sed -E 's/.*BOUNDS/BOUNDS/' | sort -u; adb -s $DEV logcat -c; "$D" cmd bounds >/dev/null; sleep 1; }
xy() { adb -s $DEV logcat -d -s TrafficSignals:V | grep "BOUNDS window $1 " | tail -1 | sed -E 's/.*BOUNDS window [^ ]+ ([0-9-]+) ([0-9-]+) .*/\1 \2/'; }
t() {
  pos=$(xy "$1"); [ -z "$pos" ] && { echo "FAIL $1: not on this page"; fail=$((fail+1)); return; }
  n0=$(events); adb -s $DEV shell input tap $pos; sleep "${2:-5}"; n1=$(events)
  if [ "$n1" -gt "$n0" ]; then echo "ok   $1 ($pos)"; pass=$((pass+1)); else echo "FAIL $1 ($pos): no event"; fail=$((fail+1)); fi
  "$D" cmd bounds >/dev/null; sleep 1
}
page() { echo "--- $1"; covered; }
tab() { case "$1" in 1) x=258 ;; 2) x=352 ;; 4) x=624 ;; esac; adb -s $DEV shell input tap $x 88; sleep 5; }
page home; t lang_1; t lang_0; t sound; t sound; t card_review
page review_empty; "$D" cmd 'seedMistakes 3' >/dev/null; sleep 3; "$D" cmd bounds >/dev/null; sleep 1; t review_drill
page drill_intro; t sound; t sound; t quiz_back
tab 1; page lessons; t lesson_0; page lesson0; t lesson_next; t lesson_prev; t lesson_play; t lesson_nextlesson
page lesson; t step_0; t lesson_next; t lesson_prev; t lesson_play; t angle_1; t angle_0; t lesson_speed_1; t lesson_speed_0; t lesson_back_list
tab 2; page levels; t level_4; page intro; t sound; t sound; t quiz_start
page question; t sound; t sound; t quiz_hint; t quiz_replay; t answer_stop
page verdict; t sound; t sound; t verdict_breakdown; page breakdown; t lesson_next; t lesson_back; t verdict_lane 12
page question_lane; t answer_go; t quiz_home; page confirm; t dialog_stay; t quiz_home; t dialog_leave
tab 2; page levels2; t level_1; t quiz_back
tab 4; page settings; t set_lang_1; t set_lang_0; t sound; t sound; t set_volume; t set_size_1 8; t set_size_0 8; t set_speed_1; t set_speed_0; t set_recenter 14; t set_reset; t dialog_cancel; t set_exit; t dialog_cancel
"$D" cmd 'result 8/10' >/dev/null; sleep 3; page result; "$D" cmd bounds > /dev/null; sleep 1; t result_again; t quiz_back
"$D" cmd 'result 8/10' >/dev/null; sleep 3; "$D" cmd bounds > /dev/null; sleep 1; t result_next; t quiz_back
"$D" cmd 'result 8/10' >/dev/null; sleep 3; "$D" cmd bounds > /dev/null; sleep 1; t result_home
page home_end; t recenter 14
echo "pass=$pass fail=$fail"
