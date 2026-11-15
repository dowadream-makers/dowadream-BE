package kr.dowadream.publicdata

import kr.dowadream.publicdata.HospitalMatcher.Candidate
import kotlin.test.Test
import kotlin.test.assertEquals

class HospitalMatcherTest {
    private val hospitals = listOf(
        Candidate(1, "권내과의원", "02-972-8375", 37.6500, 127.0600),
        Candidate(2, "의료법인 튼튼병원", "02-111-2222", 37.6600, 127.0700),
        Candidate(3, "같은번호의원A", "02-333-4444", 37.6400, 127.0500),
        Candidate(4, "같은번호의원B", "02-333-4444", 37.6410, 127.0510),
    )

    @Test
    fun `전화번호 숫자가 같으면 짝`() {
        val (matched, unmatched) = HospitalMatcher.match(hospitals, listOf(Candidate(-1, "권내과", "029728375", null, null)))
        assertEquals(mapOf(1L to 0), matched)
        assertEquals(emptyList(), unmatched)
    }

    @Test
    fun `이름이 같고 100m 이내면 짝, 멀면 짝 없음`() {
        val near = Candidate(-1, "튼튼병원", "02-999-9999", 37.6605, 127.0700) // 약 56m
        val far = Candidate(-1, "튼튼병원", "02-999-9999", 37.6620, 127.0700) // 약 220m
        assertEquals(mapOf(2L to 0), HospitalMatcher.match(hospitals, listOf(near)).first)
        assertEquals(listOf(0), HospitalMatcher.match(hospitals, listOf(far)).second)
    }

    @Test
    fun `같은 전화번호 병원이 둘이면 전화로는 짝짓지 않는다`() {
        val (matched, _) = HospitalMatcher.match(hospitals, listOf(Candidate(-1, "다른이름", "02-333-4444", null, null)))
        assertEquals(emptyMap(), matched)
    }

    @Test
    fun `이름 정규화는 공백·괄호·법인 접두어를 지운다`() {
        assertEquals("튼튼병원", HospitalMatcher.normalizeName("의료법인 튼튼 병원(노원)"))
    }
}
