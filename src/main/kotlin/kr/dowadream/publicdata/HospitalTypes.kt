package kr.dowadream.publicdata

/** 심평원 종별코드(clCd) → 앱 분류 (docs/backend-roadmap.md 1-2) */
object HospitalTypes {
    fun appType(clCd: String?): String = when (clCd) {
        "31" -> "CLINIC"
        "01", "11", "21" -> "HOSPITAL"
        "71", "72", "73", "75" -> "PUBLIC"
        "41", "51" -> "DENTAL"
        "92", "93" -> "KOREAN_MED"
        "28", "29" -> "CARE"
        else -> "OTHER"
    }
}
