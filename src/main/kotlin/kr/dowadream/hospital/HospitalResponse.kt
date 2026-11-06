package kr.dowadream.hospital

import kr.dowadream.waiting.WaitingInfo

data class HospitalResponse(
    val id: Long,
    val name: String,
    val typeName: String?,
    val phone: String?,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    val specialistTotal: Int?,
    /** 요청에 lat/lng가 있고 병원 좌표가 있을 때만 값이 있음 */
    val distanceM: Int?,
    /** null이면 "정보 없음" */
    val waiting: WaitingInfo?,
)
