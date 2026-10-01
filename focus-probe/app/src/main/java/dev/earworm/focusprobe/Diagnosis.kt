package dev.earworm.focusprobe

/**
 * ProbeLog 의 TSV 를 다시 읽어 Go / No-Go 판정을 만든다.
 * 핵심 질문: 운전 중 들리는 교통 안내가 "폰에서" 나오는가? (그래야 앱으로 해결 가능)
 */
object Diagnosis {

    private const val SHORT_BURST_MS = 30_000L

    private class Interrupt(val usage: String, val start: Long, val playingAtStart: List<String>) {
        var end: Long? = null
        val paused = mutableSetOf<String>()
    }

    fun report(lines: List<String>): String {
        if (lines.isEmpty()) return "아직 기록 없음. 알림 접근을 허용한 뒤 YouTube 를 틀고 운전해 보세요."

        val interrupts = mutableListOf<Interrupt>()
        val open = mutableMapOf<String, Interrupt>()
        val seek = sortedMapOf<String, Boolean>()
        var carModeSeen = false
        var mediaCount = 0
        var burstStart: Long? = null
        val bursts = mutableListOf<Long>()

        for (line in lines) {
            val cols = line.split('\t')
            if (cols.size < 5) continue
            val ts = cols[0].toLongOrNull() ?: continue
            val event = cols[2]
            val subject = cols[3]
            val kv = parseDetail(cols[4])

            when (event) {
                Events.CAR_MODE -> if (cols[4] == "on") carModeSeen = true
                Events.USAGE_ON -> if (subject !in Usages.BACKGROUND) {
                    val playing = kv["playing"].orEmpty().split(',').filter { it.isNotBlank() && it != "none" }
                    Interrupt(subject, ts, playing).also { open[subject] = it; interrupts += it }
                }
                Events.USAGE_OFF -> open.remove(subject)?.end = ts
                Events.SESSION_ADD -> seek[subject] = kv["seek"] == "true"
                Events.MEDIA_STATE -> {
                    seek[subject] = kv["seek"] == "true"
                    if (kv["state"] == "PAUSED") open.values.forEach { it.paused += subject }
                }
                Events.PLAYERS -> {
                    val n = countOf(cols[4], "MEDIA")
                    if (n > mediaCount && mediaCount > 0 && burstStart == null) burstStart = ts
                    if (n <= 1 && burstStart != null) {
                        val d = ts - burstStart!!
                        if (d < SHORT_BURST_MS) bursts += d
                        burstStart = null
                    }
                    mediaCount = n
                }
            }
        }

        val first = lines.first().split('\t').getOrNull(1).orEmpty()
        val last = lines.last().split('\t').getOrNull(1).orEmpty()
        val nav = interrupts.filter { it.usage == Usages.NAV }
        val withMedia = interrupts.filter { it.playingAtStart.isNotEmpty() }
        val paused = withMedia.count { i -> i.paused.any { it in i.playingAtStart } }
        val ducked = withMedia.size - paused

        return buildString {
            appendLine("■ 기간: $first ~ $last (${lines.size}줄)")
            appendLine("■ 차량 모드 감지: ${if (carModeSeen) "예" else "아니오"}")
            appendLine("■ 길안내 음성(NAV_GUIDANCE): ${nav.size}회 ${durationStats(nav)}")

            val others = interrupts.filter { it.usage != Usages.NAV }.groupBy { it.usage }
            if (others.isNotEmpty()) {
                appendLine("■ 그 밖에 끼어든 소리:")
                others.forEach { (u, list) -> appendLine("   - $u ${list.size}회 ${durationStats(list)}") }
            }
            if (bursts.isNotEmpty()) {
                appendLine("■ 짧게 나타난 추가 MEDIA 플레이어: ${bursts.size}회 (평균 ${bursts.average().toLong() / 1000.0}초)")
                appendLine("   → 내비가 안내를 MEDIA 채널로 내는 경우일 수 있음")
            }
            appendLine("■ 미디어 재생 중 끼어든 경우: ${withMedia.size}회 → 일시정지 $paused / 덕킹(계속 재생) $ducked")
            if (seek.isNotEmpty()) {
                appendLine("■ 되감기(seekTo) 지원:")
                seek.forEach { (pkg, ok) -> appendLine("   - $pkg ${if (ok) "가능" else "불가"}") }
            }
            appendLine()
            appendLine(verdict(nav.size, interrupts.size, bursts.size, carModeSeen))
        }
    }

    private fun verdict(nav: Int, all: Int, bursts: Int, carModeSeen: Boolean): String = when {
        nav > 0 ->
            "판정: GO ✅ 안내가 폰에서 길안내 채널로 나옵니다. 감지 → 일시정지 → 되감기 → 재개 방식으로 해결 가능."
        all > 0 ->
            "판정: 조건부 GO ⚠️ 길안내 채널은 아니지만 폰에서 끼어드는 소리가 있습니다. 위 usage 로 감지하도록 설계하면 됩니다."
        bursts > 0 ->
            "판정: 조건부 GO ⚠️ 안내가 MEDIA 채널로 나오는 것으로 보입니다. 플레이어 수 변화로 감지해야 하며 정확도 검증이 필요합니다."
        carModeSeen ->
            "판정: NO-GO 가능성 ❌ 운전 중 안내가 들렸는데도 폰에서 잡힌 소리가 없다면 차량 순정 내비가 내는 안내입니다. 폰 앱으로는 해결 불가 → 순정 내비 음성 설정에서 꺼야 합니다."
        else ->
            "판정: 데이터 부족. 안드로이드 오토를 연결해 YouTube 를 들으며 안내가 몇 번 나올 때까지 운전해 보세요."
    }

    private fun durationStats(list: List<Interrupt>): String {
        val durs = list.mapNotNull { i -> i.end?.let { it - i.start } }
        if (durs.isEmpty()) return ""
        return "(평균 %.1f초, 최대 %.1f초)".format(durs.average() / 1000, durs.max() / 1000.0)
    }

    private fun parseDetail(detail: String): Map<String, String> =
        detail.split(' ').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i > 0) part.substring(0, i) to part.substring(i + 1) else null
        }.toMap()

    private fun countOf(counts: String, usage: String): Int =
        counts.split(',').firstOrNull { it.startsWith("$usage:") }?.substringAfter(':')?.toIntOrNull() ?: 0
}
