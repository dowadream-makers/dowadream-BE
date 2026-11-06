package kr.dowadream.waiting

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** 병원별 '지금' 대기 상태. 센서 값과 관리자 입력값을 다른 칸에 둔다. */
@Entity
@Table(name = "waiting_status")
class WaitingStatus(
    @Id
    var hospitalId: Long,
    var adminWaiting: Int? = null,
    var adminUpdatedAt: Instant? = null,
    var sensorWaiting: Int? = null,
    var sensorUpdatedAt: Instant? = null,
)

interface WaitingStatusRepository : JpaRepository<WaitingStatus, Long>
