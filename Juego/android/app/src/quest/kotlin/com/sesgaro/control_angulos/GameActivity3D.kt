// Body tracking (finger joints) is an experimental Spatial SDK API.
@file:OptIn(com.meta.spatial.core.SpatialSDKExperimentalAPI::class)

package com.sesgaro.control_angulos

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.meta.spatial.core.Color4
import com.meta.spatial.core.ComponentBase
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Query
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.isdk.IsdkFeature
import com.meta.spatial.isdk.IsdkGrabFilter
import com.meta.spatial.isdk.IsdkGrabMovementType
import com.meta.spatial.isdk.IsdkGrabState
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.isdk.IsdkSphereCollider
import com.meta.spatial.runtime.AlphaMode
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.ControllerPose
import com.meta.spatial.runtime.JointType
import com.meta.spatial.runtime.SkeletonJoint
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.AvatarSystem
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Box
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.ControllerType
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Sphere
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.ViewPanelRegistration
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.VRFeature
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/// Quest tic-tac-toe table over passthrough, scaled to the machine, with pieces, detected LDR
/// cell, and actual head position. Slingshot predicts a landing point and fires on release;
/// valid shots fall through a free cell and misses bounce. Claw mode moves while the stick is
/// tilted and fires with a button. Players choose modes independently and face each other;
/// player 2 sees a half-turn rotation and the opponent's controls. Shared calibration touches
/// the real head at two firmware-selected points to place each table in machine coordinates.
/// Head/hand PoseFrames pass through the ESP32. Hand-only interaction sends move/fire requests
/// to the BLE-owning 2D panel over PanelBus and renders returned state.
class GameActivity3D : AppSystemActivity() {

    /// Latest game state from the 2D panel, received on the main thread and read by the scene system.
    @Volatile private var game: Map<*, *> = emptyMap<Any, Any>()

    /// Latest remote headset pose and its arrival time (System.nanoTime).
    @Volatile private var remotePose: ByteArray? = null
    @Volatile private var remoteAt = 0L

    private val busListener: (String, Map<*, *>) -> Unit = { method, args ->
        when (method) {
            "game" -> game = args
            // ESP relays both players' poses. Ignore our own to avoid overwriting
            // the opponent and making the avatar flicker.
            "poses" -> (args["data"] as? ByteArray)?.takeIf { it.size > 1 && (it[1].toInt() and 0xFF) != int("me") }?.let {
                remotePose = it
                remoteAt = System.nanoTime()
            }
        }
    }

    private val scene3d = BoardScene()
    private var sfx: Sfx? = null
    /// Optional looping board music from res/raw/musica.ogg.
    private var music: MediaPlayer? = null
    private var status: TextView? = null
    private var calibButton: Button? = null

    /// Requested by the Calibrate button and handled by the scene system.
    @Volatile private var calibToggle = false

