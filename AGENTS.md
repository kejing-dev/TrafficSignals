# Traffic Cop Signals (交警手势)

Sit in the driver's seat of a virtual crossroads; a life-size traffic officer on the centre post signals one of the 8
official Chinese police hand signals while facing a random direction. You decide whether the signal is for your lane:
go, stop, pull into the waiting box, slow down, clear the lane or pull over. PICO OS 6, Kotlin, PICO Spatial SDK 6.1.9,
package `io.embodit.trafficsignals`. Skeleton C, container **STAGE_FULL** (manifest `pico.spatial.stage.style=3`),
window model `stage_with_attachment_panels`: P1 home 850×475 dp, P2 question 800×400, P3 verdict 625×575, P4 lesson
700×575, DLG 500×250 (all at scale 1 = 0.0008 m/dp). No WindowContainer, no permissions, no network.

Design package (binding): `../../spatial-app-factory/apps/traffic-signals/design-package.json`. PRD
https://claude.ai/artifact/WRybtgGkGfQ15wFNfjZ51i, sample https://claude.ai/artifact/C5FFpDzhso2DL7ZYueDQrJ.
Scaffold: `pico-cli project create --sdk-type spatial --template stage`, then the panel pool, head tracker, theme,
fonts and debug receiver ported from Rehearsal Hall (kejing-dev/RehearsalHall), the thumbstick from Figure Model.

## Structure

```
app/src/main/java/io/embodit/trafficsignals/
  Main.kt                   DefaultStage (Full) -> TsScreen
  domain/                   pure Kotlin, JVM-tested (app/src/test)
    Traffic.kt              Compass / Facing (SOUTH = facing you) / Side / Lane / Gesture / Answer, Judge (the 50
                            askable combinations of design judging.table, 13 official), releases, Levels 1-6 + drill
    Content.kt              bilingual copy: gesture names, the 43 official step texts word for word, mnemonics
    Geometry.kt Vec3.kt     HeadPose, Yaw, Upright (the ONE placement function: head position + yaw, pitch/roll = 0)
  platform/                 TsLog (tag TrafficSignals), Store (prefs), Sound (synth ding/doo/engine/hum, 40 %),
                            HeadTracker, Stick (thumbstick X), LaunchActivity, SpatialApplication
  ui/
    TsUiState.kt            Screen / Phase / Dialog, designState() -> S1..S18
    TsController.kt         ALL behaviour: events -> state (also the L6 5 s timer); debug seams at the bottom
    TsScreen.kt             wires controller, scene, panels; lifecycle -> pause (S18)
    scene/TrafficScene.kt   ECS: build (IO for resources), placement, lane fade, officer turn + clips, flows, cars
    scene/Street.kt         the painted crossroads (2048² texture), props, cars, bonnet, Bézier car paths, ribbons
    scene/Palette.kt        three-shade colour atlas (sun from the south-west baked in) + Toon mesh builder
    scene/Officer.kt        the Tripo GLB: play / freeze clips, timing from assets/officer_clips.json
    scene/PanelRig.kt       the pre-built panel pool (Spatial Ruler findings)
    panels/                 TsStage (SpatialView + 5 panels), Panels.kt (P1-P4 + DLG), Copy.kt (UI strings zh/en)
app/src/debug/              DebugCommandReceiver + DebugCommands (QA hooks)
app/src/main/assets/        officer.glb (baked), officer_clips.json
tools/officer/              bake_officer.py (Tripo GLB -> calibrated GLB + 9 clips), check_officer.py (frame check)
tools/                      dev.sh, make_icon.py, subset_cjk_font.py, contact.py
```

Conventions: Stage metres, driver frame = road under your eye, -Z ahead (north), +X right. The junction centre is at
(-(laneX - 0.4), 0, -14) in the driver frame; the officer's front is his local +Z (GLB calibrated: feet centre at the
origin, face +Z, 1.75 m). Officer yaw = `Compass.yaw` (atan2(x, z)).

## Officer asset pipeline

