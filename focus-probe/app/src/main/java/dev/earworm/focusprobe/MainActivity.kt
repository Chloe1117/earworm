package dev.earworm.focusprobe

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 리소스 파일 없이 코드로만 그린 단일 화면. 권한 안내 + 판정 + 최근 로그. */
class MainActivity : Activity() {

    private lateinit var permission: TextView
    private lateinit var report: TextView
    private lateinit var log: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var shownVersion = -1L

    private val refresher = object : Runnable {
        override fun run() {
            if (ProbeLog.version != shownVersion) render()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProbeLog.init(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Focus Probe — 교통 안내 출처 진단"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })
        permission = TextView(this).apply { setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(permission)

        root.addView(button("1. 알림 접근 허용하기") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        report = TextView(this).apply {
            setPadding(0, pad, 0, pad)
            setTextIsSelectable(true)
        }
        root.addView(report)

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("로그 공유") { share() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(button("로그 지우기") { confirmClear() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })

        log = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            setPadding(0, pad, 0, 0)
            setTextIsSelectable(true)
        }
        root.addView(log)

        setContentView(ScrollView(this).apply { addView(root, MATCH_PARENT, WRAP_CONTENT) })
    }

    override fun onResume() {
        super.onResume()
        shownVersion = -1L
        handler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresher)
    }

    private fun render() {
        shownVersion = ProbeLog.version
        val granted = getSystemService(NotificationManager::class.java)!!
            .isNotificationListenerAccessGranted(ComponentName(this, ProbeListenerService::class.java))
        permission.text = if (granted) {
            "● 알림 접근: 허용됨 — 백그라운드에서 기록 중"
        } else {
            "○ 알림 접근: 필요 — 아래 버튼으로 Focus Probe 를 켜세요.\n" +
                "  (스위치가 회색이면: 앱 정보 → ⋮ → '제한된 설정 허용' 후 다시 시도)"
        }
        val lines = ProbeLog.readLines()
        report.text = Diagnosis.report(lines)
        log.text = lines.takeLast(200).reversed().joinToString("\n") { line ->
            // epoch 컬럼은 화면에서 숨긴다.
            line.substringAfter('\t').replace('\t', ' ')
        }
    }

    private fun share() {
        val lines = ProbeLog.readLines()
        val body = Diagnosis.report(lines) + "\n\n--- raw (epoch_ms, time, event, subject, detail) ---\n" +
            lines.joinToString("\n").takeLast(400_000)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Focus Probe 진단 로그")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(send, "진단 로그 공유"))
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage("기록을 모두 지울까요?")
            .setPositiveButton("지우기") { _, _ -> ProbeLog.clear(); render() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }
}
