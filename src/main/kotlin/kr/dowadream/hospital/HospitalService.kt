package kr.dowadream.hospital

import kr.dowadream.waiting.WaitingCalculator
import kr.dowadream.waiting.WaitingStatus
import kr.dowadream.waiting.WaitingStatusRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Service
@Transactional(readOnly = true)
class HospitalService(
    private val hospitalRepository: HospitalRepository,
    private val waitingStatusRepository: WaitingStatusRepository,
) {
    /**
     * 진료과(deptCode)·이름(q)·id 목록(ids)으로 거른다. 조건은 함께 쓸 수 있다.
     * 위치가 있으면 가까운 순(좌표 없는 병원은 뒤로), 없으면 이름순.
     */
    fun list(deptCode: String?, q: String?, ids: List<Long>?, lat: Double?, lng: Double?): List<HospitalResponse> {
        var hospitals = if (deptCode.isNullOrBlank()) hospitalRepository.findAll() else hospitalRepository.findByDeptCode(deptCode)
        if (!q.isNullOrBlank()) hospitals = hospitals.filter { it.name.contains(q.trim()) }
        if (ids != null) hospitals = hospitals.filter { it.id in ids }
        val hospitalIds = hospitals.mapNotNull { it.id }
        if (hospitalIds.isEmpty()) return emptyList()

        val statuses = waitingStatusRepository.findAllById(hospitalIds).associateBy { it.hospitalId }
        val departments = hospitalRepository.findDepartments(hospitalIds).groupBy { it.hospitalId }
        val now = Instant.now()
        val result = hospitals.map { toResponse(it, departments[it.id].orEmpty(), deptCode, statuses[it.id], lat, lng, now) }
        return if (lat != null && lng != null) {
            result.sortedWith(compareBy(nullsLast()) { it.distanceM })
        } else {
            result.sortedBy { it.name }
        }
    }

    fun get(id: Long, lat: Double?, lng: Double?): HospitalResponse {
        val hospital = hospitalRepository.findByIdOrNull(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val departments = hospitalRepository.findDepartments(listOf(id))
        return toResponse(hospital, departments, null, waitingStatusRepository.findByIdOrNull(id), lat, lng, Instant.now())
    }

    private fun toResponse(
        h: Hospital,
        departments: List<DepartmentRow>,
        deptCode: String?,
        status: WaitingStatus?,
        lat: Double?,
        lng: Double?,
        now: Instant,
    ) = HospitalResponse(
        id = h.id!!,
        name = h.name,
        deptName = (departments.find { it.deptCode == deptCode } ?: departments.firstOrNull())?.deptName ?: h.typeName,
        departments = departments.map { it.deptName },
        typeName = h.typeName,
        phone = h.phone,
        address = h.address,
        lat = h.lat,
        lng = h.lng,
        specialistTotal = h.specialistTotal,
        distanceM = if (lat != null && lng != null && h.lat != null && h.lng != null) distanceMeters(lat, lng, h.lat!!, h.lng!!) else null,
        waiting = WaitingCalculator.calculate(
            adminWaiting = status?.adminWaiting,
            adminUpdatedAt = status?.adminUpdatedAt,
            sensorWaiting = status?.sensorWaiting,
            sensorUpdatedAt = status?.sensorUpdatedAt,
            avgConsultMin = h.avgConsultMin,
            doctorCount = h.doctorTotal,
            now = now,
        ),
        demo = h.isDemo,
    )

    /** 두 좌표 사이 직선거리(미터, 하버사인 공식) */
    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return (2 * r * asin(sqrt(a))).roundToInt()
    }
}
