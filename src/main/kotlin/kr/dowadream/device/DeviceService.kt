package kr.dowadream.device

import kr.dowadream.hospital.HospitalRepository
import kr.dowadream.waiting.WaitingCalculator
import kr.dowadream.waiting.WaitingStatus
import kr.dowadream.waiting.WaitingStatusRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.time.Instant

@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val deviceReportRepository: DeviceReportRepository,
    private val hospitalRepository: HospitalRepository,
    private val waitingStatusRepository: WaitingStatusRepository,
) {
    @Transactional
    fun saveReport(deviceId: String, deviceKey: String, req: DeviceReportRequest) {
        val device = deviceRepository.findByIdOrNull(deviceId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (!MessageDigest.isEqual(sha256Hex(deviceKey).toByteArray(), device.keyHash.lowercase().toByteArray())) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }

        val now = Instant.now()
        deviceReportRepository.save(
            DeviceReport(
                deviceId = deviceId,
                measuredAt = req.measuredAt,
                peopleNow = req.peopleNow,
                peopleAvg = req.peopleAvg,
                peopleMax = req.peopleMax,
                dwellOver30s = req.dwellOver30s,
                receivedAt = now,
            ),
        )
        device.lastSeenAt = now
        device.lastRssi = req.rssi
        device.fwVersion = req.fw

        // 최근 3분 평균 인원 × 보정계수 → 병원의 센서 대기 인원
        val hospital = hospitalRepository.findByIdOrNull(device.hospitalId) ?: return
        val recentAvg = deviceReportRepository.averagePeopleSince(deviceId, now.minus(WaitingCalculator.SENSOR_FRESH)) ?: req.peopleAvg
        val status = waitingStatusRepository.findByIdOrNull(device.hospitalId) ?: WaitingStatus(hospitalId = device.hospitalId)
        status.sensorWaiting = WaitingCalculator.sensorPeople(recentAvg, hospital.waitingFactor)
        status.sensorUpdatedAt = now
        waitingStatusRepository.save(status)
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
