package kr.dowadream.device

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.OffsetDateTime

@Entity
@Table(name = "device")
class Device(
    @Id
    var id: String,
    var hospitalId: Long,
    var roomName: String? = null,
    /** 장치 키 원문은 저장하지 않고 SHA-256 16진수만 저장 */
    var keyHash: String,
    var lastSeenAt: Instant? = null,
    var lastRssi: Int? = null,
    var fwVersion: String? = null,
)

/** 장치가 1분마다 보내는 집계값 원본 */
@Entity
@Table(name = "device_report")
class DeviceReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var deviceId: String,
    var measuredAt: OffsetDateTime,
    var peopleNow: Int,
    var peopleAvg: Double,
    var peopleMax: Int,
    @Column(name = "dwell_over_30s")
    var dwellOver30s: Int,
    var receivedAt: Instant = Instant.now(),
)

interface DeviceRepository : JpaRepository<Device, String>

interface DeviceReportRepository : JpaRepository<DeviceReport, Long> {
    @Query("SELECT AVG(r.peopleAvg) FROM DeviceReport r WHERE r.deviceId = :deviceId AND r.receivedAt >= :since")
    fun averagePeopleSince(deviceId: String, since: Instant): Double?
}
