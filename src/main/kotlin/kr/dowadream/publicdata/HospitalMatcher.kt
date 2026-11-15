package kr.dowadream.publicdata

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 국립중앙의료원(③) 병원을 심평원(①) 병원과 짝짓는다. ③에는 ykiho가 없다.
 * 1) 전화번호 숫자가 같고 그 번호의 병원이 하나뿐 → 짝
 * 2) 정규화한 이름이 같고 좌표가 100m 이내 → 짝
 * 3) 둘 다 아니면 짝 없음
 */
object HospitalMatcher {
    data class Candidate(val id: Long, val name: String, val phone: String?, val lat: Double?, val lng: Double?)

    const val MAX_DISTANCE_M = 100.0

    /** @return 짝지어진 (병원 id → ③ 항목 번호), 짝 못 찾은 ③ 항목 번호 */
    fun match(hospitals: List<Candidate>, others: List<Candidate>): Pair<Map<Long, Int>, List<Int>> {
        val byPhone = hospitals.filter { digits(it.phone) != null }.groupBy { digits(it.phone) }
        val byName = hospitals.groupBy { normalizeName(it.name) }
        val matched = mutableMapOf<Long, Int>()
        val unmatched = mutableListOf<Int>()

        others.forEachIndexed { index, o ->
            val phoneHit = byPhone[digits(o.phone)]?.singleOrNull()
            val nameHit = byName[normalizeName(o.name)].orEmpty().firstOrNull { h ->
                h.lat != null && h.lng != null && o.lat != null && o.lng != null &&
                    distanceMeters(h.lat, h.lng, o.lat, o.lng) <= MAX_DISTANCE_M
            }
            val hit = (phoneHit ?: nameHit)?.takeIf { it.id !in matched }
            if (hit != null) matched[hit.id] = index else unmatched += index
        }
        return matched to unmatched
    }

    fun digits(phone: String?): String? = phone?.filter { it.isDigit() }?.ifEmpty { null }

    /** 공백·괄호 내용 제거, "의료법인" 같은 접두어 제거 */
    fun normalizeName(name: String): String =
        name.replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\s"), "")
            .replace(Regex("^(의료법인|재단법인|사회복지법인|학교법인)"), "")

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
