package dev.earworm.focusprobe

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 진단 이벤트를 TSV 한 줄씩 파일에 쌓는다.
 * 컬럼: epoch_ms, local_time, event, subject, detail
 * 파일이 단일 진실 원천이라 프로세스가 죽어도 Diagnosis 를 다시 계산할 수 있다.
 */
object ProbeLog {
    private const val FILE_NAME = "probe_log.tsv"
    private const val MAX_BYTES = 2_000_000L

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var file: File? = null

    /** 쓰기마다 증가. 화면은 이 값이 바뀔 때만 다시 그린다. */
    @Volatile
    var version = 0L
        private set

    @Synchronized
    fun init(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    @Synchronized
    fun write(event: String, subject: String = "-", detail: String = "") {
        val f = file ?: return
        if (f.length() > MAX_BYTES) {
            // 진단용이라 오래된 절반은 버려도 된다.
            val lines = f.readLines()
            f.writeText(lines.drop(lines.size / 2).joinToString("\n", postfix = "\n"))
        }
        val now = System.currentTimeMillis()
        val clean = detail.replace('\t', ' ').replace('\n', ' ')
        f.appendText("$now\t${timeFormat.format(Date(now))}\t$event\t$subject\t$clean\n")
        version++
    }

    @Synchronized
    fun readLines(): List<String> {
        val f = file ?: return emptyList()
        return if (f.exists()) f.readLines() else emptyList()
    }

    @Synchronized
    fun clear() {
        file?.writeText("")
        version++
    }
}
