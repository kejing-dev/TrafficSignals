# Traffic Cop Signals (交警手势)

Sit in the driver's seat of a virtual crossroads; a life-size traffic officer on the centre post signals one of the 8
official Chinese police hand signals while facing a random direction. You decide whether the signal is for your lane:
go, stop, pull into the waiting box, slow down, clear the lane or pull over. PICO OS 6, Kotlin, PICO Spatial SDK 6.1.9,
package `io.embodit.trafficsignals`. Skeleton C, container **STAGE_FULL** (manifest `pico.spatial.stage.style=3`).
v0.1.2 (profile D8 / D21, factory snippet native-window-move): all 2D UI is **one system Planar window** (760×560 dp,
opened from the Stage) with the **native TabBar**; the system moves / closes it. Every state draws in it, one
at a time (P2 / P3 / P4 / DLG of the design became pages of the window). The only attachment panel left is the Stage chip
(打开面板 / 平视前方). No permissions, no network.

Design package (binding): `../../spatial-app-factory/apps/traffic-signals/design-package.json`. PRD
`../../spatial-app-factory/apps/traffic-signals/html/prd.html`, sample `../../spatial-app-factory/apps/traffic-signals/sample.html` (local HTML in the factory repo).
Scaffold: `pico-cli project create --sdk-type spatial --template stage`, then the panel pool, head tracker, theme,
fonts and debug receiver ported from Rehearsal Hall (kejing-dev/RehearsalHall), the thumbstick from Figure Model.

**v0.1.5 (QA round 5, D44 short pages).** Every page keeps blank bands <= 25 % of the window (QA's `blank_measure.py`):
confirmations are one template (136 dp icon disc, 32 sp question, 240 x 72 dp keys, spread over the window), the result page
is a score disc + the round's / missed questions, the mistakes book shows 1-3 mistakes as mini-map cards and an empty state
with [路口判官] (`review_go`), level intros list the signals of the level and three reading rules, lessons of <= 4 steps get
big step cards. Shot / measure: `tools/dev.sh shot NAME DIR` at the fixed head pose, window rect x 725-1891, y 848-1709.

## Structure

```
app/src/main/java/io/embodit/trafficsignals/
  Main.kt                   DefaultStage (Full) -> TsScreen
  domain/                   pure Kotlin, JVM-tested (app/src/test)
    Traffic.kt              Compass / Facing (SOUTH = facing you) / Side / Lane / Gesture / Answer, Judge (the 50
                            askable combinations of design judging.table, 13 official), releases, Levels 1-6 + drill
    Content.kt              bilingual copy: gesture names, the 43 official step texts word for word, mnemonics
    Geometry.kt Vec3.kt     HeadPose, Yaw, Upright (head position + yaw, pitch/roll = 0)
    StageLayout.kt          officer spots, seat yaw, window gap, SeatPlacer; LevelGate.kt (D9: open the window on a level head)
  platform/                 TsLog (tag TrafficSignals), Store (prefs), Sound (synth ding/doo/engine/hum, 40 %),
                            HeadTracker, Stick (thumbstick X), LaunchActivity, SpatialApplication
  ui/
    TsUiState.kt            Screen / Phase / Dialog, designState() -> S1..S18
    TsController.kt         ALL behaviour: events -> state (also the L6 5 s timer); debug seams at the bottom
    TsScreen.kt             wires controller, scene, panels; lifecycle -> pause (S18)
    scene/TrafficScene.kt   ECS: build (IO for resources), placement, lane fade, officer turn + clips, flows, cars
    scene/Street.kt         the painted crossroads (2048² texture), props, Bézier car paths, ribbons
    scene/CarKit.kt         the cars and your own car as lofted smooth meshes (PBR, `Mats.paint`); no box helper
    scene/Palette.kt        three-shade colour atlas (sun from the south-west baked in) + Toon mesh builder
    scene/Officer.kt        the Tripo GLB: play / freeze clips, timing from assets/officer_clips.json
    scene/PanelRig.kt       the panel slot of the Stage chip (Spatial Ruler findings)
    window/                 TsWindow (WindowCtl: open / recall / level gate; the window's content), MainWindowTemplate
    panels/                 TsStage (SpatialView + the chip), Panels.kt (WindowPage: every page), Copy.kt (UI strings zh/en)
app/src/debug/              DebugCommandReceiver + DebugCommands (QA hooks)
app/src/main/assets/        officer.glb (baked), officer_clips.json
tools/officer/              bake_officer.py (Tripo GLB -> calibrated GLB + 9 clips), check_officer.py (frame check)
tools/                      dev.sh, make_icon.py, subset_cjk_font.py, contact.py
```

Conventions: Stage metres, driver frame = road under your eye, -Z ahead (north), +X right. The junction centre is at
(-(laneX - 0.4), 0, -14) in the driver frame; the officer's front is his local +Z (GLB calibrated: feet centre at the
origin, face +Z, 1.75 m). Officer yaw = `Compass.yaw` (atan2(x, z)).

**Seat and window (`domain/StageLayout.kt`, tests `StageLayoutTest`).** The system opens the window on the line of sight
and nothing can move it, so the road turns instead: driver yaw = sight yaw + `seatYaw(lane)`, which keeps the officer 36°
left of the window's centre in every lane, 39° at ×1.5, the lesson post 4° further (gap to the window edge >= 10° everywhere, D41;
the window draws 1 dp as 0.00173 m, not the converter's 0.0008). The eye sits on the
turning axis. He is on a post from the moment the scene is built (`openingFeet`), never at the driver origin (D14: the
2026-11-05 headset report: until the first placement he stood at the origin, which is you). `SeatPlacer` does the placing
(start, [回正], window reopened elsewhere). The steps of a verdict move him to the near post too, in every lane (`lessonSpot(laneX)`),
through the same fade as a lane change. The driver root must stay enabled while the GLB loads: `findSkinnedMeshEntity`
finds nothing below a disabled parent.

