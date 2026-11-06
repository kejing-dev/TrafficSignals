package io.embodit.trafficsignals.ui.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.StageEnvironmentLightingComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import io.embodit.trafficsignals.domain.Answer
import io.embodit.trafficsignals.domain.Compass
import io.embodit.trafficsignals.domain.Content
import io.embodit.trafficsignals.domain.Facing
import io.embodit.trafficsignals.domain.Gesture
import io.embodit.trafficsignals.domain.HeadPose
import io.embodit.trafficsignals.domain.Judge
import io.embodit.trafficsignals.domain.Lane
import io.embodit.trafficsignals.domain.Move
import io.embodit.trafficsignals.domain.Quat4
import io.embodit.trafficsignals.domain.SeatPlacer
import io.embodit.trafficsignals.domain.StageLayout
import io.embodit.trafficsignals.domain.Question
import io.embodit.trafficsignals.domain.Upright
import io.embodit.trafficsignals.domain.Vec3
import io.embodit.trafficsignals.domain.Yaw
import io.embodit.trafficsignals.platform.HeadTracker
import io.embodit.trafficsignals.platform.Stick
import io.embodit.trafficsignals.platform.TsLog
import io.embodit.trafficsignals.ui.Dialog
import io.embodit.trafficsignals.ui.Phase
import io.embodit.trafficsignals.ui.Screen
import io.embodit.trafficsignals.ui.TsEvent
import io.embodit.trafficsignals.ui.TsUiState
import io.embodit.trafficsignals.ui.panels.ChipSize
import io.embodit.trafficsignals.ui.window.WindowCtl
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The scene of the open Stage (debug hooks and the frame system reach it here). Main thread only. */
internal object ActiveScene {
    var current: TrafficScene? = null
}

/** The one per-frame ECS system of the app (registered in TsScreen). */
class TrafficFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.current?.onFrame(context)
    }
}

/** The only panel left on the Stage: "打开面板" / "平视前方", shown only while the system window is not there. */
object PanelIds {
    const val OPEN = "OPEN"
    val pool = listOf(OPEN)
}

/**
 * The 3D side (design scene, entities3d): sky, the painted crossroads with its props, parked cars, your bonnet, the
 * officer on the centre post (or on the lesson post 6 m ahead), the flow arrows of a verdict and the orientation aids.
 * Everything hangs below a driver root placed from the head (position + yaw only, [SeatPlacer], [StageLayout]); [回正] places it again.
 * Per-frame work happens in [onFrame] only.
 */
class TrafficScene(private val context: Context, private val sink: (TsEvent) -> Unit) {
    val panelShown = mutableStateMapOf<String, Boolean>()
    val bindTick = mutableIntStateOf(0)

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private val head = HeadTracker()
    private val stick = Stick()
    private var stageRoot: Entity? = null
    private var driver: Entity? = null
    private var inter: Entity? = null
    private var officerRoot: Entity? = null
    private var officer: Officer? = null
    private var rig: PanelRig? = null
    private var shared: SharedResources? = null
    private var buildJob: Job? = null
    private var attached = false
    private var built = false
    private var paused = false
    private var ui = TsUiState()
    private val attachMs = SystemClock.elapsedRealtime()

    // materials / meshes kept for per-question rebuilds
    private var mats: Mats? = null
    private var goMesh: MeshResource? = null
    private var waitMesh: MeshResource? = null
    private var stopMesh: MeshResource? = null
    private var routeMesh: MeshResource? = null
    private var markMesh: MeshResource? = null
    private var goEntity: ModelEntity? = null
    private var waitEntity: ModelEntity? = null
    private var stopEntity: ModelEntity? = null
    private var routeEntity: ModelEntity? = null
    private var markEntity: ModelEntity? = null
    private var lessonPost: Entity? = null
    private var hoodEntity: Entity? = null
    private val carMeshes = HashMap<Int, MeshResource>()
    private val cars = ArrayList<Car>()
    private var ghost: ModelEntity? = null
    private var aidsRoot: Entity? = null
    private val aidWords = ArrayList<ModelEntity>()
    private val armTags = ArrayList<ModelEntity>()
    private var aidsLang: Boolean? = null
    private var sky: Entity? = null

    // placement (design container.placement, profile D1 / D9)
    private var pose: HeadPose? = null
    private var placeEpoch = -1
    private val placer = SeatPlacer()
    private var placed = false
    private var placedAtMs = 0L
    private var origin = Vec3.ZERO
    private var yaw0 = 0f
    private var windowEpoch = 0
    private val logAt = longArrayOf(1_000L, 3_000L, 5_000L)
    private var logIndex = 0

    // lights (B24)
    private var envLight: StageEnvironmentLightingComponent? = null
    private var sunLight: DirectionalLightComponent? = null

    // your controller rays (D41: drawn by the app, the system's is not guaranteed)
    private val rays = ArrayList<ModelEntity>()
    var debugRay: Pair<Vec3, Vec3>? = null

    /** Debug: fake head pitch (degrees) for the level gate (the 6.1 emulator's head can't be pitched from a script). */
    var debugPitch: Float? = null

    // "打开面板" / "平视前方"
    private var chipAt: Vec3? = null
    private var hintShown = false

    // lane / fade
    private var shownLane = Lane.STRAIGHT
    private var shownBig = false
    private var shownNear = false
    private var officerHeld = false
    private var fadeDir = 0
    private var fadeT = 0f
    private var pendingLane: Lane? = null

    // officer motion
    private var officerYaw = 0f
    private var officerYawFrom = 0f
    private var officerYawTo = 0f
    private var turnT = 1f
    private var stickYaw = 0f
    private var stickVel = 0f
    private var gestureEpoch = -1
    private var pendingGesture: String? = null
    private var doneSent = false
    private var loopRestartAt = 0L
    private var lastFrameMs = 0L
    private var lastLogMs = 0L
    private var frames = 0L
    private var lastLessonKey = ""
    private var freezeFrames = 0
    private var flowsFor = ""
    private var flowStartMs = 0L
    private var questionKey = ""
    private var blockedSinceMs = 0L

