package kr.dowadream.hospital

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "hospital")
class Hospital(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var ykiho: String? = null,
    var hpid: String? = null,
    var name: String,
    var typeCode: String? = null,
    var typeName: String? = null,
    var phone: String? = null,
    var address: String? = null,
    var lat: Double? = null,
    var lng: Double? = null,
    var doctorTotal: Int? = null,
    var specialistTotal: Int? = null,
    var avgConsultMin: Double = 5.0,
    var waitingFactor: Double = 0.8,
    var isDemo: Boolean = false,
    var syncedAt: Instant? = null,
)
