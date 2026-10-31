"""Bakes the Tripo officer (Auto Rig, Mixamo skeleton) into the runtime GLB: calibrated (feet centre at the origin,
face towards glTF +Z, 1.75 m tall) with one animation clip per gesture (design-package gestures[], 43 steps) plus a
standing clip. The arm solver is the one of the design station's render_tripo.py (same u/f/h/n vectors).

  blender -b --factory-startup -P tools/officer/bake_officer.py -- <tripo.glb> <out.glb> <clips.json> [render_dir]

clips.json: per gesture its duration and, per step, the time the pose is reached (freeze point) - the app reads it from
assets. With render_dir, every step is rendered front and side (frame-check) and the arm angles are measured.
"""
import bpy, math, os, sys, json
from mathutils import Vector, Matrix, Quaternion

ARGS = sys.argv[sys.argv.index("--") + 1:]
GLB, OUT_GLB, OUT_JSON = ARGS[:3]
RENDER = ARGS[3] if len(ARGS) > 3 else None
HEIGHT = 1.75
FPS = 30
MOVE, HOLD, LEAD, TAIL, STOP_HOLD = 0.60, 0.25, 0.40, 0.60, 1.50

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.fps = FPS
bpy.ops.import_scene.gltf(filepath=GLB)
for o in list(scene.objects):
    if o.name.startswith("Icosphere"):
        bpy.data.objects.remove(o)
arm = next(o for o in scene.objects if o.type == "ARMATURE")
body = next(o for o in scene.objects if o.type == "MESH")
root = arm
while root.parent:
    root = root.parent
bpy.context.view_layer.update()

B = lambda s: "mixamorig:" + s
W = lambda b: arm.matrix_world @ arm.data.bones[b].head_local

# ---------- calibration (profile D25): face -> Blender -Y (= glTF +Z), feet centre -> origin, height 1.75 m
LEFT0 = W(B("LeftShoulder")) - W(B("RightShoulder")); LEFT0.z = 0; LEFT0.normalize()
FRONT0 = LEFT0.cross(Vector((0, 0, 1))).normalized()
yaw = math.atan2(FRONT0.x, -FRONT0.y)  # rotate FRONT0 onto (0,-1,0)
root.rotation_mode = "QUATERNION"
root.rotation_quaternion = Quaternion((0, 0, 1), -yaw) @ root.rotation_quaternion
bpy.context.view_layer.update()
pts = [body.matrix_world @ v.co for v in body.data.vertices]
zmin = min(p.z for p in pts); zmax = max(p.z for p in pts)
k = HEIGHT / (zmax - zmin)
root.scale = root.scale * k
bpy.context.view_layer.update()
pts = [body.matrix_world @ v.co for v in body.data.vertices]
zmin = min(p.z for p in pts)
lf, rf = W(B("LeftFoot")), W(B("RightFoot"))
feet = (lf + rf) / 2
root.location = root.location - Vector((feet.x, feet.y, zmin))
bpy.context.view_layer.update()
pts = [body.matrix_world @ v.co for v in body.data.vertices]
CAL = {
    "heightM": max(p.z for p in pts) - min(p.z for p in pts),
    "feetCentre": list((W(B("LeftFoot")) + W(B("RightFoot"))) / 2),
    "scale": k,
    "yawDeg": math.degrees(yaw),
}

UP = Vector((0, 0, 1))
LEFT = W(B("LeftShoulder")) - W(B("RightShoulder")); LEFT.z = 0; LEFT.normalize()
FRONT = LEFT.cross(UP).normalized()
CAL["front"] = list(FRONT)
print("CAL", CAL)

def bodyv(x, y, z):
    return (x * LEFT + y * UP + z * FRONT).normalized()

pbones = arm.pose.bones
for p in pbones:
    p.rotation_mode = "QUATERNION"
REST = {b.name: (arm.matrix_world @ b.matrix_local).copy() for b in arm.data.bones}
SIDE = {"l": "Left", "r": "Right"}

