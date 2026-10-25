package com.sesgaro.control_angulos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterActivityLaunchConfigs.BackgroundMode
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/// Base app window. Each Quest activity has its own Flutter engine/isolate; PanelBus messages
/// synchronize state.
abstract class PanelActivity : FlutterActivity() {
    private var channel: MethodChannel? = null

    /// False for panels that do not need plugins such as BLE or system palette access.
    protected open val registerPlugins = true

    // Quest windows are transparent: only cards appear over passthrough.
    override fun getBackgroundMode(): BackgroundMode =
        if (packageName.endsWith(".quest")) BackgroundMode.transparent
        else BackgroundMode.opaque

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PanelBus.started(this)
    }

    override fun onDestroy() {
        PanelBus.stopped(this)
        super.onDestroy()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        if (registerPlugins) super.configureFlutterEngine(flutterEngine)
        channel = PanelBus.attach(flutterEngine, this)
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        channel?.let(PanelBus::detach)
        channel = null
        if (registerPlugins) super.cleanUpFlutterEngine(flutterEngine)
    }
}

/// Relays messages between the Flutter 2D panel (one MethodChannel per engine) and native
/// GameActivity3D. Handles openGame3d (open/foreground) and closeGame3d locally.
object PanelBus {
    private const val NAME = "control_angulos/panels"

    // Resolve by name because GameActivity3D exists only in the quest flavor.
    private const val GAME_3D = "com.sesgaro.control_angulos.GameActivity3D"
    private val panels = mapOf("openGame3d" to GAME_3D)
    private val closers = mapOf("closeGame3d" to GAME_3D)

    private val channels = mutableListOf<MethodChannel>()
    private val listeners = mutableListOf<(String, Map<*, *>) -> Unit>()
    private val running = mutableMapOf<String, WeakReference<Activity>>()
    private val main = Handler(Looper.getMainLooper())

    fun attach(engine: FlutterEngine, activity: Activity): MethodChannel {
        val ch = MethodChannel(engine.dartExecutor.binaryMessenger, NAME)
        ch.setMethodCallHandler { call, result ->
            val args = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
            when (call.method) {
                // Player and mode preferences persist after the app closes.
                "loadPrefs" -> result.success(prefs(activity).all.filterValues { it is Int })
                "savePrefs" -> {
                    prefs(activity).edit().apply {
                        args.forEach { (k, v) -> if (k is String && v is Int) putInt(k, v) }
                    }.apply()
                    result.success(null)
                }
                else -> {
                    dispatch(activity, call.method, args, from = ch)
                    result.success(null)
                }
            }
        }
        channels += ch
        return ch
    }

    private fun prefs(activity: Activity) =
        activity.getSharedPreferences("panel", Activity.MODE_PRIVATE)

    fun detach(ch: MethodChannel) {
        ch.setMethodCallHandler(null)
        channels -= ch
    }

    /// Subscribe a native panel to messages from other panels.
    fun listen(listener: (String, Map<*, *>) -> Unit) {
        listeners += listener
    }

    fun unlisten(listener: (String, Map<*, *>) -> Unit) {
        listeners -= listener
    }

    /// Send from any thread; Flutter channel calls are dispatched to the main thread.
    fun send(activity: Activity, method: String, args: Map<String, Any?> = emptyMap()) {
        main.post { dispatch(activity, method, args, from = null) }
    }

    fun started(activity: Activity) {
        running[activity.javaClass.name] = WeakReference(activity)
    }

    fun stopped(activity: Activity) {
        if (running[activity.javaClass.name]?.get() === activity) running.remove(activity.javaClass.name)
    }

    private fun dispatch(activity: Activity, method: String, args: Map<*, *>, from: MethodChannel?) {
        panels[method]?.let { name ->
            // A new task with a different affinity becomes another Horizon OS panel.
            activity.startActivity(
                Intent().setClassName(activity, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            // Horizon OS gives hand input to one window. Minimize 2D so 3D can
            // receive grabs; BLE stays alive. Back to 2D restores the panel.
            activity.moveTaskToBack(true)
            return
        }
        closers[method]?.let { name ->
            running[name]?.get()?.finishAndRemoveTask()
            return
        }
        for (other in channels.toList()) {
            if (other !== from) other.invokeMethod(method, args)
        }
        if (from != null) for (l in listeners.toList()) l(method, args)
    }
}
