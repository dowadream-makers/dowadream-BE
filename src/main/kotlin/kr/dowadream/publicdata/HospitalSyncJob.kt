package kr.dowadream.publicdata

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 노원구 병원 공공데이터 동기화 (docs/backend-roadmap.md 1-3). 매일 04:00(서울).
 * 1) ① 심평원 병원정보: 노원구 전체 → hospital
 * 2) ② 심평원 상세정보: 병원마다 진료과목·과목별 전문의 수 → hospital_department (오늘 받은 병원은 건너뜀)
 * 3) ③ 국립중앙의료원: 노원구 목록 → ①과 짝지어 hospital_hours
 * 키(PUBLIC_DATA_KEY)가 없으면 건너뛴다.
 */
@Component
class HospitalSyncJob(
    private val client: PublicDataClient,
    private val store: HospitalSyncStore,
    @Value("\${app.public-data.region.sido-name:서울특별시}") private val sidoName: String,
    @Value("\${app.public-data.region.sggu-name:노원구}") private val sgguName: String,
    @Value("\${app.public-data.region.x:127.0565}") private val regionX: Double,
    @Value("\${app.public-data.region.y:37.6543}") private val regionY: Double,
    @Value("\${app.public-data.sync-on-startup:false}") private val syncOnStartup: Boolean,
    @Value("\${app.zone:Asia/Seoul}") private val zone: ZoneId,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    @Scheduled(cron = "\${app.public-data.sync-cron:0 0 4 * * *}", zone = "\${app.zone:Asia/Seoul}")
    fun scheduled() = run()

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        if (syncOnStartup) Thread({ run() }, "public-data-sync").start()
    }

    fun run() {
        if (!client.hasKey) {
            log.info("PUBLIC_DATA_KEY가 없어 공공데이터 동기화를 건너뜁니다.")
            return
        }
        if (!running.compareAndSet(false, true)) {
            log.info("공공데이터 동기화가 이미 실행 중입니다.")
            return
        }
        try {
            if (!syncBasis()) return
            syncDepartments()
            syncHours()
        } finally {
            running.set(false)
        }
    }

    /** 노원구청 좌표 주변 병원 응답에서 sgguCdNm이 노원구인 항목의 시도·시군구 코드를 읽는다 */
    internal fun resolveRegion(): Pair<String, String> {
        val page = client.get(BASIS_PATH, mapOf("xPos" to regionX, "yPos" to regionY, "radius" to 500, "numOfRows" to 50))
        val item = page.items.firstOrNull { it["sgguCdNm"] == sgguName && !it["sgguCd"].isNullOrBlank() }
            ?: throw PublicDataException("$sgguName 지역 코드를 찾지 못했습니다 (응답 ${page.items.size}건)")
        return item["sidoCd"]!! to item["sgguCd"]!!
    }

    private fun syncBasis(): Boolean {
        val started = Instant.now()
        return try {
            val (sidoCd, sgguCd) = resolveRegion()
            val items = client.getAll(BASIS_PATH, mapOf("sidoCd" to sidoCd, "sgguCd" to sgguCd))
            val r = store.upsertBasis(items, sgguCd, Instant.now())
            store.recordRun("HIRA_BASIS", started, r.inserted, r.updated, r.deactivated, 0)
            log.info("심평원 병원정보 {}({}): {}건, 추가 {} 변경 {} 비활성 {}", sgguName, sgguCd, items.size, r.inserted, r.updated, r.deactivated)
            true
        } catch (e: Exception) {
            log.error("심평원 병원정보 동기화 실패", e)
            store.recordRun("HIRA_BASIS", started, error = e.message)
            false
        }
    }

    private fun syncDepartments() {
        val started = Instant.now()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        var done = 0
        var failed = 0
        for (h in store.needingDepartments(todayStart)) {
            try {
                val items = client.getAll(DEPT_PATH, mapOf("ykiho" to h.ykiho!!))
                val departments = items.mapNotNull { item ->
                    val code = item["dgsbjtCd"]?.ifBlank { null } ?: return@mapNotNull null
                    DepartmentItem(code, item["dgsbjtCdNm"] ?: code, item["dgsbjtPrSdrCnt"]?.toIntOrNull())
                }
                store.replaceDepartments(h.id!!, departments, Instant.now())
                done++
            } catch (e: Exception) {
                failed++
                log.warn("진료과목 동기화 실패 {}: {}", h.name, e.message)
            }
        }
        store.recordRun("HIRA_DETAIL", started, updated = done, failed = failed)
        log.info("심평원 진료과목: 성공 {} 실패 {}", done, failed)
    }

    private fun syncHours() {
        val started = Instant.now()
        try {
            val items = client.getAll(NMC_PATH, mapOf("Q0" to sidoName, "Q1" to sgguName))
            val others = items.map {
                HospitalMatcher.Candidate(-1, it["dutyName"].orEmpty(), it["dutyTel1"], it["wgs84Lat"]?.toDoubleOrNull(), it["wgs84Lon"]?.toDoubleOrNull())
            }
            val (matched, unmatched) = HospitalMatcher.match(store.matchCandidates(), others)
            matched.forEach { (hospitalId, index) ->
                store.replaceHours(hospitalId, items[index]["hpid"], parseHours(items[index]))
            }
            val unmatchedNames = unmatched.map { items[it]["dutyName"].orEmpty() }
            store.recordRun("NMC", started, updated = matched.size, failed = unmatched.size, unmatched = unmatchedNames)
            log.info("국립중앙의료원 진료시간: {}건 중 짝 {} / 못 찾음 {}", items.size, matched.size, unmatched.size)
        } catch (e: Exception) {
            log.error("국립중앙의료원 진료시간 동기화 실패", e)
            store.recordRun("NMC", started, error = e.message)
        }
    }

    companion object {
        const val BASIS_PATH = "/B551182/hospInfoServicev2/getHospBasisList"
        const val DEPT_PATH = "/B551182/MadmDtlInfoService2.8/getDgsbjtInfo2.8"
        const val NMC_PATH = "/B552657/HsptlAsembySearchService/getHsptlMdcncListInfoInqire"

        /** dutyTime{1..8}s/c ("0830"/"1830") → 요일 → ("08:30", "18:30"). 2400 넘는 종료는 24:00으로 둔다. */
        fun parseHours(item: Map<String, String>): Map<Int, Pair<String, String>> =
            (1..8).mapNotNull { day ->
                val open = toTime(item["dutyTime${day}s"]) ?: return@mapNotNull null
                val close = toTime(item["dutyTime${day}c"]) ?: return@mapNotNull null
                day to (open to close)
            }.toMap()

        private fun toTime(hhmm: String?): String? {
            val v = hhmm?.trim()?.toIntOrNull() ?: return null
            if (hhmm.trim().length != 4) return null
            val clamped = v.coerceAtMost(2400)
            val h = clamped / 100
            val m = clamped % 100
            if (m >= 60) return null
            return "%02d:%02d".format(h, m)
        }
    }
}
