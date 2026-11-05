#!/usr/bin/env bash
# The core flow by real taps only (profile D43): tools/tap_flow.sh [OUT_DIR]. Learn one lesson, play level 1 to its
# score, the settings confirmations. Prints one line per tap and saves shots of the steps that matter.
T="$(cd "$(dirname "$0")" && pwd)/tap.sh"; D="$(cd "$(dirname "$0")" && pwd)/dev.sh"; OUT="${1:-.scratch/v012/flow}"; mkdir -p "$OUT"
state() { adb -s ${DEV:-emulator-5554} logcat -d -s TrafficSignals:V | grep -E 'STATE ' | tail -1 | sed -E 's/.*STATE ([^ ]+) .*/\1/'; }
shot() { sleep "${SHOT_WAIT:-8}"; "$D" shot "$1" "$OUT" >/dev/null; }
"$T" tab 0
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
