package kr.dowadream.device

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.PositiveOrZero
import java.time.OffsetDateTime

/** 장치가 보내는 JSON (계획서 6-1). 필드 이름은 장치 쪽에 맞춰 snake_case */
data class DeviceReportRequest(
    @JsonProperty("measured_at") val measuredAt: OffsetDateTime,
    @field:PositiveOrZero @JsonProperty("people_now") val peopleNow: Int,
    @field:PositiveOrZero @JsonProperty("people_avg") val peopleAvg: Double,
    @field:PositiveOrZero @JsonProperty("people_max") val peopleMax: Int,
    @field:PositiveOrZero @JsonProperty("dwell_over_30s") val dwellOver30s: Int,
    val rssi: Int? = null,
    val fw: String? = null,
    val seq: Long? = null,
)

data class DeviceReportResponse(
    val ok: Boolean,
    @JsonProperty("demo_mode") val demoMode: Boolean,
)
