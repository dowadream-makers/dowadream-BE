package kr.dowadream.waiting

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaitingCalculatorTest {
    private val now = Instant.parse("2026-12-07T01:30:00Z")
    private fun minutesAgo(m: Long) = now.minusSeconds(m * 60)

    @Test
    fun `관리자 입력이 30분 이내면 관리자 값을 쓴다`() {
        val info = WaitingCalculator.calculate(4, minutesAgo(30), 9, minutesAgo(1), 5.0, 1, now)!!
        assertEquals(4, info.people)
        assertEquals(WaitingSource.ADMIN, info.source)
    }

    @Test
    fun `관리자 입력이 30분을 넘으면 센서 값을 쓴다`() {
        val info = WaitingCalculator.calculate(4, minutesAgo(31), 7, minutesAgo(3), 5.0, 1, now)!!
        assertEquals(7, info.people)
        assertEquals(WaitingSource.SENSOR, info.source)
    }

    @Test
    fun `센서 값이 3분을 넘고 관리자 입력도 없으면 정보 없음`() {
        assertNull(WaitingCalculator.calculate(null, null, 7, minutesAgo(4), 5.0, 1, now))
        assertNull(WaitingCalculator.calculate(null, null, null, null, 5.0, 1, now))
    }

    @Test
    fun `예상 시간은 5분 단위로 올린다`() {
        assertEquals(20, WaitingCalculator.estimateMinutes(4, 5.0, 1))
        assertEquals(25, WaitingCalculator.estimateMinutes(3, 7.0, 1)) // 21분 → 25분
        assertEquals(10, WaitingCalculator.estimateMinutes(4, 5.0, 2)) // 의사 2명
        assertEquals(20, WaitingCalculator.estimateMinutes(4, 5.0, 0)) // 의사 수 0은 1로 본다
        assertEquals(20, WaitingCalculator.estimateMinutes(4, 5.0, null))
        assertEquals(0, WaitingCalculator.estimateMinutes(0, 5.0, 1))
    }

    @Test
    fun `혼잡 단계 경계값`() {
        assertEquals(CrowdLevel.RELAXED, WaitingCalculator.levelOf(0))
        assertEquals(CrowdLevel.RELAXED, WaitingCalculator.levelOf(2))
        assertEquals(CrowdLevel.NORMAL, WaitingCalculator.levelOf(3))
        assertEquals(CrowdLevel.NORMAL, WaitingCalculator.levelOf(5))
        assertEquals(CrowdLevel.BUSY, WaitingCalculator.levelOf(6))
        assertEquals(CrowdLevel.BUSY, WaitingCalculator.levelOf(9))
        assertEquals(CrowdLevel.FULL, WaitingCalculator.levelOf(10))
    }

    @Test
    fun `센서 인원은 평균 × 보정계수를 반올림`() {
        assertEquals(3, WaitingCalculator.sensorPeople(3.6, 0.8)) // 2.88
        assertEquals(4, WaitingCalculator.sensorPeople(5.0, 0.8))
        assertEquals(0, WaitingCalculator.sensorPeople(0.0, 0.8))
    }
}