def palm_local(side):
    S = SIDE[side]
    a = W(B(S + "HandMiddle1")) - W(B(S + "Hand"))
    b = W(B(S + "HandIndex1")) - W(B(S + "HandPinky1"))
    n = a.cross(b) if side == "l" else b.cross(a)
    return (REST[B(S + "Hand")].to_3x3().inverted() @ n).normalized()
PALM = {s: palm_local(s) for s in "lr"}

def cur(n):
    bpy.context.view_layer.update()
    return arm.matrix_world @ pbones[n].matrix
def setm(n, M):
    pbones[n].matrix = arm.matrix_world.inverted() @ M
    bpy.context.view_layer.update()
def rot_about(n, R):
    M = cur(n); t = M.translation.copy()
    N = (R @ M.to_3x3()).to_4x4(); N.translation = t
    setm(n, N)
def aim(n, d):
    y = cur(n).to_3x3().col[1].normalized()
    rot_about(n, y.rotation_difference(d).to_matrix())
def twist(n, ang):
    y = cur(n).to_3x3().col[1].normalized()
    rot_about(n, Matrix.Rotation(ang, 3, y))
def reset():
    for p in pbones:
        p.matrix_basis = Matrix.Identity(4)
    bpy.context.view_layer.update()

def arm_pose(side, u, f, h, n):
    S = SIDE[side]
    up_, lo_, ha_ = B(S + "Arm"), B(S + "ForeArm"), B(S + "Hand")
    u, f, h, n = (bodyv(*x) for x in (u, f, h, n))
    aim(up_, u); aim(lo_, f); aim(ha_, h)
    for _ in range(8):
        hm = cur(ha_).to_3x3()
        pn = (hm @ PALM[side]).normalized()
        hy = hm.col[1].normalized()
        want = (n - hy * n.dot(hy)).normalized()
        have = (pn - hy * pn.dot(hy)).normalized()
        ang = have.angle(want)
        if ang < 0.01:
            break
        if have.cross(want).dot(hy) < 0:
            ang = -ang
        if u.dot(f) > 0.95:
            twist(up_, ang * 0.5); twist(lo_, ang * 0.5)
        else:
            twist(lo_, ang)
        aim(lo_, f); aim(ha_, h)
    hy = cur(ha_).to_3x3().col[1].normalized()
    for fn in ("Index", "Middle", "Ring", "Pinky"):  # fingers together and straight
        for i in (1, 2, 3):
            aim(B("%sHand%s%d" % (S, fn, i)), hy)

def neck(deg):
    sign = 1 if UP.cross(FRONT).dot(LEFT) > 0 else -1
    R = Matrix.Rotation(sign * math.radians(deg) * 0.5, 3, UP)
    rot_about(B("Neck"), R); rot_about(B("Head"), R)

# ---------- gesture table (design-package gestures.list[].steps[].pose; x = his left, y = up, z = his front)
D, U, F, L, R = (0, -1, 0), (0, 1, 0), (0, 0, 1), (1, 0, 0), (-1, 0, 0)
BACK = (0, 0, -1)
REST_L = (D, D, D, R)
REST_R = (D, D, D, L)
def st(l=None, r=None, nk=None):
    return {"l": l, "r": r, "neck": nk}
