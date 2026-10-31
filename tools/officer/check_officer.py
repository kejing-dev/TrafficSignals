"""Frame check of the exported runtime GLB (not of the bake session): imports app/src/main/assets/officer.glb, plays each
clip to every step's freeze time from officer_clips.json, measures the arms against the design text and renders a
front and a side view.

  blender -b --factory-startup -P tools/officer/check_officer.py -- <officer.glb> <clips.json> <out_dir>

Writes <out_dir>/<gesture>_<k>_{front,side}.png and <out_dir>/measured.json.
"""
import bpy, math, os, sys, json
from mathutils import Vector

GLB, CLIPS, OUT = sys.argv[sys.argv.index("--") + 1:][:3]
os.makedirs(OUT, exist_ok=True)
clips = json.load(open(CLIPS))
fps = clips["fps"]
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.fps = fps
bpy.ops.import_scene.gltf(filepath=GLB)
arm = next(o for o in scene.objects if o.type == "ARMATURE")
body = next(o for o in scene.objects if o.type == "MESH")
B = lambda s: "mixamorig:" + s
pb = arm.pose.bones
P = lambda n: arm.matrix_world @ pb[n].head
T = lambda n: arm.matrix_world @ pb[n].tail
UP = Vector((0, 0, 1))
FRONT = Vector((0, -1, 0))  # glTF +Z
LEFT = Vector((1, 0, 0))    # his left when he faces -Y (Blender)

def palm(S, side):
    a = P(B(S + "HandMiddle1")) - P(B(S + "Hand"))
    b = P(B(S + "HandIndex1")) - P(B(S + "HandPinky1"))
    return (a.cross(b) if side == "l" else b.cross(a)).normalized()

def measure():
    bpy.context.view_layer.update()
    out = {}
    loc = lambda v: [round(v.dot(LEFT), 2), round(v.dot(UP), 2), round(v.dot(FRONT), 2)]
    for side, S in (("l", "Left"), ("r", "Right")):
        a, fa, h, tip = P(B(S + "Arm")), P(B(S + "ForeArm")), P(B(S + "Hand")), T(B(S + "HandMiddle3"))
        upper, fore, hand = (fa - a).normalized(), (h - fa).normalized(), (tip - h).normalized()
        out[side] = {"upperToDown": round(math.degrees(upper.angle(-UP))), "elbowDeg": round(math.degrees(upper.angle(fore))),
                     "wristDeg": round(math.degrees(fore.angle(hand))), "upper": loc(upper), "hand": loc(hand), "palm": loc(palm(S, side))}
    hb = B("Head")
    hm = arm.matrix_world.to_3x3() @ pb[hb].matrix.to_3x3() @ arm.data.bones[hb].matrix_local.to_3x3().inverted() @ arm.matrix_world.to_3x3().inverted()
    face = hm @ FRONT
    out["headYawDeg"] = round(math.degrees(math.atan2(face.dot(LEFT), face.dot(FRONT))))
    return out

for e in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
    try:
        scene.render.engine = e; break
    except TypeError:
        pass
world = bpy.data.worlds.new("W"); scene.world = world
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.55, 0.6, 0.68, 1)
bpy.ops.object.light_add(type="SUN"); sun = bpy.context.object; sun.data.energy = 3.5
sun.rotation_euler = (-(FRONT + LEFT * 0.6 + UP * 0.9)).to_track_quat("-Z", "Y").to_euler()
bpy.ops.object.camera_add(); cam = bpy.context.object; scene.camera = cam
cam.data.angle = math.radians(30)
scene.render.resolution_x, scene.render.resolution_y = 360, 480
tgt = Vector((0, 0, 1.05))

if arm.animation_data is None:
    arm.animation_data_create()
for t in list(arm.animation_data.nla_tracks):
    arm.animation_data.nla_tracks.remove(t)
acts = {a.name.split("_")[0] if a.name not in clips["clips"] else a.name: a for a in bpy.data.actions}
print("ACTIONS", list(acts))
measured = {}
for name in clips["order"]:
    act = bpy.data.actions.get(name) or next((a for a in bpy.data.actions if a.name.startswith(name + "_") or a.name.startswith(name)), None)
    if act is None:
        print("MISSING", name); continue
    arm.animation_data.action = act
    try:
        if act.slots:
            arm.animation_data.action_slot = act.slots[0]
    except Exception:
        pass
    steps = clips["clips"][name]["steps"]
    measured[name] = []
    for k, t in enumerate(steps):
        scene.frame_set(1 + round(t * fps))
        measured[name].append(measure())
        for view, d in (("front", FRONT + LEFT * 0.2), ("side", -LEFT + FRONT * 0.25)):
            loc = tgt + d.normalized() * 5.0; loc.z = 1.35
            cam.location = loc
            cam.rotation_euler = (tgt - loc).to_track_quat("-Z", "Y").to_euler()
            scene.render.filepath = os.path.join(OUT, "%s_%d_%s.png" % (name, k + 1, view))
            bpy.ops.render.render(write_still=True)
json.dump(measured, open(os.path.join(OUT, "measured.json"), "w"), indent=1)
print("CHECKED", {k: len(v) for k, v in measured.items()})
