package dev.earworm.focusprobe

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 알림 접근 권한으로 시스템에 바인딩되어 상시 동작하며 세 가지를 관찰한다.
 * 1) 다른 앱들의 오디오 재생 usage 변화 (길안내 음성이 폰에서 나오는가?)
 * 2) 미디어 세션 상태 (안내 중 YouTube 가 PAUSED 되는가, PLAYING 유지(덕킹)인가? 되감기 지원?)
 * 3) 차량 모드 진입/해제
 * 직접 오디오 포커스를 요청하지 않는다. 요청하면 YouTube 가 멈춰 관찰 대상이 오염된다.
 */
class ProbeListenerService : NotificationListenerService() {

    private lateinit var audio: AudioManager
    private lateinit var sessions: MediaSessionManager
    private lateinit var uiMode: UiModeManager
    private val main = Handler(Looper.getMainLooper())

    private val controllers = mutableMapOf<String, Pair<MediaController, MediaController.Callback>>()
    private val lastStates = mutableMapOf<String, Int>()
    private val usageStartedAt = mutableMapOf<Int, Long>()
    private var carMode: Boolean? = null
    private var lastCounts: String? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            onPlaybackConfigs(configs)
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        syncControllers(list.orEmpty())
    }

    private val carModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = checkCarMode()
    }

    override fun onListenerConnected() {
        ProbeLog.init(this)
        audio = getSystemService(AudioManager::class.java)!!
        sessions = getSystemService(MediaSessionManager::class.java)!!
        uiMode = getSystemService(UiModeManager::class.java)!!
        ProbeLog.write(Events.LISTENER, detail = "connected sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")

        checkCarMode()
        val filter = IntentFilter().apply {
            addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
            addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(carModeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(carModeReceiver, filter)
        }

        audio.registerAudioPlaybackCallback(playbackCallback, main)
        onPlaybackConfigs(audio.activePlaybackConfigurations)

        val me = ComponentName(this, ProbeListenerService::class.java)
        sessions.addOnActiveSessionsChangedListener(sessionsListener, me, main)
        syncControllers(sessions.getActiveSessions(me))
    }

    override fun onListenerDisconnected() {
        runCatching { unregisterReceiver(carModeReceiver) }
        if (::audio.isInitialized) audio.unregisterAudioPlaybackCallback(playbackCallback)
        if (::sessions.isInitialized) sessions.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        controllers.clear()
        ProbeLog.write(Events.LISTENER, detail = "disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 내용은 기록하지 않는다. 어떤 내비 앱이 살아 있는지만 확인.
        if (sbn.packageName in NAV_PACKAGES) ProbeLog.write(Events.NAV_APP_NOTIF, sbn.packageName)
    }

    private fun checkCarMode() {
        val now = uiMode.currentModeType == Configuration.UI_MODE_TYPE_CAR
        if (now != carMode) {
            carMode = now
            ProbeLog.write(Events.CAR_MODE, detail = if (now) "on" else "off")
        }
    }

    private fun onPlaybackConfigs(configs: List<AudioPlaybackConfiguration>) {
        checkCarMode()
        val attrs = configs.map { it.audioAttributes }
        val usages = attrs.map { it.usage }.toSet()
        val now = System.currentTimeMillis()

        // 내비 앱이 안내 음성을 MEDIA usage 로 내면 usage 집합은 그대로다.
        // 그래서 usage 별 플레이어 수 변화도 남겨, 잠깐 늘었다 줄어드는 MEDIA 플레이어를 잡는다.
        val counts = attrs.groupingBy { usageName(it.usage) }.eachCount().toSortedMap()
            .entries.joinToString(",") { "${it.key}:${it.value}" }
        if (counts != lastCounts) {
            lastCounts = counts
            ProbeLog.write(Events.PLAYERS, detail = counts.ifEmpty { "none" })
        }

        for (u in usages - usageStartedAt.keys) {
            usageStartedAt[u] = now
            val contentTypes = attrs.filter { it.usage == u }.joinToString(",") { contentTypeName(it.contentType) }
            ProbeLog.write(
                Events.USAGE_ON, usageName(u),
                "content=$contentTypes playing=${playingPackages().joinToString(",").ifEmpty { "none" }}",
            )
        }
        for (u in usageStartedAt.keys - usages) {
            val dur = now - (usageStartedAt.remove(u) ?: now)
            ProbeLog.write(Events.USAGE_OFF, usageName(u), "dur_ms=$dur")
        }
    }

    private fun syncControllers(list: List<MediaController>) {
        val live = list.associateBy { it.packageName }
        for (pkg in controllers.keys - live.keys) {
            controllers.remove(pkg)?.let { (c, cb) -> c.unregisterCallback(cb) }
            lastStates.remove(pkg)
            ProbeLog.write(Events.SESSION_GONE, pkg)
        }
        for ((pkg, c) in live) {
            if (pkg in controllers) continue
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = logState(pkg, state)
                override fun onSessionDestroyed() {
                    controllers.remove(pkg)?.let { (ctrl, self) -> ctrl.unregisterCallback(self) }
                    lastStates.remove(pkg)
                    ProbeLog.write(Events.SESSION_GONE, pkg)
                }
            }
            c.registerCallback(cb, main)
            controllers[pkg] = c to cb
            ProbeLog.write(Events.SESSION_ADD, pkg, "seek=${canSeek(c.playbackState)}")
            logState(pkg, c.playbackState)
        }
    }

    /** 위치 갱신으로 콜백이 자주 오므로 상태 코드가 바뀔 때만 기록한다. */
    private fun logState(pkg: String, state: PlaybackState?) {
        val code = state?.state ?: PlaybackState.STATE_NONE
        if (lastStates[pkg] == code) return
        lastStates[pkg] = code
        ProbeLog.write(
            Events.MEDIA_STATE, pkg,
            "state=${stateName(code)} pos_ms=${state?.position ?: -1} seek=${canSeek(state)}",
        )
    }

    private fun playingPackages(): List<String> =
        controllers.values.map { it.first }
            .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            .map { it.packageName }

    private fun canSeek(state: PlaybackState?) =
        state != null && state.actions and PlaybackState.ACTION_SEEK_TO != 0L

    companion object {
        val NAV_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.waze",
            "com.skt.tmap.ku",
            "com.skt.skaf.l001mtm091",
            "com.locnall.KimGiSa",
            "com.nhn.android.nmap",
            "com.google.android.projection.gearhead",
        )

        fun usageName(u: Int): String = when (u) {
            AudioAttributes.USAGE_MEDIA -> "MEDIA"
            AudioAttributes.USAGE_VOICE_COMMUNICATION -> "VOICE_CALL"
            AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING -> "CALL_SIGNAL"
            AudioAttributes.USAGE_ALARM -> "ALARM"
            AudioAttributes.USAGE_NOTIFICATION -> "NOTIFICATION"
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "RINGTONE"
            AudioAttributes.USAGE_NOTIFICATION_EVENT -> "NOTIFICATION_EVENT"
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY -> "ACCESSIBILITY"
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> Usages.NAV
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "SONIFICATION"
            AudioAttributes.USAGE_GAME -> "GAME"
            AudioAttributes.USAGE_ASSISTANT -> "ASSISTANT"
            AudioAttributes.USAGE_UNKNOWN -> "UNKNOWN"
            else -> "USAGE_$u"
        }

        fun contentTypeName(c: Int): String = when (c) {
            AudioAttributes.CONTENT_TYPE_SPEECH -> "speech"
            AudioAttributes.CONTENT_TYPE_MUSIC -> "music"
            AudioAttributes.CONTENT_TYPE_MOVIE -> "movie"
            AudioAttributes.CONTENT_TYPE_SONIFICATION -> "sonification"
            else -> "unknown"
        }

        fun stateName(s: Int): String = when (s) {
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_NONE -> "NONE"
            else -> "STATE_$s"
        }
    }
}

object Events {
    const val LISTENER = "LISTENER"
    const val CAR_MODE = "CAR_MODE"
    const val USAGE_ON = "USAGE_ON"
    const val USAGE_OFF = "USAGE_OFF"
    const val PLAYERS = "PLAYERS"
    const val SESSION_ADD = "SESSION_ADD"
    const val SESSION_GONE = "SESSION_GONE"
    const val MEDIA_STATE = "MEDIA_STATE"
    const val NAV_APP_NOTIF = "NAV_APP_NOTIF"
}

object Usages {
    const val NAV = "NAV_GUIDANCE"

    /** 미디어 재생을 끊거나 덮을 수 있는 "끼어드는 소리"로 보지 않는 usage. */
    val BACKGROUND = setOf("MEDIA", "GAME", "UNKNOWN")
}
