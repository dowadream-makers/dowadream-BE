package kr.dowadream.publicdata

import kr.dowadream.hospital.Hospital
import kr.dowadream.hospital.HospitalRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant

data class BasisResult(val inserted: Int, val updated: Int, val deactivated: Int)

data class DepartmentItem(val code: String, val name: String, val specialists: Int?)

/** 동기화 결과를 DB에 쓰는 부분. API 호출은 HospitalSyncJob이 한다. */
@Service
@Transactional
class HospitalSyncStore(
    private val hospitalRepository: HospitalRepository,
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    /**
     * ① 심평원 목록을 ykiho 기준으로 넣거나 고친다.
     * - ykiho가 없는 기존 임시 병원(V2 시드)은 전화번호가 같으면 그 줄을 이어 쓴다(id 유지).
     * - 이번에 안 나온 병원과 이어 쓰지 못한 임시 병원은 active = false (지우지 않음). 시연용 가상 병원은 건드리지 않는다.
     */
    fun upsertBasis(items: List<Map<String, String>>, sgguCd: String, now: Instant): BasisResult {
        val all = hospitalRepository.findAll().filter { !it.isDemo }
        val byYkiho = all.filter { it.ykiho != null }.associateBy { it.ykiho!! }
        val legacyByPhone = all.filter { it.ykiho == null && HospitalMatcher.digits(it.phone) != null }
            .associateBy { HospitalMatcher.digits(it.phone)!! }.toMutableMap()

        var inserted = 0
        var updated = 0
        val seen = mutableSetOf<String>()
        val touched = mutableListOf<Hospital>()
        for (item in items) {
            val ykiho = item["ykiho"]?.ifBlank { null } ?: continue
            val name = item["yadmNm"]?.ifBlank { null } ?: continue
            if (!seen.add(ykiho)) continue
            val h = byYkiho[ykiho]
                ?: HospitalMatcher.digits(item["telno"])?.let { legacyByPhone.remove(it) }
                ?: Hospital(name = name).also { inserted++ }
            if (h.id != null) updated++
            h.ykiho = ykiho
            h.name = name
            h.typeCode = item["clCd"]
            h.typeName = item["clCdNm"]
            h.appType = HospitalTypes.appType(item["clCd"])
            h.phone = item["telno"]?.ifBlank { null }
            h.address = item["addr"]?.ifBlank { null }
            h.lng = item["XPos"]?.toDoubleOrNull()
            h.lat = item["YPos"]?.toDoubleOrNull()
            h.doctorTotal = item["drTotCnt"]?.toIntOrNull()
            // 의과·치과·한방 전문의 수 합
            h.specialistTotal = listOf("mdeptSdrCnt", "detySdrCnt", "cmdcSdrCnt")
                .mapNotNull { item[it]?.toIntOrNull() }.takeIf { it.isNotEmpty() }?.sum()
            h.sgguCd = sgguCd
            h.active = true
            h.syncedAt = now
            touched += h
        }
        hospitalRepository.saveAll(touched)

        val gone = all.filter { it.active && ((it.ykiho != null && it.sgguCd == sgguCd && it.ykiho !in seen) || it.ykiho == null) }
            .filter { it !in touched }
        gone.forEach { it.active = false }
        hospitalRepository.saveAll(gone)
        return BasisResult(inserted, updated, gone.size)
    }

    /** ② 진료과목을 받아야 하는 병원: 오늘(since 이후) 아직 안 받은 활성 병원 */
    @Transactional(readOnly = true)
    fun needingDepartments(since: Instant): List<Hospital> =
        hospitalRepository.findAll().filter { it.active && !it.isDemo && it.ykiho != null && (it.deptSyncedAt?.isBefore(since) ?: true) }

    fun replaceDepartments(hospitalId: Long, departments: List<DepartmentItem>, now: Instant) {
        jdbc.update("DELETE FROM hospital_department WHERE hospital_id = ?", hospitalId)
        departments.distinctBy { it.code }.forEach {
            jdbc.update(
                "INSERT INTO hospital_department (hospital_id, dept_code, dept_name, specialist_count) VALUES (?, ?, ?, ?)",
                hospitalId, it.code, it.name, it.specialists,
            )
        }
        jdbc.update("UPDATE hospital SET dept_synced_at = ? WHERE id = ?", Timestamp.from(now), hospitalId)
    }

    @Transactional(readOnly = true)
    fun matchCandidates(): List<HospitalMatcher.Candidate> =
        hospitalRepository.findAll().filter { it.active && !it.isDemo && it.ykiho != null }
            .map { HospitalMatcher.Candidate(it.id!!, it.name, it.phone, it.lat, it.lng) }

    /** @param hours 요일(1=월 … 7=일, 8=공휴일) → "HH:MM" 시작·종료 */
    fun replaceHours(hospitalId: Long, hpid: String?, hours: Map<Int, Pair<String, String>>) {
        jdbc.update("UPDATE hospital SET hpid = ? WHERE id = ?", hpid, hospitalId)
        jdbc.update("DELETE FROM hospital_hours WHERE hospital_id = ?", hospitalId)
        hours.forEach { (day, time) ->
            jdbc.update(
                "INSERT INTO hospital_hours (hospital_id, day_of_week, open_time, close_time) VALUES (?, ?, ?::time, ?::time)",
                hospitalId, day, time.first, time.second,
            )
        }
    }

    fun recordRun(
        source: String,
        startedAt: Instant,
        inserted: Int? = null,
        updated: Int? = null,
        deactivated: Int? = null,
        failed: Int? = null,
        unmatched: List<String>? = null,
        error: String? = null,
    ) {
        jdbc.update(
            """INSERT INTO sync_run (source, started_at, finished_at, inserted, updated, deactivated, failed, unmatched, error)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)""",
            source, Timestamp.from(startedAt), Timestamp.from(Instant.now()),
            inserted, updated, deactivated, failed, unmatched?.let { objectMapper.writeValueAsString(it) }, error,
        )
    }
}