    override fun registerFeatures(): List<SpatialFeature> = listOf(
        VRFeature(this),
        // Interaction SDK hands grab the ball, joystick, and button.
        IsdkFeature(this, spatial, systemManager),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PanelBus.started(this)
        PanelBus.listen(busListener)
        if (checkSelfPermission(BODY_TRACKING) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(BODY_TRACKING), 1)
        }
    }

    override fun onPause() {
        music?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        music?.start()
    }

    override fun onDestroy() {
        music?.release()
        music = null
        sfx?.release()
        PanelBus.unlisten(busListener)
        PanelBus.stopped(this)
        super.onDestroy()
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.enablePassthrough(true)
        // Cat paws replace the system's transparent hands.
        systemManager.findSystem<AvatarSystem>().setShowHands(false)
        scene.setLightingEnvironment(
            Vector3(0.35f),
            Vector3(2.2f, 2.1f, 2.0f),
            -Vector3(0.3f, 1.0f, -0.8f),
            0.3f,
        )
        sfx = Sfx()
        // The copyrighted res/raw/musica.ogg is excluded from this public repository.
        // Missing music is optional.
        val musicId = resources.getIdentifier("musica", "raw", packageName)
        music = musicId.takeIf { it != 0 }?.let { MediaPlayer.create(this, it) }?.apply {
            isLooping = true
            setVolume(MUSIC_VOLUME, MUSIC_VOLUME)   // Keep music quiet enough to hear sound effects.
            start()
        }
        systemManager.registerSystem(GameSystem())
        PanelBus.send(this, "hello")
    }

    override fun registerPanels(): List<PanelRegistration> = listOf(
        ViewPanelRegistration(
            R.id.game3d_status,
            { _, ctx ->
                TextView(ctx).apply {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.argb(190, 40, 10, 22))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(24, 8, 24, 8)
                    text = "Waiting for data…"
                }
            },
            { _ -> UIPanelSettings(QuadShapeOptions(0.36f, 0.12f)) },
            { view, _, _ -> status = view as TextView },
        ),
        button(R.id.game3d_new, "New game") { PanelBus.send(this, "newGame") },
        // Immersive mode hides 2D windows; this button restores them.
        button(R.id.game3d_back, "Back to 2D") { backTo2d() },
        button(R.id.game3d_calib, "Calibrate", { calibButton = it }) { calibToggle = true },
    )

    private fun button(id: Int, label: String, onView: (Button) -> Unit = {}, onClick: () -> Unit) = ViewPanelRegistration(
        id,
        { _, ctx ->
            Button(ctx).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                background = GradientDrawable().apply {
                    cornerRadius = 200f
                    setColor(Color.rgb(188, 0, 75))
                }
                setOnClickListener { onClick() }
            }
        },
        { _ -> UIPanelSettings(QuadShapeOptions(0.14f, 0.045f)) },
        { view: View, _, _ -> onView(view as Button) },
    )

    /// Return home with the 2D panel in front, following Meta's immersive-to-panel transition.
    private fun backTo2d() {
        val panel = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            panel,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("extra_launch_in_home_pending_intent", pending),
        )
        finishAndRemoveTask()
    }

    // State decoding.

    private fun num(key: String, default: Float = 0f) = (game[key] as? Number)?.toFloat() ?: default
    private fun int(key: String, default: Int = 0) = (game[key] as? Number)?.toInt() ?: default

    /// Board dimensions in mm, as configured by the ESP.
    private class Geometry(
        val xMin: Float, val xMax: Float, val yMin: Float, val yMax: Float,
        val x0: Float, val xStep: Float, val y0: Float, val yStep: Float,
    ) {
        val left get() = x0 - xStep / 2
        val right get() = x0 + 2.5f * xStep
        val bottom get() = y0 - yStep / 2
        val top get() = y0 + 2.5f * yStep
        fun cellX(c: Int) = x0 + (c % 3) * xStep
        fun cellY(c: Int) = y0 + (c / 3) * yStep
    }

    private fun geometry(): Geometry? {
        if (!game.containsKey("xStep")) return null
        val g = Geometry(
            num("xMin"), num("xMax"), num("yMin"), num("yMax"),
            num("x0"), num("xStep"), num("y0"), num("yStep"),
        )
        return if (g.xStep > 0 && g.yStep > 0) g else null
    }

    /// Only the active player can move the head (phase 1 = turn).
    private fun canMove() =
        game["connected"] == true && int("phase") == 1 && int("turn") == int("me")

    private fun clawMode() = int("mode") == 1

    // Scene: all table elements descend from root; +Z faces the user and +X points right.
    // Machine X goes right and Y goes away (-Z). Player 2 at maximum Y gets a half-turn
    // rotation via side. Slingshot, joystick, and button stay on the local edge. Grabbable
    // ball, joystick knob, and button have no parent so grabs operate in world coordinates.
    private inner class BoardScene {
        lateinit var root: Entity
        lateinit var head: Entity
        private lateinit var timeBar: Entity
        private lateinit var landing: Entity
        private val winDots = mutableListOf<Entity>()
        private val confetti = mutableListOf<Entity>()
        private var confettiPos = Array(CONFETTI) { Vector3(0f) }
        private var confettiVel = Array(CONFETTI) { Vector3(0f) }
        private var confettiLeft = 0f
        private var winLook = ""
        private var barLook = -1

        /// Local claw sway, added to head movement.
        var headSway = Vector3(0f)
        lateinit var base: Entity
        private val tiles = mutableListOf<Entity>()
        private val holes = mutableListOf<Entity>()
        private val sensors = mutableListOf<Entity>()
        private val pieces = mutableListOf<Pair<Entity, Entity>>()
        lateinit var geo: Geometry

        var rootPose = Pose(Vector3(0f, -10f, 0f), Quaternion())
        var placed = false
        var built = false

        /// Calibration adjusts table scale to the real two-point distance (1 for the physical board).
        var scale = 1f
        var calibrated = false
        var calibrating = false
        /// Floating calibration ball above the head marks the touch target.
        lateinit var markA: Entity
        lateinit var calibBtn: Entity
        lateinit var calibAt: Vector3
        var boardW = 0f

        // Remote player: cat head and paws in their piece color.
        private val avatar = mutableListOf<Entity>()
        private val faces = mutableListOf<Entity>()

        // Local left/right cat paws use a two-bone palm/finger rig baked into PAW_FRAMES
        // closure states. Display one pata_{x,o}_N.glb at a time; see
        // tool/pata_a_glb.py.
        private val paws = List(2) { mutableListOf<Entity>() }
        private val pawFrame = intArrayOf(-1, -1)
        private var pawColor = -1
        private val pawCurl = FloatArray(2)
        private var avatarColor = -1

        /// 1 for the minimum-Y side (player 1), -1 for the opposite side (player 2).
        var side = 1f

        /// Meters per mm at real scale; calibration adjusts scale.
        val k = 0.001f
        /// Meters per machine-axis unit; see build.
        var kx = k
        var ky = k
        var cx = 0f
        var cy = 0f
        var halfH = 0f

        // Slingshot.
        lateinit var ball: Entity
        lateinit var slingRest: Vector3
        private lateinit var forkL: Vector3
        private lateinit var forkR: Vector3
        private val bands = mutableListOf<Entity>()
        private val dots = mutableListOf<Entity>()
        private val slingParts = mutableListOf<Entity>()

        // Claw machine.
        lateinit var knob: Entity
        lateinit var knobRest: Vector3
        lateinit var button: Entity
        lateinit var buttonAt: Vector3
        private val stick = mutableListOf<Entity>()
        private val clawParts = mutableListOf<Entity>()
        private val headParts = mutableListOf<Entity>()
        private val headClaw = mutableListOf<Entity>()
        private var headClawLook = -1
        private lateinit var slingOrigin: Vector3

        // Fixed opponent controls at the opposite edge.
        private val remoteSling = mutableListOf<Entity>()
        private val remoteClaw = mutableListOf<Entity>()
        private var remoteLook = -2

        /// Head height above the table; the claw rises and falls.
        var headLift = HEAD_LIFT

        private var baseLook = -1
        private var modeLook = -1
        private val tileLook = IntArray(9) { -1 }
        private val pieceLook = IntArray(9) { -1 }

        fun local(xMm: Float, yMm: Float, lift: Float = 0f) =
            Vector3(side * (xMm - cx) * kx, lift, -side * (yMm - cy) * ky)

        /// Table-local point to machine mm.
        fun toMm(l: Vector3): Pair<Float, Float> = (cx + side * l.x / kx) to (cy - side * l.z / ky)

        fun toLocal(world: Vector3) = (rootPose.inverse() * world) * (1f / scale)
        fun toWorld(l: Vector3) = rootPose * (l * scale)

        /// Local point to board mm: machine x/y and height h.
        fun toBoardMm(l: Vector3) = Triple(cx + side * l.x / kx, cy - side * l.z / ky, l.y / k)
        fun fromBoardMm(x: Float, y: Float, h: Float) = local(x, y, h * k)

        /// Changing player swaps sides: rearrange cells and rotate a calibrated table
        /// by half a turn to keep the machine fixed and controls on the local edge.
        fun setSide(me: Int) {
            val s = if (me == 2) -1f else 1f
            if (s == side) return
            side = s
            placeCells()
            winLook = "-"
            if (calibrated) {
                place(Pose(rootPose.t, rootPose.q * HALF_TURN))
                savedCalibration = Triple(rootPose, scale, side)
            }
        }

        private fun placeCells() {
            for (c in 0 until 9) {
                val center = local(geo.cellX(c), geo.cellY(c))
                setLocal(tiles[c], center)
                setLocal(holes[c], center)
                setLocal(sensors[c], center + Vector3(0f, 0.002f, 0f))
                setLocal(pieces[c].first, center + Vector3(0f, 0.004f, 0f))
                setLocal(pieces[c].second, center + Vector3(0f, 0.004f, 0f))
            }
        }

        /// Build once when ESP geometry arrives.
        fun build(g: Geometry) {
            geo = g
            cx = (g.left + g.right) / 2
            cy = (g.bottom + g.top) / 2
            val w = BOARD_MM * k
            val h = BOARD_MM * k
            halfH = h / 2
            boardW = w
            // Board center matches the central sensor. Physical dimensions are
            // BOARD_MM and SQUARE_MM; sensor spacing is g.xStep.
            val cell = SQUARE_MM * k
            // Machine axes are not physical mm (Y counts 323 per 85 mm); scale each
            // axis using actual sensor spacing.
            kx = SENSOR_MM * k / g.xStep
            ky = SENSOR_MM * k / g.yStep
            side = savedCalibration?.third ?: if (int("me") == 2) -1f else 1f

            root = Entity.create(listOf(Transform(rootPose)))
            base = box(root, Vector3(-w / 2 - 0.02f, -0.015f, -h / 2 - 0.02f), Vector3(w / 2 + 0.02f, 0f, h / 2 + 0.02f), BASE_LOCKED)
            // Grid.
            for (i in 1..2) {
                val x = (i - 1.5f) * cell
                val z = (i - 1.5f) * cell
                box(root, Vector3(x - 0.002f, 0f, -h / 2), Vector3(x + 0.002f, 0.004f, h / 2), GRID)
                box(root, Vector3(-w / 2, 0f, z - 0.002f), Vector3(w / 2, 0.004f, z + 0.002f), GRID)
            }
            // Create each cell at the origin; placeCells positions it later.
            repeat(9) {
                // Tile highlights the detected sensor or last shot.
                val half = cell * 0.44f
                tiles += box(root, Vector3(-half, 0.0005f, -half), Vector3(half, 0.002f, half), IR, Visible(false))
                // Hole for a successful ball drop.
                val hole = cell * 0.2f
                holes += box(root, Vector3(-hole, 0.0005f, -hole), Vector3(hole, 0.003f, hole), HOLE, Visible(false))
                // Sensor at the cell center.
                sensors += sphere(root, Vector3(0f), cell * 0.04f, GRID)
                // Fichas: pieza_x/pieza_o (1 m de ancho, ver tool/modelos_a_glb.py).
                pieces += piece("pieza_x", cell * 0.7f) to piece("pieza_o", cell * 0.6f)
            }
            placeCells()
            // Head at its actual position.
            head = Entity.create(listOf(Transform(Pose(local(cx, cy, HEAD_LIFT), Quaternion())), TransformParent(root)))
            headParts += sphere(head, Vector3(0f, 0f, 0f), HEAD_R, HEAD)
            headParts += box(head, Vector3(-0.003f, 0f, -0.003f), Vector3(0.003f, 0.08f, 0.003f), HEAD)
            // Three-finger claw around the head when the active player uses claw mode.
            for (i in 0 until 3) {
                val a = i * 2 * Math.PI.toFloat() / 3
                val at = Vector3(cos(a) * 0.022f, -0.012f, sin(a) * 0.022f)
                headClaw += box(head, at + Vector3(-0.003f, -0.02f, -0.003f), at + Vector3(0.003f, 0.012f, 0.003f), CLAW, Visible(false))
            }
            // Yellow disk previews the ball's landing position.
            landing = Entity.create(
                listOf(
                    Mesh(Uri.parse("mesh://sphere")),
                    Sphere(0.022f),
                    material(MARK_B),
                    Transform(Pose(Vector3(0f), Quaternion())),
                    Scale(Vector3(1f, 0.12f, 1f)),
                    TransformParent(root),
                    Visible(false),
                ),
            )
            // Shrinking turn timer at the far table edge.
            timeBar = Entity.create(
                listOf(
                    Mesh(Uri.parse("mesh://box")),
                    Box(Vector3(0f, 0f, -0.006f), Vector3(w, 0.008f, 0.006f)),
                    material(PLACED),
                    Transform(Pose(Vector3(-w / 2, 0f, -h / 2 - 0.035f), Quaternion())),
                    Scale(Vector3(0.001f, 1f, 1f)),
                    TransformParent(root),
                ),
            )
            // Three-in-a-row: gold dots span the first and last winning cells.
            repeat(WIN_DOTS) { winDots += sphere(root, Vector3(0f), cell * 0.07f, GOLD, Visible(false)) }
            repeat(CONFETTI) { i ->
                confetti += sphere(root, Vector3(0f), 0.006f, CONFETTI_COLORS[i % CONFETTI_COLORS.size], Visible(false))
            }

            buildSlingshot(h)
            buildClawControls(h)
            buildRemoteControls()

            markA = worldSphere(MARK_R, MARK_A)
            // Touch the cyan button at the local right edge to start/cancel
            // calibration, just like the sign's Calibrate button.
            calibAt = Vector3(w / 2 + 0.12f, 0.02f, h / 2 - 0.03f)
            calibBtn = grabbable(toWorld(calibAt), 0.02f, MARK_A, IsdkGrabMovementType.None)

            // Opponent: head, left/right paws, and ball.
            avatar += model("cabeza_x", HEAD_SIZE)
            avatar += model("pata_x_0", 1f)
            avatar += model("pata_x_0", 1f)
            avatar += worldSphere(BALL_R, BALL)
            for (i in 1..FACES) faces += model("cara_$i", HEAD_SIZE)
            for (hand in paws) repeat(PAW_FRAMES) { hand += model("pata_x_$it", 1f) }

            // Sign and buttons sit to the right without obscuring the opponent;
            // faceUi turns them toward the viewer each frame.
            uiAt = Vector3(w / 2 + 0.24f, 0f, 0f)
            ui = Entity.create(listOf(Transform(Pose(uiAt, Quaternion())), TransformParent(root)))
            Entity.createPanelEntity(R.id.game3d_status, Transform(Pose(Vector3(0f, 0.24f, 0f), Quaternion())), TransformParent(ui))
            Entity.createPanelEntity(R.id.game3d_new, Transform(Pose(Vector3(-0.08f, 0.16f, 0f), Quaternion())), TransformParent(ui))
            Entity.createPanelEntity(R.id.game3d_back, Transform(Pose(Vector3(0.08f, 0.16f, 0f), Quaternion())), TransformParent(ui))
            Entity.createPanelEntity(R.id.game3d_calib, Transform(Pose(Vector3(0f, 0.11f, 0f), Quaternion())), TransformParent(ui))
            built = true
            // Session calibration survives leaving and reopening 3D.
            savedCalibration?.let { (pose, sc, _) ->
                scale = sc
                calibrated = true
                place(pose)
            }
        }

        /// Slingshot at the center of the local edge: slingshot.glb, bands, and ball.
        private fun buildSlingshot(h: Float) {
            // The Blender model (Y up) is about 7.9 units tall; scale and offset it
            // to center the fork tips at x=0.
            val z = h / 2 + SLING_DIST
            val origin = Vector3(-SLING_FORK_MID.x * SLING_SCALE, 0f, z - SLING_FORK_MID.z * SLING_SCALE)
            slingOrigin = origin
            forkL = origin + SLING_TIP_L * SLING_SCALE
            forkR = origin + SLING_TIP_R * SLING_SCALE
            slingRest = (forkL + forkR) * 0.5f - Vector3(0f, 0.02f, 0f)
            slingParts += Entity.create(
                listOf(
                    Mesh(Uri.parse("apk:///models/slingshot.glb")),
                    Transform(Pose(origin, Quaternion())),
                    Scale(Vector3(SLING_SCALE)),
                    TransformParent(root),
                ),
            )
            repeat(2 * BAND_DOTS) { bands += sphere(root, slingRest, 0.004f, BAND) }
            repeat(TRAJ_DOTS) { dots += sphere(root, slingRest, 0.005f, WHITE, Visible(false)) }
            ball = grabbable(toWorld(slingRest), BALL_R, BALL, IsdkGrabMovementType.Direct)
            slingParts += bands
            slingParts += ball
        }

        /// Joystick on the left and button on the right, like an arcade claw machine.
        private fun buildClawControls(h: Float) {
            val z = h / 2 + 0.09f
            val joyBase = Vector3(-0.13f, 0f, z)
            knobRest = joyBase + Vector3(0f, JOY_H, 0f)
            clawParts += box(root, joyBase + Vector3(-0.04f, 0f, -0.04f), joyBase + Vector3(0.04f, 0.015f, 0.04f), CONSOLE)
            repeat(STICK_DOTS) { stick += sphere(root, knobRest, 0.006f, GRID) }
            knob = grabbable(toWorld(knobRest), 0.018f, BUTTON, IsdkGrabMovementType.Direct)
            buttonAt = Vector3(0.13f, 0.025f, z)
            clawParts += box(root, Vector3(0.09f, 0f, z - 0.04f), Vector3(0.17f, 0.015f, z + 0.04f), CONSOLE)
            // The button stays fixed; grabbing or touching it counts as pressing.
            button = grabbable(toWorld(buttonAt), 0.025f, BUTTON, IsdkGrabMovementType.None)
            clawParts += stick
            clawParts += knob
            clawParts += button
        }

        /// Opponent slingshot/console uses the same parts rotated half a turn, with grabbing disabled.
        private fun buildRemoteControls() {
            val across = Entity.create(listOf(Transform(Pose(Vector3(0f), HALF_TURN)), TransformParent(root)))
            remoteSling += Entity.create(
                listOf(
                    Mesh(Uri.parse("apk:///models/slingshot.glb")),
                    Transform(Pose(slingOrigin, Quaternion())),
                    Scale(Vector3(SLING_SCALE)),
                    TransformParent(across),
                    Visible(false),
                ),
            )
            val joyBase = knobRest - Vector3(0f, JOY_H, 0f)
            remoteClaw += box(across, joyBase + Vector3(-0.04f, 0f, -0.04f), joyBase + Vector3(0.04f, 0.015f, 0.04f), CONSOLE, Visible(false))
            remoteClaw += box(across, joyBase + Vector3(-0.003f, 0.015f, -0.003f), knobRest + Vector3(0.003f, 0f, 0.003f), GRID, Visible(false))
            remoteClaw += sphere(across, knobRest, 0.018f, BUTTON, Visible(false))
            remoteClaw += box(across, Vector3(0.09f, 0f, buttonAt.z - 0.04f), Vector3(0.17f, 0.015f, buttonAt.z + 0.04f), CONSOLE, Visible(false))
            remoteClaw += sphere(across, buttonAt, 0.025f, BUTTON, Visible(false))
        }

        /// Opponent mode: 0 slingshot, 1 claw, -1 absent.
        fun showRemoteControls(mode: Int) {
            if (mode == remoteLook) return
            remoteLook = mode
            remoteSling.forEach { it.setComponent(Visible(mode == 0)) }
            remoteClaw.forEach { it.setComponent(Visible(mode == 1)) }
        }

        private lateinit var ui: Entity
        private lateinit var uiAt: Vector3

        /// Rotate sign/buttons horizontally so their front (-Z) faces the viewer's head.
        fun faceUi(viewer: Vector3) {
            val v = toLocal(viewer) - uiAt
            val yaw = atan2(-v.x, -v.z)
            ui.setComponent(Transform(Pose(uiAt, Quaternion(cos(yaw / 2), 0f, sin(yaw / 2), 0f))))
        }

        fun place(pose: Pose) {
            rootPose = pose
            if (built) {
                root.setComponent(Transform(pose))
                root.setComponent(Scale(Vector3(scale)))
            }
            placed = true
        }

        /// Place the table using two calibration touches: world is the index fingertip
        /// and mm is the machine head's x/y. Two points determine rotation, scale, and
        /// translation.
        fun applyCalibration(world: List<Vector3>, mm: List<Pair<Float, Float>>) {
            val la = local(mm[0].first, mm[0].second)
            val lb = local(mm[1].first, mm[1].second)
            val dl = lb - la
            val dw = Vector3(world[1].x - world[0].x, 0f, world[1].z - world[0].z)
            if (dw.length() < 0.05f || dl.length() < 1e-4f) return // Ignore overlapping touch points.
            val yaw = atan2(-dw.z, dw.x) - atan2(-dl.z, dl.x)
            val q = Quaternion(cos(yaw / 2), 0f, sin(yaw / 2), 0f)
            scale = dw.length() / dl.length()
            // Fingertip touches the head top at HEAD_TOUCH_M above the table.
            val up = Vector3(0f, HEAD_TOUCH_M, 0f)
            val t = (world[0] - up - q * (la * scale) + world[1] - up - q * (lb * scale)) * 0.5f
            calibrated = true
            place(Pose(t, q))
            savedCalibration = Triple(rootPose, scale, side)
        }

        /// Render the opponent's PoseFrame, or hide it for null. face selects
        /// expression 1..4 for player 1/2; see tool/cabeza_a_glb.py.
        fun drawAvatar(f: ByteArray?, me: Int, face: (Int) -> Int) {
            val ok = f != null && f.size == POSE_LENGTH && (f[0].toInt() and 0xFF) == POSE_HEADER &&
                (f[1].toInt() and 0xFF) != me
            if (!ok) {
                (avatar + faces).forEach { it.setComponent(Visible(false)) }
                return
            }
            f!!
            fun i16(at: Int) = ((f[at].toInt() and 0xFF) or (f[at + 1].toInt() shl 8)).toShort().toFloat()
            fun point(at: Int) = toWorld(fromBoardMm(i16(at), i16(at + 2), i16(at + 4)))
            val flags = f[29].toInt()
            val id = f[1].toInt() and 0xFF
            // Use the opponent's piece color; the simulation mirror counts as the opponent.
            val other = if (id == 1 || id == 2) id else 3 - me
            if (other != avatarColor) {
                avatarColor = other
                avatar[0].setComponent(Mesh(Uri.parse("apk:///models/cabeza_${tag(other)}.glb")))
                avatar[1].setComponent(Mesh(Uri.parse("apk:///models/pata_${tag(other)}_0.glb")))
                avatar[2].setComponent(Mesh(Uri.parse("apk:///models/pata_${tag(other)}_0.glb")))
            }
            val head = point(2)
            val fwd = rootPose.q * Vector3(side * f[8] / 127f, f[10] / 127f, -side * f[9] / 127f)
            // Use yaw only: the model faces -Z and follows the player's viewing direction.
            val yaw = atan2(-fwd.x, -fwd.z)
            val q = Quaternion(cos(yaw / 2), 0f, sin(yaw / 2), 0f)
            show(avatar[0], head, true, q)
            val expr = face(other)
            for ((i, e) in faces.withIndex()) show(e, head, i + 1 == expr, q)
            show(avatar[1], point(11), flags and 1 != 0, q)
            show(avatar[2], point(17), flags and 2 != 0, q)
            show(avatar[3], point(23), flags and 4 != 0)
        }

        private fun tag(player: Int) = if (player == 1) "x" else "o"

        /// Unparented models/ asset, hidden until needed.
        private fun model(name: String, size: Float, vararg more: ComponentBase) = Entity.create(
            listOf(
                Mesh(Uri.parse("apk:///models/$name.glb")),
                Transform(Pose(Vector3(0f, -10f, 0f), Quaternion())),
                Scale(Vector3(size)),
                Visible(false),
            ) + more,
        )

        /// The closure animation spans one second: t=0 open, t=1 closed. Position each
        /// paw at its hand and select the matching baked closure frame.
        fun drawPaws(hands: List<Pair<Pose, Float>?>, me: Int, dt: Float) {
            if (me != pawColor) {
                pawColor = me
                for (hand in paws) hand.forEachIndexed { f, e -> e.setComponent(Mesh(Uri.parse("apk:///models/pata_${tag(me)}_$f.glb"))) }
            }
            for ((i, h) in hands.withIndex()) {
                val frame = if (h == null) -1 else {
                    val target = h.second
                    pawCurl[i] += (target - pawCurl[i]) * min(1f, dt * 15f)
                    (pawCurl[i] * (PAW_FRAMES - 1) + 0.5f).toInt()
                }
                if (frame != pawFrame[i]) {
                    if (pawFrame[i] >= 0) paws[i][pawFrame[i]].setComponent(Visible(false))
                    if (frame >= 0) paws[i][frame].setComponent(Visible(true))
                    pawFrame[i] = frame
                }
                // The right hand has a further 180-degree longitudinal rotation relative to the left.
                val rot = if (i == 1) RIGHT_FLIP * PAW_ROT else PAW_ROT
                if (h != null) paws[i][frame].setComponent(Transform(Pose(h.first.t, h.first.q * rot)))
            }
        }

        private fun show(e: Entity, world: Vector3, visible: Boolean, q: Quaternion = Quaternion()) {
            e.setComponent(Visible(visible))
            if (visible) e.setComponent(Transform(Pose(world, q)))
        }

        private fun worldSphere(r: Float, color: Color4) = Entity.create(
            listOf(
                Mesh(Uri.parse("mesh://sphere")),
                Sphere(r),
                material(color),
                Transform(Pose(Vector3(0f, -10f, 0f), Quaternion())),
                Visible(false),
            ),
        )

        fun grabbed(e: Entity) = e.getComponent<IsdkGrabbable>().grabState == IsdkGrabState.Grabbed

        fun setGrabbable(e: Entity, on: Boolean) {
            val g = e.getComponent<IsdkGrabbable>()
            if (g.enabled != on) e.setComponent(g.apply { enabled = on })
        }

        fun setLocal(e: Entity, l: Vector3) = e.setComponent(Transform(Pose(l, Quaternion())))
        fun setWorld(e: Entity, l: Vector3) = e.setComponent(Transform(Pose(toWorld(l), rootPose.q)))

        /// Slingshot bands connect each fork tip to the ball.
        fun drawBands(ballLocal: Vector3) {
            for (i in 0 until BAND_DOTS) {
                val t = (i + 1f) / (BAND_DOTS + 1)
                setLocal(bands[i], forkL + (ballLocal - forkL) * t)
                setLocal(bands[BAND_DOTS + i], forkR + (ballLocal - forkR) * t)
            }
        }

        /// Predicted trajectory dots; null hides them.
        fun drawTrajectory(p0: Vector3?, v: Vector3, tLand: Float) {
            for ((i, d) in dots.withIndex()) {
                if (p0 == null) {
                    d.setComponent(Visible(false))
                    continue
                }
                val t = tLand * (i + 1) / TRAJ_DOTS
                setLocal(d, p0 + v * t + Vector3(0f, -GRAVITY * t * t / 2, 0f))
                d.setComponent(Visible(true))
            }
        }

        fun drawStick(knobLocal: Vector3) {
            val base = Vector3(knobRest.x, 0.015f, knobRest.z)
            for ((i, s) in stick.withIndex()) setLocal(s, base + (knobLocal - base) * ((i + 0.5f) / STICK_DOTS))
        }

        fun showHole(cell: Int, on: Boolean) {
            if (cell in 0 until 9) holes[cell].setComponent(Visible(on))
        }

        fun showLanding(l: Vector3?) {
            landing.setComponent(Visible(l != null))
            if (l != null) setLocal(landing, Vector3(l.x, 0.004f, l.z))
        }

        /// Confetti from the board center.
        fun burst() {
            for (i in 0 until CONFETTI) {
                confettiPos[i] = Vector3(0f, 0.05f, 0f)
                confettiVel[i] = Vector3(
                    (Random.nextFloat() - 0.5f) * 0.8f,
                    0.8f + Random.nextFloat() * 0.8f,
                    (Random.nextFloat() - 0.5f) * 0.8f,
                )
                confetti[i].setComponent(Visible(true))
            }
            confettiLeft = 2.5f
        }

        fun animate(dt: Float) {
            if (confettiLeft <= 0f) return
            confettiLeft -= dt
            for (i in 0 until CONFETTI) {
                confettiVel[i] = confettiVel[i] + Vector3(0f, -1.5f * dt, 0f)
                confettiPos[i] = confettiPos[i] + confettiVel[i] * dt
                setLocal(confetti[i], confettiPos[i])
                if (confettiLeft <= 0f) confetti[i].setComponent(Visible(false))
            }
        }

        /// turnClaw means the active player uses claw mode, so the head carries a claw.
        fun update(turnClaw: Boolean) {
            setSide(int("me"))
            val mine = canMove()
            val claw = clawMode()
            setLocal(head, local(num("posX"), num("posY"), headLift) + headSway)

            // Turn-only timer; red for the final five seconds.
            val left = num("timeLeft")
            val frac = if (int("phase") == 1) (left / num("turnSeconds", 20f)).coerceIn(0.001f, 1f) else 0.001f
            val bar = (frac * 100).toInt() + if (left <= 5f) 1000 else 0
            if (bar != barLook) {
                val warn = left <= 5f
                if ((barLook >= 1000) != warn || barLook < 0) timeBar.setComponent(material(if (warn) MISSED else PLACED))
                barLook = bar
                timeBar.setComponent(Scale(Vector3(frac, 1f, 1f)))
            }

            val bl = if (calibrating) 2 else if (mine) 1 else 0
            if (bl != baseLook) {
                baseLook = bl
                base.setComponent(material(when (bl) { 2 -> BASE_CALIB; 1 -> BASE_MINE; else -> BASE_LOCKED }))
            }
            val ml = if (claw) 1 else 0
            if (ml != modeLook) {
                modeLook = ml
                slingParts.forEach { it.setComponent(Visible(!claw)) }
                clawParts.forEach { it.setComponent(Visible(claw)) }
                if (claw) drawTrajectory(null, Vector3(0f), 0f)
            }
            val hl = if (turnClaw) 1 else 0
            if (hl != headClawLook) {
                headClawLook = hl
                headClaw.forEach { it.setComponent(Visible(turnClaw)) }
            }

            val board = game["board"] as? List<*>
            val ir = int("irCell", -1)
            val last = int("lastCell", -1)
            val lastResult = int("lastResult")
            drawWinLine(board)
            for (c in 0 until 9) {
                // Pieces: 0 empty, 1 X, 2 O.
                val p = (board?.getOrNull(c) as? Number)?.toInt() ?: 0
                if (p != pieceLook[c]) {
                    pieceLook[c] = p
                    pieces[c].first.setComponent(Visible(p == 1))
                    pieces[c].second.setComponent(Visible(p == 2))
                }
                // Tile priority: current sensor detection, last shot, then no highlight.
                val look = when {
                    c == ir -> 1
                    c == last && lastResult == 1 -> 2
                    c == last && lastResult > 1 -> 3
                    else -> 0
                }
                if (look != tileLook[c]) {
                    tileLook[c] = look
                    tiles[c].setComponent(Visible(look != 0))
                    if (look != 0) tiles[c].setComponent(material(when (look) { 1 -> IR; 2 -> PLACED; else -> MISSED }))
                }
            }
        }

        /// Highlight the winner's three-in-a-row, if any.
        private fun drawWinLine(board: List<*>?) {
            val w = int("winner")
            val cells = if (w == 1 || w == 2) {
                WIN_LINES.firstOrNull { l -> l.all { (board?.getOrNull(it) as? Number)?.toInt() == w } }
            } else {
                null
            }
            val look = cells?.joinToString() ?: ""
            if (look == winLook) return
            winLook = look
            if (cells == null) {
                winDots.forEach { it.setComponent(Visible(false)) }
                return
            }
            val a = local(geo.cellX(cells[0]), geo.cellY(cells[0]), 0.035f)
            val b = local(geo.cellX(cells[2]), geo.cellY(cells[2]), 0.035f)
            for ((i, d) in winDots.withIndex()) {
                setLocal(d, a + (b - a) * (i.toFloat() / (WIN_DOTS - 1)))
                d.setComponent(Visible(true))
            }
        }

        private fun piece(name: String, size: Float) = Entity.create(
            listOf(
                Mesh(Uri.parse("apk:///models/$name.glb")),
                Transform(Pose(Vector3(0f), Quaternion())),
                Scale(Vector3(size)),
                TransformParent(root),
                Visible(false),
            ),
        )

        /// Sphere grabbable by palm or pinch.
        private fun grabbable(world: Vector3, r: Float, color: Color4, movement: IsdkGrabMovementType) = Entity.create(
            listOf(
                Mesh(Uri.parse("mesh://sphere")),
                Sphere(r),
                material(color),
                Transform(Pose(world, rootPose.q)),
                IsdkSphereCollider(r + 0.015f),
                IsdkGrabbable(enabled = false, movementType = movement),
                IsdkGrabFilter(
                    handContactPinch = true,
                    handDistancePinch = false,
                    handContactPalm = true,
                    handDistancePalm = false,
                ),
            ),
        )

        private fun box(parent: Entity, min: Vector3, max: Vector3, color: Color4, vararg more: ComponentBase): Entity =
            Entity.create(
                listOf(
                    Mesh(Uri.parse("mesh://box")),
                    Box(min, max),
                    material(color),
                    Transform(Pose(Vector3(0f, 0f, 0f), Quaternion())),
                    TransformParent(parent),
                ) + more,
            )

        private fun sphere(parent: Entity, at: Vector3, r: Float, color: Color4, vararg more: ComponentBase): Entity =
            Entity.create(
                listOf(
                    Mesh(Uri.parse("mesh://sphere")),
                    Sphere(r),
                    material(color),
                    Transform(Pose(at, Quaternion())),
                    TransformParent(parent),
                ) + more,
            )
    }

    // Interaction and current shot state.
    private enum class Shot { IDLE, AIMING, FLYING, WAITING, SINKING, BOUNCING }

    /// Each frame: place the table, build when geometry arrives, handle slingshot/claw
    /// input, and update the scene.
    private inner class GameSystem : SystemBase() {
        private var lastSentAt = 0L
        private var lastFrame = 0L
        private var statusText = ""

        // Both modes send fire, wait for ESP phase 2 (resolving), then consume the result.
        private var shot = Shot.IDLE
        private var firedAt = 0L
        private var sawResolving = false
        private var animStart = 0L
        private var resultCell = -1

        // Slingshot ball in table coordinates.
        private var ballPos = Vector3(0f)
        private var ballVel = Vector3(0f)

        // Claw stick movement; release requests a head stop.
        private var steering = false
        private var buttonDown = false
        private var calibDown = false
        private var frameHands: List<Pair<Pose, Float>?> = listOf(null, null)

        // Calibration touches the real head at two ESP-selected points.
        private val touchWorld = arrayOfNulls<Vector3>(2)
        private val touchMm = arrayOfNulls<Pair<Float, Float>>(2)
        private val tipHistory = ArrayDeque<Pair<Long, Vector3>>()
        private var prevCalib = 0
        private var calibAskedAt = 0L

        private fun calibLabel() {
            val label = if (scene3d.calibrating) "Cancel" else "Calibrate"
            calibButton?.let { b -> b.post { b.text = label } }
        }

        /// Has p remained within tol for STILL_NS? h stores time/position history.
        private fun still(h: ArrayDeque<Pair<Long, Vector3>>, p: Vector3, now: Long, tol: Float): Boolean {
            h.addLast(now to p)
            while (h.size > 1 && now - h[1].first >= STILL_NS) h.removeFirst()
            return now - h.first().first >= STILL_NS && h.all { it.second.distanceTo(p) < tol }
        }

        /// Follow ESP calibStep: show the head marker, accept a stationary index
        /// fingertip once the head arrives, send touch, then place the table on
        /// completion.
        private fun calibrate(now: Long) {
            val s = scene3d
            val step = int("calibStep")
            if (step != prevCalib) {
                if (prevCalib == 0) touchWorld.fill(null)
                if (prevCalib == 2 && step == 0 && touchWorld.all { it != null }) {
                    s.applyCalibration(touchWorld.map { it!! }, touchMm.map { it!! })
                    sfx?.play(Sfx.Sound.TURN)
                }
                prevCalib = step
                s.calibrating = step != 0
                s.markA.setComponent(Visible(step != 0))
                tipHistory.clear()
                calibLabel()
            }
            if (step == 0) return
            val x = num("posX")
            val y = num("posY")
            s.markA.setComponent(Transform(Pose(s.toWorld(s.local(x, y, HEAD_TOUCH_M / s.scale)), Quaternion())))
            val bit = 1 shl (int("me") - 1)
            val arrived = abs(x - num("refX")) < 3f && abs(y - num("refY")) < 3f
            val tip = pointingTip()
            if (int("calibWho") and bit == 0 || touchWorld[step - 1] != null || !arrived || tip == null) {
                tipHistory.clear()
                return
            }
            if (!still(tipHistory, tip, now, STILL_M)) return
            touchWorld[step - 1] = tip
            touchMm[step - 1] = num("refX") to num("refY")
            Log.d("Gato3D", "calibration point $step: fingertip $tip, hand ${frameHands.map { it?.first?.t }}")
            sfx?.play(Sfx.Sound.TICK)
            PanelBus.send(this@GameActivity3D, "touch", mapOf("step" to step))
        }

        private fun calibNote(): String {
            val step = int("calibStep")
            if (step == 0) {
                return when {
                    System.nanoTime() - calibAskedAt >= CALIB_WAIT_NS -> ""
                    int("phase") == 1 || int("phase") == 2 -> "Wait for the game to finish before calibrating"
                    else -> "Waiting for the other player to press Calibrate…"
                }
            }
            val bit = 1 shl (int("me") - 1)
            return when {
                int("calibWho") and bit == 0 -> "The other player is calibrating"
                touchWorld[step - 1] != null -> "Point $step ready. Waiting for the other player…"
                abs(num("posX") - num("refX")) >= 3f || abs(num("posY") - num("refY")) >= 3f -> "Calibrating: moving the head to point $step of 2…"
                else -> "Calibrating (point $step of 2): touch the head with your index fingertip and hold still"
            }
        }

        private var remoteClawDown = false
        /// Remote pose mode: 0 slingshot, 1 claw, -1 absent.
        private var remoteMode = -1

        private fun turnClaw() = if (int("turn") == int("me")) clawMode() else remoteMode == 1

        /// Idle head height: raised for claw mode, lowered while the opponent drops their claw.
        private fun idleLift() = if (!turnClaw()) HEAD_LIFT else if (remoteClawDown) CLAW_DOWN else CLAW_UP
        private var lastPoseAt = 0L

        // Announcements and sounds on state transitions.
        private var prevPhase = -1
        private var prevMine = false
        private var prevTick = -1
        private var resultAt = 0L
        private var hadRemote = false
        private var note = ""

        override fun execute() {
            // Before tracking starts, the headset reports a floor-level head; wait for a valid height.
            val viewer = scene.getViewerPose()
            if (viewer.t.y < MIN_HEAD_HEIGHT) return
            // A calibrated table stays at its placed position.
            if (!scene3d.calibrated && (!scene3d.placed || viewer.t.distanceTo(scene3d.rootPose.t) > 2f)) {
                placeInFront(viewer)
            }

            val g = geometry()
            if (g != null && !scene3d.built) scene3d.build(g)
            updateStatus()
            if (!scene3d.built || g == null) return

            val now = System.nanoTime()
            val dt = if (lastFrame == 0L) 0f else min((now - lastFrame) / 1e9f, 0.05f)
            lastFrame = now
            frameHands = handStates()
            scene3d.faceUi(viewer.t)

            val calPressed = scene3d.grabbed(scene3d.calibBtn) || handNear(scene3d.toWorld(scene3d.calibAt), BUTTON_REACH)
            scene3d.setWorld(scene3d.calibBtn, scene3d.calibAt - Vector3(0f, if (calPressed) 0.01f else 0f, 0f))
            if (calPressed && !calibDown) calibToggle = true
            calibDown = calPressed
            if (calibToggle) {
                calibToggle = false
                calibAskedAt = if (scene3d.calibrating) 0L else System.nanoTime()
                PanelBus.send(this@GameActivity3D, "calibrate")
            }
            sendPose(viewer, now)
            val remote = remotePose.takeIf { now - remoteAt < REMOTE_STALE_NS }
            calibrate(now)
            scene3d.drawAvatar(remote, int("me"), ::face)
            val flags = if (remote != null && remote.size == POSE_LENGTH) remote[29].toInt() else -1
            remoteClawDown = flags >= 0 && flags and 8 != 0
            remoteMode = if (flags < 0) -1 else if (flags and 0x40 != 0) 1 else 0
            scene3d.showRemoteControls(remoteMode)
            if (remote != null) hadRemote = true
            val calibText = calibNote()
            note = when {
                calibText.isNotEmpty() -> calibText
                hadRemote && remote == null -> "The other headset is not sending its position"
                else -> ""
            }
            events()
            scene3d.animate(dt)
            scene3d.drawPaws(frameHands, int("me"), dt)

            trackResult(now)
            // Hidden controls for the other mode must not remain grabbable.
            if (clawMode()) {
                scene3d.setGrabbable(scene3d.ball, false)
                claw(g, now)
            } else {
                scene3d.setGrabbable(scene3d.knob, false)
                scene3d.setGrabbable(scene3d.button, false)
                slingshot(g, dt, now)
            }
            scene3d.update(turnClaw())
        }

        // Shot result: turn/countdown/end sounds and winning confetti. Avatar
        // expressions: 4 hit/win, 3 game loss, 2 recent missed shot, otherwise 1.
        private fun face(player: Int): Int {
            if (int("phase") == 3) {
                val w = int("winner")
                return if (w == player) 4 else if (w == 1 || w == 2) 3 else 1
            }
            if (int("lastPlayer") == player && System.nanoTime() - resultAt < FACE_NS) {
                return if (int("lastResult") == 1) 4 else 2
            }
            return 1
        }

        private fun events() {
            val phase = int("phase")
            if (prevPhase == 2 && phase != 2) resultAt = System.nanoTime()
            val mine = canMove()
            if (mine && !prevMine) sfx?.play(Sfx.Sound.TURN)
            val tick = if (mine) num("timeLeft").toInt() else -1
            if (mine && tick in 0..4 && tick != prevTick) sfx?.play(Sfx.Sound.TICK)
            if (phase == 3 && prevPhase != 3 && prevPhase != -1) {
                val w = int("winner")
                if (w == 1 || w == 2) {
                    sfx?.play(Sfx.Sound.WIN)
                    scene3d.burst()
                } else {
                    sfx?.play(Sfx.Sound.DRAW)
                }
            }
            prevPhase = phase
            prevMine = mine
            prevTick = tick
        }

        private fun fire(now: Long) {
            PanelBus.send(this@GameActivity3D, "fire")
            firedAt = now
            sawResolving = false
        }

        /// After firing, wait for phase 2 and a result. Cancel if the ESP never accepts
        /// the shot or takes too long.
        private fun trackResult(now: Long) {
            if (shot != Shot.FLYING && shot != Shot.WAITING) return
            val phase = int("phase")
            val ms = (now - firedAt) / 1_000_000
            if (phase == 2) sawResolving = true
            when {
                sawResolving && phase != 2 -> if (shot == Shot.WAITING) finishShot(now)
                !sawResolving && ms > FIRE_ACCEPT_MS -> reset()
                ms > FIRE_TIMEOUT_MS -> reset()
            }
        }

        private fun finishShot(now: Long) {
            val placed = int("lastResult") == 1
            resultCell = int("lastCell", -1)
            animStart = now
            if (clawMode()) {
                shot = Shot.SINKING // Raise the claw again.
                return
            }
            if (placed && resultCell >= 0) {
                scene3d.showHole(resultCell, true)
                sfx?.play(Sfx.Sound.HOLE)
                shot = Shot.SINKING
            } else {
                // Bounce upward and sideways.
                val side = if (ballPos.x >= 0) 1f else -1f
                ballVel = Vector3(side * 0.5f, 1.1f, -0.25f)
                shot = Shot.BOUNCING
                sfx?.play(Sfx.Sound.BOUNCE)
            }
        }

        private fun reset() {
            if (resultCell >= 0) scene3d.showHole(resultCell, false)
            resultCell = -1
            shot = Shot.IDLE
            scene3d.headLift = idleLift()
        }

        // --- Slingshot ------------------------------------------------------------

        private fun slingshot(g: Geometry, dt: Float, now: Long) {
            val s = scene3d
            s.headSway = Vector3(0f)
            s.headLift = idleLift()
            val mine = canMove()
            s.setGrabbable(s.ball, mine && (shot == Shot.IDLE || shot == Shot.AIMING))
            when (shot) {
                Shot.IDLE -> {
                    if (mine && s.grabbed(s.ball)) {
                        shot = Shot.AIMING
                    } else {
                        s.setWorld(s.ball, s.slingRest)
                        s.drawBands(s.slingRest)
                        s.drawTrajectory(null, Vector3(0f), 0f)
                    }
                }
                Shot.AIMING -> {
                    val (p0, v) = launch(s.toLocal(s.ball.getComponent<Transform>().transform.t))
                    val pulled = (s.slingRest - p0).length() > MIN_PULL
                    if (s.grabbed(s.ball) && mine) {
                        s.drawBands(p0)
                        val tLand = landingTime(p0, v)
                        s.drawTrajectory(if (pulled) p0 else null, v, tLand)
                        s.showLanding(if (pulled) p0 + v * tLand else null)
                        if (pulled) sendMove(g, p0 + v * tLand, now)
                    } else if (pulled && mine) {
                        // On release, the head is already moving to the
                        // landing point; animate flight and fire.
                        sendMove(g, p0 + v * landingTime(p0, v), now, force = true)
                        fire(now)
                        sfx?.play(Sfx.Sound.LAUNCH)
                        s.showLanding(null)
                        ballPos = p0
                        ballVel = v
                        s.drawTrajectory(null, v, 0f)
                        shot = Shot.FLYING
                    } else {
                        shot = Shot.IDLE
                    }
                }
                Shot.FLYING, Shot.BOUNCING -> {
                    ballPos += ballVel * dt
                    ballVel += Vector3(0f, -GRAVITY * dt, 0f)
                    if (shot == Shot.FLYING) {
                        s.drawBands(s.slingRest)
                        if (ballPos.y <= BALL_R) {
                            ballPos = Vector3(ballPos.x, BALL_R, ballPos.z)
                            shot = Shot.WAITING
                        }
                    } else if (now - animStart > BOUNCE_NS) {
                        reset()
                    }
                    s.setWorld(s.ball, ballPos)
                }
                Shot.WAITING -> s.setWorld(s.ball, ballPos)
                Shot.SINKING -> {
                    // Roll toward the hole center and sink.
                    val t = (now - animStart) / 1e9f
                    val hole = s.local(g.cellX(resultCell), g.cellY(resultCell), BALL_R)
                    val p = if (t < 0.3f) ballPos + (hole - ballPos) * (t / 0.3f) else hole - Vector3(0f, (t - 0.3f) * 0.12f, 0f)
                    s.setWorld(s.ball, p)
                    if (t > 1.2f) reset()
                }
            }
        }

        /// Pulled ball in local coordinates to launch position and velocity.
        private fun launch(ball: Vector3): Pair<Vector3, Vector3> {
            val rest = scene3d.slingRest
            var pull = rest - ball
            if (pull.length() > MAX_PULL) pull = pull.normalize() * MAX_PULL
            return (rest - pull) to (pull * LAUNCH_K)
        }

        /// Time for a shot from p0 with velocity v to hit table height y=radius.
        private fun landingTime(p0: Vector3, v: Vector3): Float {
            val h = p0.y - BALL_R
            return (v.y + sqrt(v.y * v.y + 2 * GRAVITY * maxOf(h, 0f))) / GRAVITY
        }

        // Claw machine.

        private fun claw(g: Geometry, now: Long) {
            val s = scene3d
            val mine = canMove()
            val idle = shot == Shot.IDLE
            s.setGrabbable(s.knob, mine && idle)
            s.setGrabbable(s.button, mine && idle)
            val x = num("posX")
            val y = num("posY")

            // While tilted, the joystick advances the head with a setpoint up to
            // JOY_LEAD_MM ahead, proportional to tilt. Release stops at the current
            // position.
            val knobLocal = s.toLocal(s.knob.getComponent<Transform>().transform.t)
            if (mine && idle && s.grabbed(s.knob)) {
                var off = Vector3(knobLocal.x - s.knobRest.x, 0f, knobLocal.z - s.knobRest.z)
                if (off.length() > JOY_R) off = off.normalize() * JOY_R
                val tx = (x + s.side * off.x / JOY_R * JOY_LEAD_MM).coerceIn(g.xMin, g.xMax)
                val ty = (y - s.side * off.z / JOY_R * JOY_LEAD_MM).coerceIn(g.yMin, g.yMax)
                sendMoveMm(tx, ty, now)
                steering = true
                s.drawStick(s.knobRest + off)
            } else {
                if (steering) sendMoveMm(x, y, now, force = true)
                steering = false
                s.setWorld(s.knob, s.knobRest)
                s.drawStick(s.knobRest)
            }

            // Grabbing the button or moving a hand into it presses it.
            val pressed = s.grabbed(s.button) || handNear(s.toWorld(s.buttonAt), BUTTON_REACH)
            s.setWorld(s.button, s.buttonAt - Vector3(0f, if (pressed) 0.01f else 0f, 0f))
            if (pressed && !buttonDown && mine && idle) {
                // Like a physical claw machine, the drop is not exactly on target.
                val wobble = { (Random.nextFloat() - 0.5f) * 2 * CLAW_WOBBLE_MM }
                sendMoveMm((x + wobble()).coerceIn(g.xMin, g.xMax), (y + wobble()).coerceIn(g.yMin, g.yMax), now, force = true)
                fire(now)
                sfx?.play(Sfx.Sound.CLAW)
                animStart = now
                shot = Shot.FLYING
            }
            buttonDown = pressed

            // Claw descends when firing, waits below for the result, then rises;
            // sway decays during descent.
            val t = (now - animStart) / 1e9f
            s.headSway = if (shot == Shot.FLYING || shot == Shot.WAITING) {
                Vector3(sin(t * 14f) * 0.005f, 0f, cos(t * 11f) * 0.004f) * exp(-1.5f * t)
            } else {
                Vector3(0f)
            }
            s.headLift = when (shot) {
                Shot.FLYING, Shot.WAITING -> {
                    if (t >= CLAW_DROP_S) shot = Shot.WAITING
                    CLAW_UP + (CLAW_DOWN - CLAW_UP) * min(t / CLAW_DROP_S, 1f)
                }
                Shot.SINKING -> {
                    if (t >= CLAW_DROP_S) reset()
                    CLAW_DOWN + (CLAW_UP - CLAW_DOWN) * min(t / CLAW_DROP_S, 1f)
                }
                // Show the opponent's claw descending when they fire.
                else -> idleLift()
            }
        }

        /// Tracked left/right hand pose and closure (0 open, 1 fist); null if invisible.
        private fun handStates(): List<Pair<Pose, Float>?> {
            val curls = handCurls()
            val body = Query.where { has(AvatarBody.id) }.eval()
                .filter { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
                .firstOrNull()?.getComponent<AvatarBody>() ?: return listOf(null, null)
            return listOf(body.leftHand, body.rightHand).mapIndexed { i, h ->
                val c = h.tryGetComponent<Controller>()
                val p = h.tryGetComponent<Transform>()?.transform
                if (c != null && p != null && c.isActive && c.type == ControllerType.HAND) {
                    val sig = c.buttonState.toLong() shl 32 or c.directTouchButtonState.toLong()
                    if (lastHandSig[h.id] != sig) {
                        lastHandSig[h.id] = sig
                        Log.d("Gato3D", "hand ${h.id}: buttons ${Integer.toBinaryString(c.buttonState)} touch ${Integer.toBinaryString(c.directTouchButtonState)}")
                    }
                    p to (curls?.get(i) ?: if (c.buttonState and HAND_CLOSED != 0) 1f else 0f)
                } else null
            }
        }

        private val lastHandSig = HashMap<Long, Long>()
        private val bodyPoses = ArrayList<ControllerPose>()
        private val bodySkeleton = ArrayList<SkeletonJoint>()
        private var bodyOk: Boolean? = null

        private fun joint(j: JointType) =
            bodyPoses.getOrNull(j.ordinal)?.takeIf { it.flags and ControllerPose.ValidBits != 0 }?.pose?.t

        /// Pointing index fingertip (index extended, middle curled), right hand first;
        /// null if neither points. Uses this frame's body tracking updated by
        /// handCurls.
        private fun pointingTip(): Vector3? {
            if (bodyOk != true) return null
            val hands = listOf(
                Triple(JointType.RIGHT_HAND_PALM, JointType.RIGHT_HAND_INDEX_TIP, JointType.RIGHT_HAND_MIDDLE_TIP),
                Triple(JointType.LEFT_HAND_PALM, JointType.LEFT_HAND_INDEX_TIP, JointType.LEFT_HAND_MIDDLE_TIP),
            )
            for ((palm, index, middle) in hands) {
                val p = joint(palm) ?: continue
                val i = joint(index) ?: continue
                val m = joint(middle) ?: continue
                if (i.distanceTo(p) > POINT_INDEX_M && m.distanceTo(p) < POINT_MIDDLE_M) return i
            }
            return null
        }

        /// Left/right hand closure from fingertip-to-palm distances in body tracking; null without data.
        private fun handCurls(): FloatArray? {
            val ok = scene.updateBodyTrackingBuffersAtTime(bodyPoses, bodySkeleton, 0L)
            if (ok != bodyOk) {
                bodyOk = ok
                Log.d("Gato3D", "rastreo de cuerpo: $ok (${bodyPoses.size} articulaciones)")
            }
            if (!ok) return null
            fun curl(palm: JointType, tips: List<JointType>): Float? {
                val p = bodyPoses.getOrNull(palm.ordinal)?.takeIf { it.flags and ControllerPose.ValidBits != 0 } ?: return null
                val d = tips.mapNotNull { bodyPoses.getOrNull(it.ordinal)?.takeIf { j -> j.flags and ControllerPose.ValidBits != 0 } }
                    .map { it.pose.t.distanceTo(p.pose.t) }
                if (d.isEmpty()) return null
                return ((CURL_OPEN_M - d.average().toFloat()) / (CURL_OPEN_M - CURL_FIST_M)).coerceIn(0f, 1f)
            }
            val l = curl(JointType.LEFT_HAND_PALM, listOf(JointType.LEFT_HAND_INDEX_TIP, JointType.LEFT_HAND_MIDDLE_TIP, JointType.LEFT_HAND_RING_TIP, JointType.LEFT_HAND_LITTLE_TIP))
            val r = curl(JointType.RIGHT_HAND_PALM, listOf(JointType.RIGHT_HAND_INDEX_TIP, JointType.RIGHT_HAND_MIDDLE_TIP, JointType.RIGHT_HAND_RING_TIP, JointType.RIGHT_HAND_LITTLE_TIP))
            if (l == null && r == null) return null
            return floatArrayOf(l ?: 0f, r ?: 0f)
        }

        /// Tracked left/right hand positions; null when invisible.
        private fun hands(): List<Vector3?> = frameHands.map { it?.first?.t }

        /// Is any tracked hand within r meters of world?
        private fun handNear(world: Vector3, r: Float) = hands().any { it != null && it.distanceTo(world) < r }

        /// Send local head, hands, and ball to the other headset as PoseFrame at 15 Hz.
        private fun sendPose(viewer: Pose, now: Long) {
            if (now - lastPoseAt < POSE_EVERY_NS || game["connected"] != true) return
            lastPoseAt = now
            val s = scene3d
            val f = ByteArray(POSE_LENGTH)
            f[0] = POSE_HEADER.toByte()
            f[1] = int("me").toByte()
            fun put(at: Int, world: Vector3) {
                val (x, y, h) = s.toBoardMm(s.toLocal(world))
                for ((i, v) in listOf(x, y, h).withIndex()) {
                    val n = v.toInt().coerceIn(-32768, 32767)
                    f[at + 2 * i] = n.toByte()
                    f[at + 2 * i + 1] = (n shr 8).toByte()
                }
            }
            put(2, viewer.t)
            val d = s.rootPose.q.inverse() * viewer.forward()
            f[8] = (s.side * d.x * 127).toInt().toByte()
            f[9] = (-s.side * d.z * 127).toInt().toByte()
            f[10] = (d.y * 127).toInt().toByte()
            var flags = 0
            val (left, right) = frameHands
            left?.let { put(11, it.first.t); flags = flags or 1 or (if (it.second >= FIST_CURL) 0x10 else 0) }
            right?.let { put(17, it.first.t); flags = flags or 2 or (if (it.second >= FIST_CURL) 0x20 else 0) }
            if (!clawMode()) {
                put(23, s.ball.getComponent<Transform>().transform.t)
                flags = flags or 4
            } else {
                flags = flags or 0x40 or if (shot == Shot.FLYING || shot == Shot.WAITING) 8 else 0
            }
            f[29] = flags.toByte()
            PanelBus.send(this@GameActivity3D, "pose", mapOf("data" to f))
        }

        // Sending.

        private fun sendMove(g: Geometry, landLocal: Vector3, now: Long, force: Boolean = false) {
            val (x, y) = scene3d.toMm(landLocal)
            sendMoveMm(x.coerceIn(g.xMin, g.xMax), y.coerceIn(g.yMin, g.yMax), now, force)
        }

        private fun sendMoveMm(x: Float, y: Float, now: Long, force: Boolean = false) {
            if (!force && now - lastSentAt < SEND_EVERY_NS) return
            lastSentAt = now
            PanelBus.send(this@GameActivity3D, "move", mapOf("x" to x.toDouble(), "y" to y.toDouble()))
        }

        private fun placeInFront(viewer: Pose) {
            // 50 cm ahead at table height, 45 cm below the eyes.
            val f = viewer.forward()
            var flat = Vector3(f.x, 0f, f.z)
            if (flat.length() < 1e-3f) flat = Vector3(0f, 0f, -1f)
            flat = flat.normalize()
            val pos = viewer.t + flat * 0.55f + Vector3(0f, -0.45f, 0f)
            val toViewer = -flat
            val a = atan2(toViewer.x, toViewer.z)
            scene3d.place(Pose(pos, Quaternion(cos(a / 2), 0f, sin(a / 2), 0f)))
        }

        private fun updateStatus() {
            val base = (game["status"] as? String) ?: "Waiting for 2D panel data…"
            val score = (game["score"] as? String)?.let { "\n$it" } ?: ""
            val text = base + score + if (note.isEmpty()) "" else "\n$note"
            if (text == statusText) return
            statusText = text
            status?.let { tv -> tv.post { tv.text = text } }
        }
    }

    private fun material(c: Color4) = Material().apply {
        baseColor = c
        roughness = 0.5f
        if (c.alpha < 1f) alphaMode = AlphaMode.TRANSLUCENT.mode
    }

    private companion object {
        const val MUSIC_VOLUME = 0.35f
        const val BOARD_MM = 240f          // Physical board: 24 x 24 cm.
        const val SQUARE_MM = 80f          // cuadros de 8 x 8 cm
        const val SENSOR_MM = 85f          // Spacing between adjacent LDR sensors.
        const val HEAD_R = 0.018f          // Head sphere radius in meters.
        const val HEAD_LIFT = 0.03f        // Meters above the table in slingshot mode.
        const val SEND_EVERY_NS = 50_000_000L  // 20 moves per second, matching UART.
        const val MIN_HEAD_HEIGHT = 0.3f

        // Slingshot gravity is reduced to make the flight visible.
        const val SLING_DIST = 0.1f        // Meters from the table edge to the slingshot. Fork tips and their midpoint are in slingshot.glb model units, printed by tool/modelos_a_glb.py.
        val SLING_TIP_L = Vector3(-2.00f, 7.67f, -0.05f)
        val SLING_TIP_R = Vector3(2.00f, 7.85f, 0.05f)
        val SLING_FORK_MID = (SLING_TIP_L + SLING_TIP_R) * 0.5f
        const val SLING_SCALE = 0.018f     // Fork tips about 14 cm above the table.
        const val BALL_R = 0.02f
        const val MAX_PULL = 0.25f         // Maximum band pull distance in meters.
        const val MIN_PULL = 0.03f         // Pulls smaller than this do not count as shots.
        const val LAUNCH_K = 6.5f          // Launch speed (m/s) per meter pulled; helps reach distant rows.
        const val GRAVITY = 2.5f           // m/s²; a longer arc covers all nine cells.
        const val BAND_DOTS = 5
        const val TRAJ_DOTS = 14
        const val BOUNCE_NS = 1_500_000_000L

        // Claw machine.
        const val JOY_H = 0.07f            // Knob height above the base, in meters.
        const val JOY_R = 0.05f            // Joystick tilt in meters for maximum speed.
        const val JOY_LEAD_MM = 30f        // At full tilt, the setpoint is 30 mm ahead of the head.
        const val STICK_DOTS = 6
        const val BUTTON_REACH = 0.045f    // Hand-to-button press distance, in meters.
        const val CLAW_UP = 0.12f
        const val CLAW_DOWN = 0.03f
        const val CLAW_DROP_S = 0.8f
        const val CLAW_WOBBLE_MM = 4f      // Claw drop offset is up to ±4 mm from the target. Calibration and opponent settings follow.
        const val MARK_R = 0.015f
        const val POSE_HEADER = 0xD0
        const val POSE_LENGTH = 30
        const val POSE_EVERY_NS = 66_000_000L     // 15 Hz
        const val REMOTE_STALE_NS = 1_000_000_000L // Hide after one second without poses. After firing, ESP should enter resolving promptly and finish within resolverMax (12 s) plus settling time.
        const val FIRE_ACCEPT_MS = 2000L
        const val FIRE_TIMEOUT_MS = 16000L

        // Translucent base lets the physical machine remain visible underneath.
        val BASE_LOCKED = Color4(0.20f, 0.17f, 0.19f, 0.4f)
        val BASE_MINE = Color4(0.42f, 0.10f, 0.24f, 0.45f)   // tu turno: rosa oscuro
        val BASE_CALIB = Color4(0.10f, 0.55f, 0.20f, 0.45f)  // calibrando: verde
        val GRID = Color4(0.85f, 0.78f, 0.80f, 1f)
        val IR = Color4(0.20f, 0.75f, 0.85f, 1f)
        val PLACED = Color4(0.30f, 0.75f, 0.40f, 1f)
        val MISSED = Color4(0.85f, 0.25f, 0.25f, 1f)
        val HOLE = Color4(0.02f, 0.02f, 0.02f, 1f)
        val HEAD = Color4(1.0f, 0.62f, 0.18f, 1f)
        val CLAW = Color4(0.75f, 0.75f, 0.80f, 1f)
        val BAND = Color4(0.85f, 0.15f, 0.15f, 1f)
        val BALL = Color4(0.95f, 0.20f, 0.20f, 1f)
        val CONSOLE = Color4(0.15f, 0.15f, 0.18f, 1f)
        val BUTTON = Color4(0.95f, 0.10f, 0.20f, 1f)
        val WHITE = Color4(1f, 1f, 1f, 1f)

        // Cat avatar.
        const val HEAD_SIZE = 0.2f         // Head diameter in meters.
        const val FACES = 4
        const val FACE_NS = 4_000_000_000L // Duration of hit/miss faces. Average fingertip-to-palm distances define open/fist states. Assumes typical adult-hand measurements; adjust if full closure is not reached.
        const val CURL_OPEN_M = 0.085f
        const val CURL_FIST_M = 0.045f
        const val FIST_CURL = 0.7f         // Closed-hand threshold in the pose frame. A stationary index fingertip held for the calibration interval counts as a touch.
        const val STILL_NS = 800_000_000L
        const val STILL_M = 0.01f
        // Assumed real head-top height above the table; measure it on the machine to
        // avoid an incorrectly raised/lowered board.
        const val HEAD_TOUCH_M = 0.04f
        // Pointing means the index tip is far from the palm while the middle fingertip stays near it.
        const val POINT_INDEX_M = 0.075f
        const val POINT_MIDDLE_M = 0.065f
        const val CALIB_WAIT_NS = 2_500_000_000L  // ESP waits two seconds for the other player's calibration button.
        const val BODY_TRACKING = "com.oculus.permission.BODY_TRACKING"
        const val PAW_FRAMES = 4           // Must match CUADROS in tool/pata_a_glb.py. Flip the paw longitudinally (fingers -Z to +Z) and rotate 90 degrees to align pads with the palm. Tuned visually on Quest 3.
        val RIGHT_FLIP = Quaternion(0f, 0f, 0f, 1f)
        val PAW_ROT = Quaternion(0.70710677f, 0f, 0f, 0.70710677f) * Quaternion(0f, 0f, 1f, 0f)
        val HAND_CLOSED = ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR or
            ButtonBits.ButtonSqueezeL or ButtonBits.ButtonSqueezeR
        val GOLD = Color4(1.0f, 0.80f, 0.15f, 1f)
        val CONFETTI_COLORS = listOf(GOLD, Color4(0.2f, 0.8f, 1f, 1f), Color4(1f, 0.3f, 0.6f, 1f), Color4(0.4f, 1f, 0.4f, 1f))
        const val CONFETTI = 40
        const val WIN_DOTS = 9
        val WIN_LINES = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6),
        )

        /// App-session table calibration and scale. Closing the app loses it because
        /// headset coordinates change between sessions.
        var savedCalibration: Triple<Pose, Float, Float>? = null   // pose, escala, side
        val HALF_TURN = Quaternion(0f, 0f, 1f, 0f)
        val MARK_A = Color4(0.10f, 0.85f, 0.95f, 1f)
        val MARK_B = Color4(1.0f, 0.85f, 0.10f, 1f)
    }
}
