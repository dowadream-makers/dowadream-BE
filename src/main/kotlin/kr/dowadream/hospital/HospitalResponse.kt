package kr.dowadream.hospital

import kr.dowadream.waiting.WaitingInfo

data class HospitalResponse(
    val id: Long,
    val name: String,
    /** 카드에 보여 줄 진료과. dept로 찾았으면 그 과, 아니면 첫 진료과, 없으면 종별(의원 등) */
    val deptName: String?,
    val departments: List<String>,
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
    /** 시연용 가상 병원이면 true */
    val demo: Boolean,
)