The raw Tripo GLB (`traffic_cop_summer.glb`, not in git) lives in `../traffic-signals-assets/tripo/` (copy from the
factory's draft release `traffic-signals-assets`). Re-bake after changing a pose:

```bash
blender -b --factory-startup -P tools/officer/bake_officer.py -- ../traffic-signals-assets/tripo/traffic_cop_summer.glb \
  app/src/main/assets/officer.glb app/src/main/assets/officer_clips.json
blender -b --factory-startup -P tools/officer/check_officer.py -- app/src/main/assets/officer.glb \
  app/src/main/assets/officer_clips.json ../traffic-signals-assets/framecheck
```

Timing (design gestures.timing): 0.4 s stand, each step 0.6 s motion + 0.25 s hold (stop step 1 holds 1.5 s), 0.6 s
stand at the end; `steps[k]` in the json is the time the pose of step k is reached (the freeze point).

## Build, run (macOS, factory emulator emulator-5554 / AVD Pico_Emulator_6_1)

```bash
tools/dev.sh build && tools/dev.sh install && tools/dev.sh relaunch
tools/dev.sh test                          # JVM tests: judge table, levels, placement gates, calibration
tools/dev.sh log 'STATE|PLACE|STARTUP|GESTURE|FLOW' 60
tools/dev.sh crash
tools/dev.sh shot NAME [DIR]
python3 tools/make_icon.py ../../spatial-app-factory/apps/traffic-signals/icon.png
python3 tools/subset_cjk_font.py           # after adding Chinese copy anywhere in app/src/main/java
```

## Debug broadcast (debug builds only)

`adb -s emulator-5554 shell "am broadcast -a io.embodit.trafficsignals.DEBUG -p io.embodit.trafficsignals --es cmd 'NAME ARGS'"`
or `tools/dev.sh cmd 'NAME ARGS'`. Each answers `CMD NAME -> ok state=<design id>`; every state change logs
`STATE <id> why=...`. Commands send the same events as the panels (factory rule: no shortcut state writes).

| command | effect |
|---|---|
| `state S2/S16/S17/S11/SET/S3/S4/S5/S6/S7/S8/S9/S10/S12/S13/S14/S15/S18` | go to a design state (S6 = level 4, first question) |
| `lesson N [step K]` / `step K` / `play` | lesson 0-8, freeze on step K (0-based) / play again |
| `level N` then `start` | level intro (S5), first question (S6) |
| `q gesture,FACING,LANE` | one-question level, e.g. `q leftWait,WEST,LEFT`, `q straight,EAST,STRAIGHT` |
| `facing SOUTH/WEST/NORTH/EAST` | stop signal at the post facing that way (readability shots) |
| `freeze K` / `freeze off` | hold the quiz gesture at step K |
| `answer go/stop/wait/slow/lane/pull`, `hint`, `replay`, `next`, `switchLane`, `breakdown`, `home`, `back` | panel buttons |
| `timeout` | L6 time-out now (S14) |
| `result 8/10`, `seedMistakes 6`, `dialog leave/exit/reset/close`, `block` | S10, S11 with rows, DLG, S13 |
| `dialog confirm` / `dialog cancel` | press the open dialog's right / left button with the panel's own event: on the exit dialog (or S13) `confirm` = `TsEvent.Exit`, the app calls `finishAndRemoveTask()` and ends its process; reset -> ResetProgress; leave -> Leave |
| `quit` | the whole D26 path: settings -> [Quit app] -> [Quit] (`EXIT confirmed` in the log, then the process is gone) |
| `lang zh/en`, `sound on/off`, `big on/off`, `slow on/off` | settings |
| `panels 0/1`, `cam front/side` | hide every panel / officer turned 90° |
| `stick X` / `stick off` | fake thumbstick X (I8) |
| `yaw DEG` / `yaw off`, `recenter` | fake head yaw, re-place (I10) |
| `background`, `resume` | S18 and back |
| `name LABEL` / `name clear`, `dump` | name override, scene + instance dump (`DUMP` lines) |

Other log lines QA can grep: `GHOST from=… t0=… dEye=…` (your ghost car's first visible point and its distance from the
eye, always >= 5.7 m), `OVERFLOW name=… text=…` (a fixed-line text that does not fit: must never appear), `PALETTE …`
(the live theme colours; the vibrant grey roles print as #FF000000, see tools/contrast.py).

## Contrast gate (build prompt, before every QA hand-off)

`python3 tools/contrast.py <shots dir>` (python3 without -I, needs Pillow) prints the table of every text colour x
background pair: fixed colours exactly, the SpatialUI vibrant roles measured on the tour screenshots (boxes in
`tools/contrast_samples.py`, re-pick them when a layout moves). Exit 1 if any pair is < 4.5:1 (graphics < 3:1).

## Screenshot list (QA)

| shot | commands |
|---|---|
| S1 | relaunch, capture within 2 s |
| S2 | wait for `STATE S2` |
| S16 / S17 / S11 / SET | `state S16` / `state S17` / `state S11` (+ `seedMistakes 6`) / `state SET` |
| S3 / S4 | `lesson 3` / `lesson 3 step 1` |
| S4 frame check | `panels 0`, `lesson G step K`, `cam front` / `cam side` (43 × 2; the Blender frame check covers the same poses) |
| S5 / S6 | `level 4` / `start` |
| S7 / S8 | `q straight,EAST,STRAIGHT` + `answer go` / `answer stop`; wait 1.5 s |
| ghost car (q1-1) | `q straight,EAST,STRAIGHT` / `q right,EAST,RIGHT` / `q left,WEST,LEFT` + `answer go`, `q leftWait,WEST,LEFT` + `answer wait`; shoot at once and again after 1.5 s |
| D26 exit | `state SET`, `dialog exit` (shot), `dialog confirm`; then `adb shell dumpsys activity recents` has no io.embodit.trafficsignals and `pidof` is empty; relaunch -> S1 / S2 |
| S9 | from S7 `switchLane` |
| L3 wait box | `q leftWait,WEST,LEFT` + `answer wait` |
| S10 / S12 / S13 / S14 | `result 8/10` / `dialog leave` / `block` / `state S14` |
| S15 | `lang en` then S2 / S6 / S7 / S4 |
| S18 | `background` |
| readability | `panels 0`, `facing SOUTH|WEST|NORTH|EAST` |

## Platform facts found here

- `Entity.load("asset://officer.glb")` on Dispatchers.IO, then `findSkinnedMeshEntity()` + `getAnimationResources()`
  (names = glTF animation names); `playAnimation(res, AnimationPlayConfig(fade))`, `setTime` + `pause` freeze a step.
- Mesh / texture / material resources are created on Dispatchers.IO (main-thread creation costs about a frame each).
- An `OpacityControllerComponent` is removed when the fade reaches 1 (a mounted one blends even at 1.0).
- Stage dialogs are self-built attachment panels (AlertDialog composes nothing in a Stage).

## UI rules

All 2D UI is SpatialUI inside `PicoTheme`. No Material / Material3 (gate: `../../spatial-app-factory/bin/material-grep.txt`).
Icons are Material Symbols Rounded vector drawables (Apache-2.0), every icon button carries a text label.
