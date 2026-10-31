#!/usr/bin/env bash
# Cold start N times; each run waits for STATE S2, then idles 60 s; counts ANRs of the app (factory gate A9).
DEV=emulator-5554; PKG=io.embodit.trafficsignals; N=${1:-3}
for k in $(seq 1 $N); do
  adb -s $DEV shell am force-stop $PKG; sleep 2; adb -s $DEV logcat -c
  t0=$(date +%s); adb -s $DEV shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  ready=""
  for i in $(seq 1 60); do adb -s $DEV logcat -d -s TrafficSignals:V | grep -q "STATE S2" && { ready=$(( $(date +%s) - t0 )); break; }; sleep 2; done
  sleep 60
  anr=$(adb -s $DEV logcat -d -b events | grep am_anr | grep -c $PKG)
  crash=$(adb -s $DEV logcat -d | grep -c "FATAL EXCEPTION")
  built=$(adb -s $DEV logcat -d -s TrafficSignals:V | grep -o "SCENE built ms=[0-9]*")
  echo "run $k: S2 after ${ready:-timeout}s, $built, ANR=$anr, crash=$crash"
done
