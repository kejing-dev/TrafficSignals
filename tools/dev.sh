#!/usr/bin/env bash
# Traffic Cop Signals developer helper (macOS). Every device command is pinned to the factory emulator
# (emulator-5554, AVD Pico_Emulator_6_1 on kj-mbp) and refuses to run when that serial is another AVD.
#
#   tools/dev.sh build | test          assembleDebug / unit tests        -> .scratch/build.log
#   tools/dev.sh install | launch | stop | relaunch
#   tools/dev.sh cmd 'NAME ARGS'       debug broadcast (app/src/debug/.../DebugCommands.kt)
#   tools/dev.sh log [REGEX] [LINES]   logcat, tag TrafficSignals
#   tools/dev.sh crash                 last crash lines of the app
#   tools/dev.sh shot NAME [DIR]       pico-cli screenshot -> DIR/NAME.png (default .scratch/shots)
DEV=${DEV:-emulator-5554}
AVD=${AVD:-Pico_Emulator_6_1}
PKG=io.embodit.trafficsignals
TAG=TrafficSignals
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export PICO_HOME="${PICO_HOME:-$HOME/Library/PICO/sdk}"
mkdir -p "$ROOT/.scratch"

check() {
  avd="$(adb -s $DEV emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  if [ "$avd" != "$AVD" ]; then echo "WRONG AVD on $DEV: '$avd' (expected $AVD)" >&2; exit 2; fi
}

case "${1:-}" in
  build)
    cd "$ROOT" && ./gradlew assembleDebug --console=plain > .scratch/build.log 2>&1
    rc=$?; echo "exit=$rc"; grep -E '^e: |error:|BUILD|FAILED' .scratch/build.log | head -40; exit $rc ;;
  test)
    cd "$ROOT" && ./gradlew testDebugUnitTest --console=plain > .scratch/test.log 2>&1
    rc=$?; echo "exit=$rc"; grep -E 'tests completed|FAILED|BUILD' .scratch/test.log | head -40; exit $rc ;;
  install)
    # force-stop first: install -r over a running Stage app leaves a false "isn't responding" dialog that only a
    # reboot clears (factory feedback, fire-plan / traffic-signals)
    check; adb -s $DEV shell am force-stop $PKG; adb -s $DEV install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" 2>&1 | tail -1 ;;
  launch)
    check; adb -s $DEV logcat -c; adb -s $DEV shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; echo launched ;;
  stop)
    check; adb -s $DEV shell am force-stop $PKG; echo stopped ;;
  relaunch)
    check; adb -s $DEV shell am force-stop $PKG; sleep 1; adb -s $DEV logcat -c; adb -s $DEV shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; echo relaunched ;;
  cmd)
    check; adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '${2:?cmd}'" | tail -1 | tr -d '\r'; sleep "${CMD_WAIT:-0.5}"
    adb -s $DEV logcat -d -v brief -s $TAG:V 2>/dev/null | grep "CMD " | tail -1 ;;
  log)
    check; adb -s $DEV logcat -d -v time -s $TAG:V 2>/dev/null | grep -E -- "${2:-.}" | tail -n "${3:-200}" | cut -c1-700 ;;
  crash)
    check; adb -s $DEV logcat -d -v time 2>/dev/null | grep -E "AndroidRuntime|FATAL EXCEPTION|Process: $PKG|ANR in" | tail -n 40 | cut -c1-400 ;;
  shot)
    check
    dir="${3:-$ROOT/.scratch/shots}"; mkdir -p "$dir"
    rm -f "$dir/${2:-shot}.png"; pico-cli capture screenshot --device $DEV --out "$dir/${2:-shot}.png" >/dev/null 2>&1 || adb -s $DEV exec-out screencap -p > "$dir/${2:-shot}.png"
    echo "$dir/${2:-shot}.png" ;;
  *) echo "usage: dev.sh build|test|install|launch|stop|relaunch|cmd|log|crash|shot" ;;
esac