    /** Debug: a fixed head yaw (degrees) used by `recenter` tests in place of the real head. */
    var debugYaw: Float? = null

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (attached || stageRoot != null) detach()
        this.scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val root = Entity().named("stageRoot")
        content.addEntity(root)
        // an entity added to the SpatialView is not at the Stage origin (Spatial Ruler): move the root once
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        stageRoot = root
        val rig = PanelRig(root) { id, shown -> panelShown[id] = shown }
        this.rig = rig
        rig.world(PanelIds.OPEN, ChipSize.W, ChipSize.H, 1f).maxPitch = 0f
        head.start()
        stick.start()
        ActiveScene.current = this
        attached = true
        bindPanels(attachments)
        buildJob =
            scope?.launch {
                try {
                    build(root)
                } catch (t: Throwable) {
                    if (t !is CancellationException) TsLog.e("SCENE build failed: $t", t)
                    throw t
                }
            }
        TsLog.i("SCENE attached")
    }

    private suspend fun frame() = delay(16)

    /** Builds in steps (design lifecycle: road -> officer -> cars -> panels); resources on IO, entities on main. */
    private suspend fun build(root: Entity) {
        val t0 = SystemClock.elapsedRealtime()
        val sh = SharedResources()
        shared = sh
        val m = withContext(Dispatchers.IO) { Mats(sh) }
        mats = m
        val driver = Entity().named("driver")
        root.addChild(driver)
        this.driver = driver
        // invisible until the first place. It stays enabled: findSkinnedMeshEntity finds nothing below a disabled parent.
        driver.setOpacity(0f)
        val inter = Entity().named("intersection")
        driver.addChild(inter)
        this.inter = inter
        frame()
        // sky dome (around the driver, inside-out sphere)
        val skyMesh = withContext(Dispatchers.IO) { sh.keep(MeshResource.createSphere(SKY_R)) } // gate:allow geometry 天空穹顶：包住人四周的内翻球，只贴天空渐变，不是可见物件
        sky = model(skyMesh, m.sky, "sky", root).also { it.setLocal(scale = Vec3(-1f, 1f, 1f)) }
        frame()
        // road
        val groundMesh = withContext(Dispatchers.IO) { sh.keep(Street.groundMesh()) }
        model(groundMesh, m.ground, "ground", inter)
        frame()
        val outer = withContext(Dispatchers.IO) { sh.keep(Toon(64).also { Street.outerGround(it) }.build("outer")) }
        model(outer, m.toon, "outer", inter)
        frame()
        val props = withContext(Dispatchers.IO) { sh.keep(Toon(20_000).also { Street.props(it) }.build("props")) }
        model(props, m.toon, "props", inter)
        frame()
        // lesson post 6 m ahead, your bonnet
        val lp = withContext(Dispatchers.IO) { sh.keep(Toon(400).also { Street.lessonPost(it) }.build("lessonPost")) }
        lessonPost = model(lp, m.toon, "lessonPost", driver).also {
            it.setLocal(position = StageLayout.lessonSpot(Lane.STRAIGHT.x))
            it.enabled = false
        }
        val hoodColor = Palette.HOODS[Random().nextInt(Palette.HOODS.size)]
        val hood = withContext(Dispatchers.IO) { sh.keep(MeshBuilder(1500).also { CarKit.ownCar(it, hoodColor) }.build("ownCar")) }
        hoodEntity = model(hood, m.paint, "ownCar", driver)
        val cockpit = withContext(Dispatchers.IO) { sh.keep(MeshBuilder(300).also { CarKit.ownCockpit(it) }.build("ownCockpit")) }
        model(cockpit, m.toon, "ownCockpit", driver)
        frame()
        // light for the officer (B24): PBR under a soft city sky (IBL, tools/make_ibl.py) and one warm sun, no fill and no
        // emission. The street is Unlit with the sun baked into its palette. v0.1.1's 5000 + 2200 lux over the default
        // environment light burnt his shirt white (2026-11-05 headset: "交警身上光好像太白了").
        val iblTex = runCatching { withContext(Dispatchers.IO) { TextureResource.load(IBL_ASSET, LoadType.FROM_ASSETS) } }
            .onFailure { TsLog.e("LIGHT ibl $IBL_ASSET failed: $it") }.getOrNull()
        if (iblTex != null) {
            sh.keep(iblTex)
            val env = StageEnvironmentLightingComponent(ImageBasedLightSource.Single(iblTex), IBL_EXP)
            root.addChild(Entity().named("cityIbl").also { it.components.set(env) })
            envLight = env
        }
        val sun = Entity().named("sun")
        val sunC = DirectionalLightComponent(Color4(1f, 0.93f, 0.82f, 1f), SUN_LUX, false)
        sun.components.set(sunC)
        sunLight = sunC
        driver.addChild(sun)
        sun.setLocal(rotation = Quat4.fromTo(Vec3(0f, 0f, -1f), -Palette.SUN))
        TsLog.i(lightLine())
        // officer
        val oRoot = Entity().named("officerRoot")
        driver.addChild(oRoot)
        officerRoot = oRoot
        oRoot.setLocal(position = StageLayout.openingFeet()) // on the post from the first frame he could ever be seen in (D14)
        val clips = OfficerClips.load(context)
        val model = withContext(Dispatchers.IO) { Entity.load("asset://officer.glb") }
        oRoot.addChild(model)
        contactShadow()
        val off = Officer(model, clips)
        off.bind()
        officer = off
        TsLog.i("OFFICER loaded heightM=${clips.heightM} opacityComponent=${model.hasOpacityComponent()}")
        frame()
        // cars: one mesh per colour (shared by the pool), plus the ghost
        withContext(Dispatchers.IO) {
            for (c in Palette.CARS) carMeshes[c] = sh.keep(MeshBuilder(1500).also { CarKit.car(it, c) }.build("car$c"))
        }
        repeat(CAR_POOL) { i ->
            val e = model(carMeshes[Palette.CARS[i % Palette.CARS.size]]!!, m.paint, "car$i", inter)
            e.enabled = false
            cars += Car(e)
        }
        ghost = model(carMeshes[Palette.CAR_WHITE]!!, m.ghost, "ghost", inter).also { it.enabled = false }
        frame()
        // flows (rebuilt per verdict)
        withContext(Dispatchers.IO) {
            goMesh = sh.keep(MeshBuilder().build("flowGo"))
            waitMesh = sh.keep(MeshBuilder().build("flowWait"))
            stopMesh = sh.keep(MeshBuilder().build("flowStop"))
            routeMesh = sh.keep(MeshBuilder().build("route"))
            markMesh = sh.keep(MeshBuilder().build("routeMark"))
        }
        goEntity = model(goMesh!!, m.flowGo, "flowGo", inter).also { it.enabled = false }
        waitEntity = model(waitMesh!!, m.flowWait, "flowWait", inter).also { it.enabled = false }
        stopEntity = model(stopMesh!!, m.flowStop, "flowStop", inter).also { it.enabled = false }
        routeEntity = model(routeMesh!!, m.route, "route", inter).also { it.enabled = false }
        markEntity = model(markMesh!!, m.routeMark, "routeMark", inter).also { it.enabled = false }
        // orientation aids (lesson 0 and the hint): four ground words around the post, two arm tags
        val aids = Entity().named("aids")
        driver.addChild(aids)
        aids.enabled = false
        aidsRoot = aids
        val wordMesh = withContext(Dispatchers.IO) { sh.keep(uprightQuad(2.0f, WORD_H, "aidWord")) }
        repeat(4) { aidWords += model(wordMesh, m.newTextMaterial(), "aidWord$it", aids) }
        val tagMesh = withContext(Dispatchers.IO) { sh.keep(uprightQuad(1.1f, ARM_TAG_H, "armTag")) }
        repeat(2) { armTags += model(tagMesh, m.newTextMaterial(), "armTag$it", driver).also { e -> e.enabled = false } }
        // the rays: a fading line from each controller, Stage space (not below the driver)
        val rayMesh = withContext(Dispatchers.IO) { sh.keep(rayMesh()) }
        val rayMat = withContext(Dispatchers.IO) { m.rayMaterial() }
        repeat(2) { rays += model(rayMesh, rayMat, "ray$it", root).also { e -> e.enabled = false } }
        built = true
        TsLog.i("SCENE built ms=${SystemClock.elapsedRealtime() - t0}")
        render(ui)
    }

    /** Contact shadow under the officer's feet: a soft dark blob on the post top (the street is Unlit, it receives nothing). */
    private fun contactShadow() {
        val mats = mats ?: return
        val mesh = shared?.keep(flatQuad(1f, 1f, "contact")) ?: return
        val blob = model(mesh, mats.contactMaterial(), "contact", officerRoot ?: return)
        blob.setLocal(position = Vec3(0f, 0.006f, 0f), scale = Vec3(0.95f, 1f, 0.95f))
    }

    fun pause() {
        if (paused || !attached) return
        paused = true
        head.stop()
        stick.stop()
        officer?.pause()
        TsLog.i("SCENE paused")
    }

    fun resume() {
        if (!paused || !attached) return
        paused = false
        head.start()
        stick.start()
        lastFrameMs = 0L
        TsLog.i("SCENE resumed")
    }

    fun bindPanels(attachments: SpatialViewAttachments) {
        val rig = rig ?: return
        if (!rig.bind(attachments, PanelIds.pool, SystemClock.elapsedRealtime())) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, 250L)
        }
    }

    private val rebind = Runnable { if (stageRoot != null) bindTick.intValue++ }

    fun detach() {
        if (!attached && stageRoot == null) return
        attached = false
        if (ActiveScene.current === this) ActiveScene.current = null
        main.removeCallbacksAndMessages(null)
        buildJob?.cancel()
        scope?.cancel()
        scope = null
        head.stop()
        stick.stop()
        officer?.release()
        officer = null
        panelShown.clear()
        cars.clear()
        rays.clear()
        chipAt = null
        hintShown = false
        aidWords.clear()
        armTags.clear()
        runCatching { rig?.release() }
        runCatching { stageRoot?.destroy(true) }
        mats?.close()
        shared?.close()
        shared = null
        mats = null
        stageRoot = null
        driver = null
        inter = null
        rig = null
        built = false
        placed = false
        TsLog.i("SCENE detached")
    }

    // =========================================================================================
    // state -> entities
    // =========================================================================================

    fun render(state: TsUiState) {
        val before = ui
        ui = state
        if (!built) return
        if (state.breakdown) ghost?.enabled = false // the near post stands on its route
        if (state.zh != aidsLang) paintAids(state.zh)
        // a new question: fresh cars, flows hidden, your route in interaction blue
        val q = state.question
        val key = if (state.screen == Screen.QUIZ && q != null) "${q.key}#${state.index}" else ""
        if (key != questionKey) {
            questionKey = key
            layoutCars(q, state.lane)
            flowsFor = ""
        }
        if (state.lane != before.lane && state.screen == Screen.QUIZ) layoutCars(q, state.lane)
        if (state.screen != Screen.QUIZ && state.screen != Screen.INTRO && before.screen == Screen.QUIZ) layoutCars(null, Lane.STRAIGHT)
        renderFlows(state)
        officerRoot?.setLocal(scale = if (state.big) Vec3(1.5f, 1.5f, 1.5f) else Vec3(1f, 1f, 1f))
    }

    // ---------------------------------------------------------------- cars

    private class Car(val e: ModelEntity) {
        var approach = Compass.NORTH
        var laneX = 4.5f
        var d = 15f
        var path: Street.Bezier? = null
        var stopAt = 1f
        var moving = false
        var opacity = 1f
    }

    private fun layoutCars(q: Question?, lane: Lane) {
        if (cars.isEmpty()) return
        ghost?.enabled = false
        val rnd = Random((q?.key ?: "home").hashCode().toLong())
        var i = 0
        fun place(c: Compass, laneX: Float, d: Float) {
            if (i >= cars.size) return
            val car = cars[i++]
            car.approach = c
            car.laneX = laneX
            car.d = d
            car.path = null
            car.moving = false
            car.opacity = 1f
            car.e.setOpacity(1f)
            val pos = Street.entry(c, laneX, d)
            val heading = -c.vec
            car.e.setLocal(position = pos, rotation = Quat4.axisAngle(Vec3.UP, Yaw.of(heading)))
            car.e.enabled = true
            (car.e.components[ModelComponent::class.java])?.mesh = carMeshes[Palette.CARS[rnd.nextInt(Palette.CARS.size)]]!!
        }
        run {
            for (c in listOf(Compass.NORTH, Compass.EAST, Compass.WEST)) {
                val n = 1 + rnd.nextInt(3)
                val lanes = Lane.entries.shuffled(rnd).take(n)
                for (l in lanes) {
                    place(c, l.x, CAR_FIRST)
                    if (rnd.nextFloat() < 0.25f) place(c, l.x, CAR_FIRST + 6f)
                }
            }
            // a car level with you in a neighbouring lane (never in the lane you are in)
            val beside = when (lane) { Lane.LEFT -> listOf(Lane.STRAIGHT); Lane.STRAIGHT -> listOf(Lane.LEFT, Lane.RIGHT); Lane.RIGHT -> listOf(Lane.STRAIGHT) }
            if (rnd.nextFloat() < 0.6f) place(Compass.SOUTH, beside[rnd.nextInt(beside.size)].x, Street.STOP + 2.3f)
        }
        while (i < cars.size) cars[i++].e.enabled = false
    }

    // ---------------------------------------------------------------- flows (design entities3d.flowArrows, yourRoute)

    private fun renderFlows(state: TsUiState) {
        val q = state.question
        val inQuiz = state.screen == Screen.QUIZ && q != null
        if (!inQuiz) {
            listOf(goEntity, waitEntity, stopEntity, routeEntity, markEntity).forEach { it?.enabled = false }
            ghost?.enabled = false
            flowsFor = ""
            return
        }
        val judged = state.phase in setOf(Phase.CORRECT, Phase.WRONG, Phase.TIMEOUT, Phase.DEMO)
        val key = "${q!!.key}|${state.lane}|$judged|${state.index}"
        if (key == flowsFor) return
        flowsFor = key
        val m = mats ?: return
        val lane = state.lane
        val v = Judge.verdict(q.copy(lane = lane))
        val myPath = Street.path(Compass.SOUTH, lane.x, Judge.yourMove(lane))
        val tStart = myPath.nearest(Vec3(lane.x, 0f, Street.ROUTE_START_D))
        if (!judged) {
            // watching: your route in interaction blue, nothing else
            val b = MeshBuilder()
            Street.ribbon(b, myPath, tStart, 1f, 1.2f, LIFT, head = true)
            listOf(goEntity, waitEntity, stopEntity, markEntity, routeEntity).forEach { it?.enabled = false }
            ghost?.enabled = false
            swap(listOf(b to routeMesh!!)) {
                m.route.setBaseColor(UiColors.INTERACTION)
                m.route.setOpacity(0.55f)
                routeEntity?.enabled = true
            }
            return
        }
        // released flows (green / yellow) from the released approach's matching lanes
        val rel = Judge.release(q.gesture, q.facing)
        val go = MeshBuilder()
        val wait = MeshBuilder()
        if (rel != null && rel.approach != Compass.SOUTH) {
            // your own approach is shown by your route + the ghost car in the visible part of the junction (design yourRoute)
            for (mv in rel.moves) {
                val lx = when (mv) { Move.LEFT, Move.WAIT -> Lane.LEFT.x; Move.STRAIGHT -> Lane.STRAIGHT.x; Move.RIGHT -> Lane.RIGHT.x }
                val p = Street.path(rel.approach, lx, mv)
                if (mv == Move.WAIT) Street.ribbon(wait, p, 0f, WAIT_T, 1.2f, LIFT, head = true) else Street.ribbon(go, p, 0f, 0.62f, 1.2f, LIFT, head = true)
            }
        }
        val stop = MeshBuilder()
        for (c in Judge.stopped(q.gesture, q.facing)) if (c != Compass.SOUTH) Street.stopBar(stop, c, LIFT)
        // your route: the true outcome (design yourRoute)
        val route = MeshBuilder()
        val mark = MeshBuilder()
        val answer = v?.answer
        when (answer) {
            Answer.GO -> Street.ribbon(route, myPath, tStart, 1f, 1.2f, LIFT, head = true)
            Answer.WAIT -> Street.ribbon(route, myPath, tStart, WAIT_T, 1.2f, LIFT, head = true)
            Answer.STOP -> {
                Street.ribbon(route, myPath, tStart, 1f, 1.2f, LIFT, head = true)
                val s = myPath.at(tStart)
                val r = Street.rightOf(myPath.tangent(tStart)) * 1.1f
                val f = myPath.tangent(tStart) * 0.25f
                val n = Vec3.UP
                val v0 = stop.vertex(Vec3(s.x - r.x - f.x, LIFT2, s.z - r.z - f.z), n)
                val v1 = stop.vertex(Vec3(s.x + r.x - f.x, LIFT2, s.z + r.z - f.z), n)
                val v2 = stop.vertex(Vec3(s.x + r.x + f.x, LIFT2, s.z + r.z + f.z), n)
                val v3 = stop.vertex(Vec3(s.x - r.x + f.x, LIFT2, s.z - r.z + f.z), n)
                stop.quad(v0, v1, v2, v3)
                stop.quad(v3, v2, v1, v0)
            }
            Answer.SLOW -> {
                val p = Street.Bezier(Vec3(lane.x, 0f, 3f), Vec3(lane.x, 0f, -8f), Vec3(lane.x, 0f, -20f))
                Street.ribbon(route, p, 0f, 1f, 1.2f, LIFT, head = true)
                Street.chevrons(mark, lane.x, 1.5f, LIFT2)
            }
            Answer.LANE -> {
                val to = if (lane == Lane.RIGHT) lane.x - 3f else lane.x + 3f
                val p = Street.Bezier(Vec3(lane.x, 0f, 3f), Vec3(lane.x, 0f, -6f), Vec3(to, 0f, -16f))
                Street.ribbon(route, p, 0f, 1f, 1.2f, LIFT, head = true)
            }
            Answer.PULL -> {
                val p = Street.Bezier(Vec3(lane.x, 0f, 3f), Vec3(lane.x, 0f, -5f), Vec3(Street.HALF - 1.2f, 0f, -9f))
                Street.ribbon(route, p, 0f, 1f, 1.2f, LIFT, head = true)
            }
            null -> Unit
        }
        listOf(goEntity, waitEntity, stopEntity, markEntity, routeEntity).forEach { it?.enabled = false }
        // mesh refills cost about a rendered frame each: do them off the main thread (cold-emulator ANR, Rehearsal Hall)
        swap(listOf(go to goMesh!!, wait to waitMesh!!, stop to stopMesh!!, route to routeMesh!!, mark to markMesh!!)) {
            val routeColor =
                when (answer) {
                    Answer.GO -> UiColors.PASSABLE
                    Answer.WAIT -> UiColors.ALERT
                    Answer.STOP -> UiColors.INTERACTION
                    else -> UiColors.ALERT
                }
            m.route.setBaseColor(routeColor)
            goEntity?.enabled = rel != null && rel.approach != Compass.SOUTH && rel.moves.any { it != Move.WAIT }
            waitEntity?.enabled = rel != null && rel.approach != Compass.SOUTH && rel.moves.any { it == Move.WAIT }
            stopEntity?.enabled = true
            routeEntity?.enabled = true
            markEntity?.enabled = answer == Answer.SLOW
            flowStartMs = SystemClock.elapsedRealtime()
            // cars that are released drive off; the ghost car takes your route when you may go / wait
            if (rel != null) {
                for (car in cars) {
                    if (!car.e.enabled || car.approach != rel.approach) continue
                    val mv = rel.moves.firstOrNull { (it == Move.LEFT || it == Move.WAIT) && car.laneX == Lane.LEFT.x || it == Move.STRAIGHT && car.laneX == Lane.STRAIGHT.x || it == Move.RIGHT && car.laneX == Lane.RIGHT.x } ?: continue
                    if (car.approach == Compass.SOUTH) continue
                    car.path = Street.path(car.approach, car.laneX, mv, car.d)
                    car.stopAt = if (mv == Move.WAIT) WAIT_T else 1f
                    car.moving = true
                }
            }
            val g = ghost
            if (g != null) {
                // q1-1: the ghost starts at the junction mouth (tail 4.7 m ahead of your eye), never inside your own car,
                // and fades in, so it can never fill your view or sit between your eyes and P2 / P3 (profile D14 / D20)
                val show = answer == Answer.GO || answer == Answer.WAIT
                val eyeI = Vec3(lane.x - EYE_LEFT_OF_LANE, 0f, EYE_TO_CENTRE)
                val gp = Street.ghostRun(lane.x, Judge.yourMove(lane), answer == Answer.WAIT, eyeI)
                ghostPath = gp
                m.ghost.setOpacity(0f)
                g.setLocal(position = gp.at(0f), rotation = Quat4.axisAngle(Vec3.UP, Yaw.of(gp.tangent(0f))))
                g.enabled = show
                if (show) {
                    val c0 = gp.at(0f)
                    TsLog.i(String.format(Locale.US, "GHOST from=%s to=%s t0=%.3f dEye=%.2f", TsLog.pos(c0), TsLog.pos(gp.at(1f)), gp.t0, kotlin.math.hypot(c0.x - eyeI.x, c0.z - eyeI.z)))
                }
            }
            TsLog.i("FLOW q=${q.key} lane=$lane answer=$answer release=${rel?.approach}:${rel?.moves} stopped=${Judge.stopped(q.gesture, q.facing)}")
    
        }
    }

    private var flowJob: Job? = null

    /** Refills meshes on Dispatchers.IO, then runs [then] on the main thread (the last request wins). */
    private fun swap(pairs: List<Pair<MeshBuilder, MeshResource>>, then: () -> Unit) {
        flowJob?.cancel()
        flowJob =
            scope?.launch {
                withContext(Dispatchers.IO) { pairs.forEach { (b, mesh) -> b.replace(mesh) } }
                then()
            }
    }

    private var ghostPath: Street.GhostRun? = null

    private fun stepFlows(now: Long) {
        val m = mats ?: return
        if (flowsFor.isEmpty()) return
        val t = ((now - flowStartMs) / 400f).coerceIn(0f, 1f)
        m.flowGo.setOpacity(0.85f * t)
        m.flowWait.setOpacity(0.85f * t)
        m.flowStop.setOpacity(0.9f * t)
        m.routeMark.setOpacity(0.9f * t)
        if (ui.phase != Phase.WATCH) m.route.setOpacity(0.35f + 0.5f * t)
        val drive = ((now - flowStartMs - 300L) / DRIVE_MS.toFloat()).coerceIn(0f, 1f)
        val eased = drive * drive * (3 - 2 * drive)
        for (car in cars) {
            val p = car.path ?: continue
            if (!car.moving) continue
            val tt = eased * car.stopAt
            val pos = p.at(tt)
            car.e.setLocal(position = pos, rotation = Quat4.axisAngle(Vec3.UP, Yaw.of(p.tangent(tt))))
            if (drive >= 1f && car.stopAt >= 1f) {
                val fade = ((now - flowStartMs - 300L - DRIVE_MS) / 500f).coerceIn(0f, 1f)
                car.e.setOpacity(1f - fade)
                if (fade >= 1f) {
                    car.e.enabled = false
                    car.moving = false
                }
            }
        }
        val g = ghost ?: return
        val gp = ghostPath ?: return
        if (g.enabled) {
            m.ghost.setOpacity(GHOST_OPACITY * t)
            val tt = eased
            g.setLocal(position = gp.at(tt), rotation = Quat4.axisAngle(Vec3.UP, Yaw.of(gp.tangent(tt))))
        }
    }

    // ---------------------------------------------------------------- orientation aids (lesson 0, hint)

    private fun paintAids(zh: Boolean) {
        val m = mats ?: return
        aidsLang = zh
        val labels = if (zh) listOf("他的前", "他的后", "他的左", "他的右") else listOf("HIS FRONT", "HIS BACK", "HIS LEFT", "HIS RIGHT")
        val jobs = ArrayList<Triple<ModelEntity, String, IntArray>>()
        for (i in 0 until 4) jobs += Triple(aidWords[i], labels[i], intArrayOf(512, 175, 0xFFFFFFFF.toInt(), 0xCC1D4E89.toInt()))
        jobs += Triple(armTags[0], if (zh) "他的左手" else "his LEFT hand", intArrayOf(384, 118, 0xFF1B1B1B.toInt(), 0xF2C6F432.toInt()))
        jobs += Triple(armTags[1], if (zh) "他的右手" else "his RIGHT hand", intArrayOf(384, 118, 0xFF1B1B1B.toInt(), 0xF2FFFFFF.toInt()))
        scope?.launch {
            // texture uploads cost about a frame each: make them on IO, attach on main
            val made = withContext(Dispatchers.IO) { jobs.map { (_, text, a) -> m.makeText(text, a[0], a[1], a[2], a[3]) } }
            jobs.forEachIndexed { i, (e, _, _) -> m.attach(e, made[i]) }
        }
    }

    /** [officerPos] is the officer's feet in the driver frame: words lie on the road around him, tags float by his hands. */
    private fun stepAids(facing: Facing, officerPos: Vec3, show: Boolean) {
        val aids = aidsRoot ?: return
        aids.enabled = show
        armTags.forEach { it.enabled = show }
        if (!show) return
        val f = facing.compass.vec
        val l = facing.left.vec
        val dirs = listOf(f, -f, l, -l)
        aids.setLocal(position = Vec3(officerPos.x, 0f, officerPos.z))
        // small boards standing on the road around him (flat paint is unreadable from 14 m), turned to you, never mirrored
        val eye = Vec3(0f, EYE_H, 0f)
        // at the post (14 m) the boards are full size; on the lesson post (6 m) they shrink and keep out of your way
        val near = shownNear
        val r = if (near) 1.25f else 2.2f
        val ks = if (near) 0.42f else 1f
        val k = if (ui.big) 1.5f else 1f
        // q1-5: boards tier by depth as seen from your seat, so none hides him or another board. The board on your side of
        // him stands low, its top under your sight line to his feet; the board behind him floats above his head and above
        // the hand tags; the two side boards stay at the middle height, left and right of him.
        val feet = officerPos.y
        val toHim = Vec3(officerPos.x, 0f, officerPos.z).let { Vec3(it.x - eye.x, 0f, it.z - eye.z) }
        val dHim = toHim.length.coerceAtLeast(1f)
        val view = toHim * (1f / dHim)
        for (i in 0 until 4) {
            val depth = dirs[i].x * view.x + dirs[i].z * view.z
            // at the crossroads post the board on your side of him stands twice as far out, on the road in front of the
            // post; on the lesson post (6 m) it stays close, or your bonnet would hide it
            val flat = dirs[i] * (if (depth < -0.7f && !near) 2f * r else r)
            val world = Vec3(officerPos.x + flat.x, 0f, officerPos.z + flat.z)
            val dBoard = Vec3(world.x - eye.x, 0f, world.z - eye.z).length
            var kb = ks
            val y: Float
            when {
                depth < -0.7f -> {
                    // in front of him (towards you): its top stays under your sight line to the foot of his post, so it
                    // covers neither him nor the post (lesson post: under the line to his feet, so never his legs)
                    val topMax = if (near) eye.y + (feet - eye.y) * (dBoard / dHim) - 0.05f else eye.y * (1f - dBoard / dHim) - 0.03f
                    kb = minOf(ks, (topMax - 0.02f) / WORD_H).coerceAtLeast(0.25f * ks)
                    y = 0.02f + WORD_H * kb / 2
                }
                depth > 0.7f -> {
                    // behind him: bottom above his head and above the top of the higher hand tag as seen from your eye
                    val tagTop = feet + 2.0f * k + ARM_TAG_H * (if (near) 0.6f else 1f) / 2 + 0.05f
                    val head = feet + OFFICER_H * k + 0.1f
                    val clear = maxOf(head, tagTop)
                    val bottom = eye.y + (clear - eye.y) * (dBoard / dHim) + 0.08f
                    y = bottom + WORD_H * kb / 2
                }
                else -> y = if (near) 0.2f else 0.45f
            }
            val local = Vec3(flat.x, y, flat.z)
            aidWords[i].setLocal(position = local, rotation = Upright.panelRotation(world + Vec3(0f, y, 0f), eye), scale = Vec3(kb, kb, kb))
        }
        // the two hand tags at different heights so they never cover each other when he stands side-on
        val kt = if (near) 0.6f else 1f
        val leftHand = officerPos + l * (0.75f * k) + Vec3(0f, 1.55f * k, 0f)
        val rightHand = officerPos - l * (0.75f * k) + Vec3(0f, 2.0f * k, 0f)
        armTags.forEach { it.setLocal(scale = Vec3(kt, kt, kt)) }
        armTags[0].setLocal(position = leftHand, rotation = Upright.panelRotation(leftHand, eye))
        armTags[1].setLocal(position = rightHand, rotation = Upright.panelRotation(rightHand, eye))
    }

    // =========================================================================================
    // per frame
    // =========================================================================================

    fun onFrame(context: SceneUpdateContext) {
        val root = stageRoot ?: return
        if (root.scene == null || context.scene != root.scene) return
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.25f)
        lastFrameMs = now
        frames++
        head.pose()?.let { if (Upright.valid(it)) pose = it }
        val rig = rig ?: return
        rig.onFrame(now)
        pose?.let { rig.stowBehind(it) }
        WindowCtl.stepGate(now, pose?.let { debugPitch ?: Math.toDegrees(it.pitch.toDouble()).toFloat() })
        stepRays()
        checkBlocked(now)
        if (!attached || paused) return
        val state = ui
        if (built) {
            stepPlacement(state, now)
            if (placed) {
                stepFade(dt)
                if (!state.paused) stepOfficer(state, dt, now)
                stepFlows(now)
                stepStartupLog(now)
            }
        }
        placeChip(now)
        if (now - lastLogMs > LOG_MS) {
            lastLogMs = now
            TsLog.i(String.format(Locale.US, "FRAME n=%d head=%s state=%s officer=%s yaw=%.0f stick=%.2f", frames, TsLog.pos(pose?.position), state.designState(), officer?.playing, Math.toDegrees(officerYaw.toDouble()), stick.x))
        }
    }

    private fun checkBlocked(now: Long) {
        val reason = head.blockReason()
        if (reason == null || pose != null) {
            blockedSinceMs = 0L
            return
        }
        if (blockedSinceMs == 0L) blockedSinceMs = now
        if (now - blockedSinceMs > BLOCK_AFTER_MS && now - attachMs > BLOCK_AFTER_MS && ui.screen != Screen.BLOCKED) {
            TsLog.i("BLOCKED reason=$reason")
            sink(TsEvent.Blocked(reason))
        }
    }

    // ---------------------------------------------------------------- placement (design container.placement)

    private fun sightYaw(p: HeadPose): Float = debugYaw?.let { Math.toRadians(it.toDouble()).toFloat() } ?: p.yaw(0f)

    private fun stepPlacement(state: TsUiState, now: Long) {
        if (state.placeEpoch != placeEpoch) {
            placeEpoch = state.placeEpoch
            placer.request("epoch=$placeEpoch", now)
            TsLog.i("PLACE start epoch=$placeEpoch")
        }
        val p = pose
        if (WindowCtl.createdEpoch != windowEpoch && p != null) {
            windowEpoch = WindowCtl.createdEpoch
            // the system has just put the window on the line of sight: the seat follows if you turned since (D41)
            placer.windowOpened(now, sightYaw(p))
        }
        val spot = placer.step(now, p?.let { it.position to sightYaw(it) }) ?: return
        origin = Vec3(spot.eye.x, spot.eye.y - EYE_H, spot.eye.z)
        yaw0 = spot.yaw
        updateIntersectionOffset()
        sky?.setLocal(position = origin)
        if (spot.why != "settle") {
            val feet = StageLayout.officerFeet(shownNear, shownLane.x)
            val at = Upright.toStage(origin, StageLayout.driverYaw(yaw0, shownLane.x, shownBig), feet)
            TsLog.i(String.format(Locale.US, "PLACE done %s origin=%s sightYaw=%.1f seat=%.1f eye=%s officer=%s dHead=%.2f gapDeg=%.1f", spot.why, TsLog.pos(origin), Math.toDegrees(yaw0.toDouble()), Math.toDegrees(StageLayout.seatYaw(shownLane.x, shownBig).toDouble()), TsLog.pos(spot.eye), TsLog.pos(at), (at + Vec3(0f, OFFICER_H / 2, 0f) - spot.eye).length, StageLayout.windowGapDeg(feet, shownLane.x, shownBig)))
        }
        if (spot.jump) {
            // a visible jump (recenter, window reopened elsewhere): the same short fade as a lane change
            fadeT = 0f
            fadeDir = 1
            driver?.setOpacity(0f)
        }
        if (!placed) {
            placed = true
            placedAtMs = now
            sink(TsEvent.SceneReady)
        }
    }

    /** Your lane puts the junction centre at (-(laneX - 0.4), 0, -14) in the driver frame (design scene.intersection.you). */
    private fun interOffset(lane: Lane): Vec3 = StageLayout.junctionLocal(lane.x)

    /** Seat for the shown lane: the eye on the turning axis, the officer [StageLayout.OFFICER_LEFT_DEG] left of the window (D41). */
    private fun updateIntersectionOffset() {
        inter?.setLocal(position = interOffset(shownLane))
        lessonPost?.setLocal(position = StageLayout.lessonSpot(shownLane.x))
        lessonPost?.enabled = shownNear
        driver?.setLocal(position = origin, rotation = Quat4.axisAngle(Vec3.UP, StageLayout.driverYaw(yaw0, shownLane.x, shownBig)))
    }

    private fun stepStartupLog(now: Long) {
        if (logIndex >= logAt.size) return
        if (now - placedAtMs < logAt[logIndex]) return
        logIndex++
        val p = pose
        val o = officerRoot?.let { runCatching { it.convertPositionTo(Vector3.ZERO, stageRoot) }.getOrNull()?.domain() }
        val parts = ArrayList<String>()
        for (slot in rig?.all ?: emptyList()) {
            if (!slot.shown) continue
            val at = slot.position
            val rel = if (p != null) at - p.position else at
            val ang = if (p != null) Math.toDegrees(Yaw.wrap(Yaw.of(rel) - p.yaw(0f)).toDouble()) else 0.0
            parts += String.format(Locale.US, "%s=%s d=%.2f yawOff=%.0f", slot.id, TsLog.pos(at), rel.length, ang)
        }
        val od = if (p != null && o != null) (o - p.position).length else -1f
        TsLog.i(String.format(Locale.US, "STARTUP t=%ds head=%s officer=%s dOfficer=%.1f visible=%s panels[%s]", logAt[logIndex - 1] / 1000, TsLog.pos(p?.position), TsLog.pos(o), od, officerRoot?.enabled, parts.joinToString("; ")))
    }

    // ---------------------------------------------------------------- lane fade (design S9: 0.25 s out, move, 0.25 s in)

    private fun stepFade(dt: Float) {
        val d = driver ?: return
        if (fadeT < 1f && fadeDir == 0 && pendingLane == null) fadeDir = 1 // first appearance
        val want = ui.lane.takeIf { ui.screen == Screen.QUIZ || ui.screen == Screen.INTRO } ?: Lane.STRAIGHT
        if ((want != shownLane || ui.big != shownBig || ui.officerNear != shownNear) && fadeDir == 0) {
            pendingLane = want
            fadeDir = -1
        }
        if (fadeDir == 0) return
        fadeT = (fadeT + fadeDir * dt / FADE_S).coerceIn(0f, 1f)
        d.setOpacity(fadeT)
        if (fadeDir < 0 && fadeT <= 0f) {
            pendingLane?.let { shownLane = it }
            pendingLane = null
            shownBig = ui.big
            shownNear = ui.officerNear
            updateIntersectionOffset()
            fadeDir = 1
        } else if (fadeDir > 0 && fadeT >= 1f) {
            fadeDir = 0
            d.setOpacity(1f)
            if (ui.phase == Phase.SWITCHING) sink(TsEvent.LaneArrived)
        }
    }

    // ---------------------------------------------------------------- officer

    private fun stepOfficer(state: TsUiState, dt: Float, now: Long) {
        val oRoot = officerRoot ?: return
        val off = officer ?: return
        // "leave?" on top of a question: the signal stops where it is and carries on after [keep going] (q3-2)
        val hold = state.screen == Screen.QUIZ && state.dialog != Dialog.NONE
        if (hold != officerHeld) {
            officerHeld = hold
            if (hold) off.hold(now) else off.unhold(now)
            TsLog.i("GESTURE ${if (hold) "hold" else "carry on"} playing=${off.playing}")
        }
        if (hold) return
        val lesson = state.screen == Screen.LESSON || state.breakdown
        val q = state.question
        // where: the near post 6 m out, else the centre post; never anywhere else (D14)
        val pos = StageLayout.officerFeet(shownNear, shownLane.x)
        // which way
        val facing: Facing =
            when {
                state.screen == Screen.LESSON && state.lesson == 0 -> Content.lesson0Facing[state.lessonStep.coerceIn(0, 3)]
                lesson -> state.lessonAngle
                state.screen == Screen.QUIZ && q != null -> q.facing
                else -> Facing.SOUTH
            }
        if (state.screen == Screen.LESSON) {
            val x = stick.x
            val target = if (abs(x) > STICK_DEAD) -x * STICK_RATE else 0f
            stickVel += (target - stickVel) * (dt / 0.2f).coerceIn(0f, 1f)
            stickYaw = Yaw.wrap(stickYaw + stickVel * dt)
        } else {
            stickYaw = 0f
            stickVel = 0f
        }
        val wantYaw = Yaw.wrap(facing.compass.yaw + stickYaw + if (state.camSide) (PI / 2).toFloat() else 0f)
        if (abs(Yaw.wrap(wantYaw - officerYawTo)) > 0.01f) {
            if (stickVel != 0f && abs(Yaw.wrap(wantYaw - officerYaw)) < 0.2f) {
                officerYaw = wantYaw
                officerYawTo = wantYaw
                turnT = 1f
            } else {
                officerYawFrom = officerYaw
                officerYawTo = wantYaw
                turnT = 0f
            }
        }
        if (turnT < 1f) {
            turnT = (turnT + dt / TURN_S).coerceIn(0f, 1f)
            val e = turnT * turnT * (3 - 2 * turnT)
            officerYaw = officerYawFrom + Yaw.wrap(officerYawTo - officerYawFrom) * e
        }
        oRoot.setLocal(position = pos, rotation = Quat4.axisAngle(Vec3.UP, officerYaw))
        val showAids = (state.screen == Screen.LESSON && state.lesson == 0) || (state.screen == Screen.QUIZ && state.hint && state.phase == Phase.WATCH)
        stepAids(facing, pos, showAids)
        if (!off.ready) return
        val speed = if (state.slow) 0.5f else 1f
        // what to play
        val g: Gesture? =
            when {
                lesson -> state.lessonGesture
                state.screen == Screen.QUIZ && q != null -> q.gesture
                else -> null
            }
        val turning = turnT < 1f
        if (g == null) {
            if (off.playing != "stand") off.play("stand", now, 1f)
            return
        }
        val frozen = lesson && !state.lessonPlaying || state.freeze
        if (frozen) {
            val steps = off.steps(g)
            val k = state.lessonStep.coerceIn(0, steps.size - 1)
            val key = "${g.id}:$k"
            if (key != lastLessonKey || off.frozenAt == null) {
                lastLessonKey = key
                freezeFrames = 0
            }
            // a clip that has just been started ignores setTime until the engine has ticked it once: keep re-applying the
            // freeze for a few frames (lesson 2 step 2 opened from the list showed the standing pose otherwise)
            if (freezeFrames < FREEZE_FRAMES) {
                freezeFrames++
                off.freeze(g.id, steps[k], now)
            }
            return
        }
        lastLessonKey = ""
        if (state.gestureEpoch != gestureEpoch) {
            gestureEpoch = state.gestureEpoch
            pendingGesture = g.id
            doneSent = false
            if (off.playing != "stand" || off.frozenAt != null) off.play("stand", now, 1f, 0.2f)
        }
        if (state.phase == Phase.SWITCHING && !lesson) return
        val pend = pendingGesture
        if (pend != null && !turning && fadeDir == 0) {
            pendingGesture = null
            off.play(pend, now, speed, 0.2f)
            TsLog.i("GESTURE play ${pend} speed=$speed facing=$facing")
        }
        if (pendingGesture == null && off.playing == g.id) {
            if (lesson && state.lessonPlaying) {
                // report the step being shown; loop with 1 s standing between rounds
                val t = off.time(now)
                val steps = off.steps(g)
                val k = steps.indexOfLast { it - 0.6f <= t }.coerceAtLeast(0)
                sink(TsEvent.LessonAt(k))
                if (off.done(now)) {
                    off.play("stand", now, 1f, 0.2f)
                    loopRestartAt = now + 1000L
                }
            } else if (off.done(now) && !doneSent) {
                doneSent = true
                TsLog.i("GESTURE done ${g.id}")
                sink(TsEvent.GestureDone)
            }
        } else if (lesson && state.lessonPlaying && off.playing == "stand" && pendingGesture == null && loopRestartAt in 1..now) {
            loopRestartAt = 0L
            pendingGesture = g.id
        }
    }

    // ---------------------------------------------------------------- the Stage chip (native-window-move §3.3, §7.9)

    private fun placeChip(now: Long) {
        val rig = rig ?: return
        val slot = rig[PanelIds.OPEN] ?: return
        val p = pose
        if (WindowCtl.levelHint && p != null) {
            // on the line of sight so it is seen whatever the pitch (clamped), upright (D9), re-posed every frame
            chipAt = null
            val pitch = (debugPitch ?: Math.toDegrees(p.pitch.toDouble()).toFloat()).coerceIn(-40f, 40f)
            val at = StageLayout.chipAt(p.position, p.yaw(0f), 0.75f, -pitch)
            rig.faceViewer(p.position)
            if (slot.shown) slot.moveTo(at) else slot.show(at)
            if (!hintShown) TsLog.i("LEVEL hint show at=${TsLog.pos(at)}")
            hintShown = true
            return
        }
        if (hintShown) {
            hintShown = false
            slot.hide()
        }
        if (WindowCtl.chipWanted(now) && p != null && ui.screen != Screen.STARTING) {
            // posed from the head when it appears, below the sight line; again if it has left your view
            var at = chipAt
            if (at != null) {
                val off = Yaw.wrap(Yaw.of(at - p.position) - p.yaw(0f))
                if (abs(off) > CHIP_REPOSE_RAD) at = null
            }
            if (at == null) {
                at = StageLayout.chipAt(p.position, p.yaw(0f), 1.0f, 16f)
                chipAt = at
                rig.faceViewer(p.position)
                if (slot.shown) slot.moveTo(at) else slot.show(at)
                TsLog.i("CHIP show at=${TsLog.pos(at)} window=${WindowCtl.panelState}")
            }
        } else if (chipAt != null) {
            chipAt = null
            slot.hide()
        }
    }

    // ---------------------------------------------------------------- your controller rays (profile D41)

    private fun stepRays() {
        if (rays.isEmpty()) return
        val poses = debugRay?.let { listOf(it) } ?: stick.rays()
        rays.forEachIndexed { i, e ->
            val r = poses.getOrNull(i)
            e.enabled = r != null
            if (r != null) e.setLocal(position = r.first, rotation = Quat4.fromTo(Vec3(0f, 0f, -1f), r.second))
        }
    }

    // ---------------------------------------------------------------- lights (debug `light`)

    fun lightLine(): String = String.format(Locale.US, "LIGHT ibl=%s exp=%.1f sun=%.0f lux", envLight != null, envLight?.intensityExponent ?: 0f, sunLight?.intensity ?: 0f)

    fun debugLight(what: String, arg: String): String {
        when (what) {
            "ibl" -> arg.toFloatOrNull()?.let { envLight?.intensityExponent = it.coerceIn(-24f, 24f) }
            "sun" -> arg.toFloatOrNull()?.let { sunLight?.intensity = it.coerceAtLeast(0f) }
        }
        return lightLine().also { TsLog.i(it) }
    }

    // ---------------------------------------------------------------- debug

    fun dump(): List<String> {
        val out = ArrayList<String>()
        out += "SCENE built=$built placed=$placed origin=${TsLog.pos(origin)} yaw=${Math.toDegrees(yaw0.toDouble()).toInt()} lane=$shownLane fade=$fadeT"
        out += "OFFICER ready=${officer?.ready} playing=${officer?.playing} frozen=${officer?.frozenAt} yaw=${Math.toDegrees(officerYaw.toDouble()).toInt()} opacityComp=${officerRoot?.hasOpacityComponent()}"
        // instance list (profile D24): every 3D entity, its model, height, opacity component
        out += "INSTANCE officer model=officer.glb heightM=1.75 scale=${if (ui.big) 1.5 else 1.0} opacityComponent=${officerRoot?.hasOpacityComponent()} transparent=no"
        cars.forEachIndexed { i, c -> out += "INSTANCE car$i enabled=${c.e.enabled} approach=${c.approach} laneX=${c.laneX} heightM=1.45 opacityComponent=${c.e.hasOpacityComponent()} transparent=no" }
        out += "INSTANCE ghost enabled=${ghost?.enabled} transparent=yes(intended)"
        out += "INSTANCE driver opacityComponent=${driver?.hasOpacityComponent()}"
        out += "WINDOW state=${WindowCtl.panelState} open=${WindowCtl.open} ${lightLine()}"
        rig?.dump()?.let { out += it }
        return out
    }

    fun setDebugStick(x: Float?) {
        stick.debugX = x
    }

    // =========================================================================================

    /** Materials of the scene (Unlit street, transparent flows; the officer keeps his own PBR material). */
    private class Mats(private val sh: SharedResources) {
        private val atlas = sh.keep(TextureResource.create(flipV(Palette.atlas())))
        val toon: UnlitMaterial = unlit(atlas)

        /** Cars, yours and the others: the atlas's flat colours as base colour under the same IBL + sun as the officer (B24, q3-6). */
        val paint: PhysicallyBasedMaterial =
            sh.keep(
                PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(atlas)
                    setMetallic(0f)
                    setRoughness(0.42f)
                    setCullingMode(MaterialCullingMode.NONE)
                }
            )
        val ground: UnlitMaterial = unlit(sh.keep(TextureResource.create(flipV(Street.groundTexture()))))
        val sky: UnlitMaterial = unlit(sh.keep(TextureResource.create(flipV(Street.skyTexture()))))
        val flowGo: UnlitMaterial = sheer(UiColors.PASSABLE, 0.85f)
        val flowWait: UnlitMaterial = sheer(UiColors.ALERT, 0.85f)
        val flowStop: UnlitMaterial = sheer(UiColors.ERROR, 0.9f)
        val route: UnlitMaterial = sheer(UiColors.INTERACTION, 0.55f)
        val routeMark: UnlitMaterial = sheer(UiColors.ALERT, 0.9f)
        val ghost: UnlitMaterial = sheer(UiColors.PASSABLE, GHOST_OPACITY)
        private val textures = ArrayList<TextureResource>()

        private fun unlit(tex: TextureResource): UnlitMaterial =
            sh.keep(
                UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(tex)
                    runCatching { setApplyToneMapping(false) }
                    setCullingMode(MaterialCullingMode.NONE)
                }
            )

        private fun sheer(c: Color4, opacity: Float): UnlitMaterial =
            sh.keep(
                UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                    setBaseColor(c)
                    setOpacity(opacity)
                    runCatching { setApplyToneMapping(false) }
                    setCullingMode(MaterialCullingMode.NONE)
                    setDepthWrite(false)
                }
            )

        fun newTextMaterial(): UnlitMaterial =
            sh.keep(
                UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    runCatching { setApplyToneMapping(false) }
                    setCullingMode(MaterialCullingMode.NONE)
                    setDepthWrite(false)
                }
            )

        /** The ray: white fading out along its length (mesh V 0 at the controller, 1 at the far end). */
        fun rayMaterial(): UnlitMaterial {
            val bmp = Bitmap.createBitmap(4, 64, Bitmap.Config.ARGB_8888)
            for (y in 0 until 64) {
                val a = (230f * (1f - y / 63f)).toInt()
                val c = (a shl 24) or 0xFFFFFF
                for (x in 0 until 4) bmp.setPixel(x, y, c)
            }
            return sh.keep(
                UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(sh.keep(TextureResource.create(flipV(bmp))))
                    runCatching { setApplyToneMapping(false) }
                    setCullingMode(MaterialCullingMode.NONE)
                    setDepthWrite(false)
                }
            )
        }

        /** The contact shadow: a soft radial dark blob. */
        fun contactMaterial(): UnlitMaterial {
            val n = 128
            val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            for (y in 0 until n) for (x in 0 until n) {
                val dx = (x + 0.5f) / n * 2 - 1
                val dy = (y + 0.5f) / n * 2 - 1
                val r = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(1f)
                val k = 1f - r
                bmp.setPixel(x, y, ((220f * minOf(1f, k * 1.6f) * minOf(1f, k * 1.6f)).toInt() shl 24) or 0x101418)
            }
            return sh.keep(
                UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(sh.keep(TextureResource.create(bmp)))
                    runCatching { setApplyToneMapping(false) }
                    setCullingMode(MaterialCullingMode.NONE)
                    setDepthWrite(false)
                }
            )
        }

        fun makeText(text: String, w: Int, h: Int, ink: Int, bg: Int): TextureResource {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = bg
            c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h * 0.3f, h * 0.3f, p)
            p.color = ink
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.52f
            while (p.measureText(text) > w * 0.9f) p.textSize *= 0.92f
            val fm = p.fontMetrics
            c.drawText(text, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2, p)
            val tex = TextureResource.create(flipV(bmp))
            tex.toGlobal()
            synchronized(textures) { textures += tex }
            return tex
        }

        fun attach(e: ModelEntity, tex: TextureResource) {
            (e.components[ModelComponent::class.java]?.materials?.get(0) as? UnlitMaterial)?.setBaseColorTexture(tex)
        }

        fun close() {
            textures.forEach { runCatching { it.close() } }
            textures.clear()
        }
    }

    companion object {
        const val EYE_H = StageLayout.EYE_H
        const val EYE_TO_CENTRE = StageLayout.EYE_TO_CENTRE
        const val EYE_LEFT_OF_LANE = StageLayout.EYE_LEFT_OF_LANE
        const val SKY_R = 260f
        const val IBL_ASSET = "ibl/city_ibl.ktx"
        /** Tuned with `light ...` on emulator-5554: no large area of his shirt over 245 in the close-up (B24, build/ui-audit). */
        const val IBL_EXP = 9f
        const val SUN_LUX = 3000f
        const val RAY_LEN = 2.6f
        val CHIP_REPOSE_RAD = Math.toRadians(60.0).toFloat()
        const val CAR_POOL = 10
        const val CAR_FIRST = 15.5f
        const val LIFT = 0.025f
        const val LIFT2 = 0.035f
        const val WAIT_T = 0.42f
        const val GHOST_OPACITY = 0.35f
        const val WORD_H = 0.7f
        const val ARM_TAG_H = 0.34f
        const val OFFICER_H = StageLayout.OFFICER_H
        const val GHOST_HALF_LEN = 2.3f
        const val DRIVE_MS = 3000L
        const val FADE_S = 0.25f
        const val TURN_S = 0.5f
        const val FREEZE_FRAMES = 8
        const val STICK_DEAD = 0.15f
        val STICK_RATE = Math.toRadians(60.0).toFloat()
        const val BLOCK_AFTER_MS = 4_000L
        const val LOG_MS = 5_000L

        /** A flat quad on the ground (XZ), [w] × [h] m, text top towards -Z (north, away from you). */
        fun flatQuad(w: Float, h: Float, name: String): MeshResource {
            val b = MeshBuilder(4)
            val n = Vec3.UP
            b.quad(
                b.vertex(Vec3(-w / 2, 0f, h / 2), n, Vector2(0f, 1f)),
                b.vertex(Vec3(w / 2, 0f, h / 2), n, Vector2(1f, 1f)),
                b.vertex(Vec3(w / 2, 0f, -h / 2), n, Vector2(1f, 0f)),
                b.vertex(Vec3(-w / 2, 0f, -h / 2), n, Vector2(0f, 0f)),
            )
            return b.build(name)
        }

        /** The ray: two crossed thin quads from the origin along -Z, [RAY_LEN] long, V 0 at the origin. */
        fun rayMesh(): MeshResource {
            val b = MeshBuilder(8)
            val w = 0.0035f
            val n = Vec3(0f, 1f, 0f)
            fun v(x: Float, y: Float, z: Float, u: Float, t: Float) = b.vertex(Vec3(x, y, z), n, Vector2(u, t))
            b.quad(v(-w, 0f, 0f, 0f, 0f), v(w, 0f, 0f, 1f, 0f), v(w, 0f, -RAY_LEN, 1f, 1f), v(-w, 0f, -RAY_LEN, 0f, 1f))
            b.quad(v(0f, -w, 0f, 0f, 0f), v(0f, w, 0f, 1f, 0f), v(0f, w, -RAY_LEN, 1f, 1f), v(0f, -w, -RAY_LEN, 0f, 1f))
            return b.build("ray")
        }

        /** An upright quad facing +Z (towards the viewer once turned), [w] × [h] m. */
        fun uprightQuad(w: Float, h: Float, name: String): MeshResource {
            val b = MeshBuilder(4)
            val n = Vec3(0f, 0f, 1f)
            b.quad(
                b.vertex(Vec3(-w / 2, -h / 2, 0f), n, Vector2(0f, 1f)),
                b.vertex(Vec3(w / 2, -h / 2, 0f), n, Vector2(1f, 1f)),
                b.vertex(Vec3(w / 2, h / 2, 0f), n, Vector2(1f, 0f)),
                b.vertex(Vec3(-w / 2, h / 2, 0f), n, Vector2(0f, 0f)),
            )
            return b.build(name)
        }
    }
}

/** The four semantic colours (design palette.colorScheme) as engine colours. */
internal object UiColors {
    val INTERACTION = Color4(0x3D / 255f, 0x8B / 255f, 1f, 1f)
    val PASSABLE = Color4(0x5F / 255f, 0xD3 / 255f, 0x8A / 255f, 1f)
    val ALERT = Color4(1f, 0xC2 / 255f, 0x33 / 255f, 1f)
    val ERROR = Color4(1f, 0x6B / 255f, 0x5E / 255f, 1f)
}