RESTSTEP = st(REST_L, REST_R, 0)
up135 = (0, .71, .71)
GESTURES = [
    ("stop", [st(l=(up135, up135, U, F), nk=0), RESTSTEP]),
    ("straight", [
        st(l=(L, L, L, F), nk=45),
        st(r=(R, R, R, F), nk=-45),
        st(r=(F, L, L, BACK), nk=45),
        st(r=(F, F, F, L)),
        st(r=(F, L, L, BACK)),
        st(r=REST_R),
        RESTSTEP]),
    ("left", [
        st(r=(F, F, U, F), nk=45),
        st(l=((-.38, -.71, .59),) * 3 + (R,)),
        st(l=REST_L),
        st(l=((-.38, -.71, .59),) * 3 + (R,)),
        st(l=REST_L),
        RESTSTEP]),
    ("leftWait", [
        st(l=((.71, -.71, 0),) * 3 + (D,), nk=45),
        st(l=((.26, -.97, 0),) * 3 + (D,)),
        st(l=((.71, -.71, 0),) * 3 + (D,)),
        st(l=((.26, -.97, 0),) * 3 + (D,)),
        RESTSTEP]),
    ("right", [
        st(l=(F, F, U, F), nk=-45),
        st(r=((.38, -.71, .59),) * 3 + (L,)),
        st(r=REST_R),
        st(r=((.38, -.71, .59),) * 3 + (L,)),
        st(r=REST_R),
        RESTSTEP]),
    ("laneChange", [
        st(r=(F, F, F, L), nk=0),
        st(r=((.71, 0, .71),) * 3 + ((.71, 0, -.71),)),
        st(r=(F, F, F, L)),
        st(r=((.71, 0, .71),) * 3 + ((.71, 0, -.71),)),
        RESTSTEP]),
    ("slow", [
        st(r=((-.71, 0, .71),) * 3 + (D,), nk=-45),
        st(r=((-.5, -.71, .5),) * 3 + (D,)),
        st(r=((-.71, 0, .71),) * 3 + (D,)),
        st(r=((-.5, -.71, .5),) * 3 + (D,)),
        RESTSTEP]),
    ("pullOver", [
        st(r=((0, -.71, .71),) * 3 + (L,), nk=0),
        st(l=(up135, up135, U, F)),
        st(r=((.5, -.71, .5),) * 3 + (L,)),
        st(r=((0, -.71, .71),) * 3 + (L,)),
        st(r=((.5, -.71, .5),) * 3 + (L,)),
        st(r=((0, -.71, .71),) * 3 + (L,)),
        RESTSTEP]),
]
assert sum(len(s) for _, s in GESTURES) == 43, sum(len(s) for _, s in GESTURES)

state = {"l": REST_L, "r": REST_R, "neck": 0}
def apply(s):
    for key in ("l", "r", "neck"):
        if s.get(key) is not None:
            state[key] = s[key]
    reset()
    arm_pose("l", *state["l"]); arm_pose("r", *state["r"]); neck(state["neck"])

last_q = {}
def key_all(frame):
    for p in pbones:
        q = p.rotation_quaternion.copy()
        prev = last_q.get(p.name)
        if prev is not None and prev.dot(q) < 0:
            q.negate()
        p.rotation_quaternion = q
        last_q[p.name] = q.copy()
        p.keyframe_insert("rotation_quaternion", frame=frame)
        p.keyframe_insert("location", frame=frame)

def new_action(name):
    act = bpy.data.actions.new(name)
    if arm.animation_data is None:
        arm.animation_data_create()
    arm.animation_data.action = act
    last_q.clear()
    return act

clips = {}
actions = []
def bake(name, steps):
    state.update({"l": REST_L, "r": REST_R, "neck": 0})
    act = new_action(name)
    t = 0.0
    apply(RESTSTEP); key_all(1)
    t = LEAD; key_all(1 + round(t * FPS))
    reached = []
    poses = []
    for i, s in enumerate(steps):
        t += MOVE
        apply(s); key_all(1 + round(t * FPS))
        reached.append(round(t, 3))
        poses.append(measure())
        t += STOP_HOLD if (name == "stop" and i == 0) else HOLD
        key_all(1 + round(t * FPS))
    t += TAIL - HOLD
    key_all(1 + round(t * FPS))
    for fc in getattr(act, "fcurves", []) or []:
        for kp in fc.keyframe_points:
            kp.interpolation = "BEZIER"
    clips[name] = {"duration": round(t, 3), "steps": reached, "measured": poses}
    actions.append(act)
    if arm.animation_data.nla_tracks is not None:
        tr = arm.animation_data.nla_tracks.new(); tr.name = name
        tr.strips.new(name, 1, act)
    arm.animation_data.action = None

