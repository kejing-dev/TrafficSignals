#!/usr/bin/env bash
# The core flow by real taps only (profile D43, adb input tap), on the APK installed now. tools/tap_flow.sh [OUT_DIR]
# learn one lesson -> level 1 to its score -> home -> settings confirmations -> L6: time-out, leave during the signal -> quit.
T="$(cd "$(dirname "$0")" && pwd)/tap.sh"; D="$(cd "$(dirname "$0")" && pwd)/dev.sh"; OUT="${1:-.scratch/v013/flow}"; mkdir -p "$OUT"
DEV=${DEV:-emulator-5554}
state() { adb -s $DEV logcat -d -s TrafficSignals:V | grep -E 'STATE ' | tail -1 | sed -E 's/.*STATE ([^ ]+) .*/\1/'; }
shot() { sleep "${SHOT_WAIT:-8}"; "$D" shot "$1" "$OUT" >/dev/null; }
"$T" card_learn; shot 01_lessons
"$T" lesson_2; shot 02_lesson
"$T" lesson_play; "$T" lesson_next; shot 03_lesson_step
"$T" lesson_nextlesson; "$T" lesson_back_list
"$T" tab 2; shot 04_levels
"$T" level_1; shot 05_intro
"$T" quiz_start; shot 06_question
n=0
while [ $n -lt 40 ]; do
  s=$(state); case "$s" in
    S6*) "$T" answer_go; [ $n = 0 ] && shot 07_verdict ;;
    S7*|S8*|S9*) "$T" verdict_next ;;
    S10*) break ;;
    *) echo "unexpected state $s"; sleep 3 ;;
  esac
  n=$((n+1))
done
shot 08_result
"$T" result_home; shot 09_home
"$T" tab 4; shot 10_settings
"$T" set_reset; shot 11_reset_confirm; "$T" dialog_cancel
"$T" set_exit; shot 12_exit_confirm; "$T" dialog_cancel
echo "== L6 time-out"
"$T" tab 2; "$T" level_6; shot 13_L6_intro; WAIT=30 "$T" quiz_start; shot 14_L6_timeout
"$T" quiz_home; "$T" dialog_leave
echo "== L6 leave during the signal"
"$T" tab 2; "$T" level_6
WAIT=1 "$T" quiz_start; WAIT=1 "$T" quiz_home; shot 15_L6_leave_during_signal
sleep 14
"$D" log 'GESTURE|STATE|EVENT Home' 12 | cut -c1-130
echo "-- 14 s behind the dialog: now keep going"
WAIT=30 "$T" dialog_stay
"$D" log 'GESTURE|STATE|EVENT CloseDialog' 8 | cut -c1-130
shot 16_L6_after_keep_going
"$T" quiz_home; "$T" dialog_leave
echo "== quit"
"$T" tab 4; "$T" set_exit; WAIT=6 "$T" dialog_exit
echo "pidof=[$(adb -s $DEV shell pidof io.embodit.trafficsignals)]"; adb -s $DEV logcat -d -s TrafficSignals:V | grep EXIT | tail -1 | cut -c1-120