**Light (B24).** Officer: PBR, `assets/ibl/city_ibl.ktx` (tools/make_ibl.py) at exponent 9 + one warm sun 3000 lux. Street:
Unlit, cars PBR under the same lights. `tools/officer_light.py SHOT.png` measures the burnt-out share on a close-up; `light ibl EXP` / `light sun LUX` tune live.

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
| `result 8/10`, `seedMistakes 6`, `dialog leave/exit/reset/clear/close`, `block` | S10, S11 with rows, DLG, S13 |
| `dialog confirm` / `dialog cancel` | press the open dialog's right / left button with the panel's own event: on the exit dialog (or S13) `confirm` = `TsEvent.Exit`, the app calls `finishAndRemoveTask()` and ends its process; reset -> ResetProgress; leave -> Leave |
| `quit` | the whole D26 path: settings -> [Quit app] -> [Quit] (`EXIT confirmed` in the log, then the process is gone) |
| `lang zh/en`, `sound on/off`, `big on/off`, `slow on/off` | settings |
| `panels 0/1`, `cam front/side` | close the window and hide the chip / officer turned 90° |
| `window close` / `window chip` / `window open` | close the window as × does / the chip's path / ask for it |
| `pitch DEG` / `pitch off` | fake head pitch for the level gate (S1 hint 平视前方) |
| `ray ox oy oz dx dy dz` / `ray off` | fake a controller ray (the 6.1 emulator has none) |
| `light ibl EXP`, `light sun LUX` | tune the lights live |
| `bounds` | `BOUNDS window NAME cx cy w h`: every probed control, window px, for `adb shell input tap` |
| `stick X` / `stick off` | fake thumbstick X (I8) |
| `yaw DEG` / `yaw off`, `recenter` | fake line-of-sight yaw, re-place (I10). `panels 0` + `yaw -40` + `recenter` (lesson post) puts the officer in the middle of a shot |
| `background`, `resume` | S18 and back |
| `name LABEL` / `name clear`, `dump` | name override, scene + instance dump (`DUMP` lines) |

Other log lines QA can grep: `GHOST from=… t0=… dEye=…` (your ghost car's first visible point and its distance from the
eye, always >= 5.7 m), `OVERFLOW name=… text=…` (a fixed-line text that does not fit: must never appear), `PALETTE …`
(the live theme colours; the vibrant grey roles print as #FF000000, see tools/contrast.py).

## Real taps (profile D43)

`tools/tap.sh NAME` taps a probed control with `adb shell input tap` (window px), `tools/tap.sh tab N` taps TabBar item N. In
adb coordinates the TabBar window [0,0]-[704,176] and systemui windows [0,0]-[236,208], [364,0]-[600,208] lie on top of the main
window: no key may have its centre in x < 704, y < 208 px (`bounds` marks those `COVERED`), and tabs 0 and 3 can't be tapped here.
**In English the TabBar window is [0,0]-[1199,176]** (labels make it wider), so the top row is covered up to x = 1199 px:
keep keys out of the top row unless they sit right of that (v0.1.4 moved the review [清空] to the bottom row and the quiz [声音]
to the second row / the verdict's action row). `EN=1 tools/tap.sh tab N` uses the English positions (1 = 360, 2 = 640, 4 = 1065).
`tools/tap_flow.sh OUT` walks the core flow with real taps only; `tools/tap_sweep.sh` taps every key of every page once
(`EN=1 tools/tap_sweep.sh` runs it in English; do both before QA).

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
| chip / hint | `window close` / `pitch -30`, `recenter` |
| D41 | S6 and S3 as they come (officer left of the window); `ray 0.2 1.3 -0.2 0 0.1 -1` |
| S4 frame check | `panels 0`, `yaw -40`, `recenter`, `lesson G step K`, `cam front` / `cam side` (43 × 2; the Blender frame check covers the same poses) |
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

## Sound

`domain/SoundSynth.kt` is pure: the app plays and the JVM test renders the same samples. Source level near full scale, slider
33 / 66 / 100 % (default 100 %), mute key separate. `testDebugUnitTest` writes `app/build/loudness/loudness.wav` (16-bit PCM,
the sounding section at the default gain): copy it to the factory's `apps/traffic-signals/qa/loudness-<version>.wav`.

## Platform facts found here

- `Entity.load("asset://officer.glb")` on Dispatchers.IO, then `findSkinnedMeshEntity()` + `getAnimationResources()`
  (names = glTF animation names); `playAnimation(res, AnimationPlayConfig(fade))`, `setTime` + `pause` freeze a step.
- Mesh / texture / material resources are created on Dispatchers.IO (main-thread creation costs about a frame each).
- An `OpacityControllerComponent` is removed when the fade reaches 1 (a mounted one blends even at 1.0).
- `findSkinnedMeshEntity` returns nothing while the entity's parent is disabled.
- Stage attachment panels are 0.0008 m/dp; the Planar window reports the same through LocalPhysicalLengthConverter.

## UI rules

All 2D UI is SpatialUI inside `PicoTheme`. No Material / Material3 (gate: `../../spatial-app-factory/bin/material-grep.txt`).
Icons are Material Symbols Rounded vector drawables (Apache-2.0), every icon button carries a text label.