def angle_to_down(v):
    return math.degrees(v.angle(Vector((0, 0, -1))))
def measure():
    out = {}
    for side, S in (("l", "Left"), ("r", "Right")):
        a = W2(B(S + "Arm")); fa = W2(B(S + "ForeArm")); h = W2(B(S + "Hand")); tip = arm.matrix_world @ pbones[B(S + "HandMiddle3")].tail
        upper = (fa - a).normalized(); fore = (h - fa).normalized(); hand = (tip - h).normalized()
        loc = lambda v: [round(v.dot(LEFT), 2), round(v.dot(UP), 2), round(v.dot(FRONT), 2)]
        out[side] = {"upperToDown": round(angle_to_down(upper)), "elbowDeg": round(math.degrees(upper.angle(fore))),
                     "wristDeg": round(math.degrees(fore.angle(hand))), "upper": loc(upper), "fore": loc(fore), "hand": loc(hand)}
        palm = (cur(B(S + "Hand")).to_3x3() @ PALM[side]).normalized()
        out[side]["palm"] = loc(palm)
    hf = cur(B("Head")).to_3x3() @ REST[B("Head")].to_3x3().inverted() @ FRONT
    hf.z = 0
    out["headYawDeg"] = round(math.degrees(math.atan2(hf.dot(LEFT), hf.dot(FRONT)))) if hf.length > 1e-3 else 0
    return out
def W2(n):
    return cur(n).translation

# standing clip (1 s) first so it is animation 0
state.update({"l": REST_L, "r": REST_R, "neck": 0})
act = new_action("stand"); apply(RESTSTEP); key_all(1); key_all(1 + FPS)
clips["stand"] = {"duration": 1.0, "steps": [], "measured": []}
actions.append(act)
tr = arm.animation_data.nla_tracks.new(); tr.name = "stand"; tr.strips.new("stand", 1, act)
arm.animation_data.action = None
for name, steps in GESTURES:
    bake(name, steps)

# ---------- optional frame-check renders (front / side) of every step
if RENDER:
    os.makedirs(RENDER, exist_ok=True)
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
    tgt = Vector((0, 0, 1.0))
    for name, steps in GESTURES:
        state.update({"l": REST_L, "r": REST_R, "neck": 0})
        for i, s in enumerate(steps):
            apply(s)
            for view, d in (("front", FRONT + LEFT * 0.2), ("side", -LEFT * 1.0 + FRONT * 0.25)):
                loc = tgt + d.normalized() * 5.2; loc.z = 1.3
                cam.location = loc
                cam.rotation_euler = (tgt - loc).to_track_quat("-Z", "Y").to_euler()
                scene.render.filepath = os.path.join(RENDER, "%s_%d_%s.png" % (name, i + 1, view))
                bpy.ops.render.render(write_still=True)
    reset()

reset()
for p in pbones:
    p.matrix_basis = Matrix.Identity(4)
bpy.ops.object.select_all(action="DESELECT")
for o in (root, arm, body):
    o.select_set(True)
kw = dict(filepath=OUT_GLB, export_format="GLB", use_selection=True, export_animations=True, export_apply=False,
          export_yup=True, export_skins=True, export_morph=False)
for mode in ("NLA_TRACKS", "ACTIONS"):
    try:
        bpy.ops.export_scene.gltf(**kw, export_animation_mode=mode)
        print("EXPORT mode", mode)
        break
    except TypeError as e:
        print("EXPORT retry", e)
names = ["stand"] + [n for n, _ in GESTURES]
json.dump({"fps": FPS, "order": names, "calibration": CAL, "clips": clips,
           "timing": {"move": MOVE, "hold": HOLD, "lead": LEAD, "tail": TAIL, "stopHold": STOP_HOLD}},
          open(OUT_JSON, "w"), indent=1)
print("BAKED", names)
