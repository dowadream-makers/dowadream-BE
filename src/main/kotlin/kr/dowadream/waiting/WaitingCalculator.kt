package kr.dowadream.waiting

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** 혼잡 단계: 여유 0–2 / 보통 3–5 / 혼잡 6–9 / 포화 10+ (시안 기준, 팀 확정 전) */
enum class CrowdLevel { RELAXED, NORMAL, BUSY, FULL }

enum class WaitingSource { ADMIN, SENSOR }

data class WaitingInfo(
    val people: Int,
    val minutes: Int,
    val level: CrowdLevel,
    val source: WaitingSource,
)

/**
 * 대기 인원·예상 시간 계산 (계획서 5장). DB 없이 테스트할 수 있게 순수 함수로만 둔다.
 */
object WaitingCalculator {
    val ADMIN_FRESH: Duration = Duration.ofMinutes(30)
    val SENSOR_FRESH: Duration = Duration.ofMinutes(3)

    /**
     * 1) 관리자 입력이 30분 이내면 그 값, 2) 아니면 센서 값이 3분 이내면 그 값, 3) 둘 다 없으면 null("정보 없음").
     * TODO: 진료시간(hospital_hours) 동기화가 생기면 "진료 시간이 아니에요" 판단을 추가한다.
     */
    fun calculate(
        adminWaiting: Int?,
        adminUpdatedAt: Instant?,
        sensorWaiting: Int?,
        sensorUpdatedAt: Instant?,
        avgConsultMin: Double,
        doctorCount: Int?,
        now: Instant,
    ): WaitingInfo? {
        val (people, source) = when {
            isFresh(adminWaiting, adminUpdatedAt, ADMIN_FRESH, now) -> adminWaiting!! to WaitingSource.ADMIN
            isFresh(sensorWaiting, sensorUpdatedAt, SENSOR_FRESH, now) -> sensorWaiting!! to WaitingSource.SENSOR
            else -> return null
        }
        return WaitingInfo(people, estimateMinutes(people, avgConsultMin, doctorCount), levelOf(people), source)
    }

    /** 센서의 최근 평균 인원 × 보정계수(보호자 동반 보정)를 반올림 */
    fun sensorPeople(recentAvg: Double, waitingFactor: Double): Int = (recentAvg * waitingFactor).roundToInt()

    /** 예상 분 = ceil(대기 인원 × 평균 진료시간 ÷ max(의사 수, 1) ÷ 5) × 5 */
    fun estimateMinutes(people: Int, avgConsultMin: Double, doctorCount: Int?): Int {
        val doctors = max(doctorCount ?: 1, 1)
        return ceil(people * avgConsultMin / doctors / 5).toInt() * 5
    }

    fun levelOf(people: Int): CrowdLevel = when {
        people <= 2 -> CrowdLevel.RELAXED
        people <= 5 -> CrowdLevel.NORMAL
        people <= 9 -> CrowdLevel.BUSY
        else -> CrowdLevel.FULL
    }

    private fun isFresh(value: Int?, updatedAt: Instant?, limit: Duration, now: Instant): Boolean =
        value != null && updatedAt != null && Duration.between(updatedAt, now) <= limit
}
