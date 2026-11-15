package kr.dowadream.publicdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PublicDataTest {
    @Test
    fun `정상 응답에서 항목과 전체 건수를 읽는다`() {
        val xml = """
            <response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
            <body><items>
              <item><yadmNm>권내과의원</yadmNm><ykiho>abc=</ykiho><XPos>127.06</XPos></item>
              <item><yadmNm>위대한내과의원</yadmNm><ykiho>def=</ykiho></item>
            </items><numOfRows>2</numOfRows><pageNo>1</pageNo><totalCount>5</totalCount></body></response>
        """.trimIndent()
        val page = PublicDataXml.parse(xml)
        assertEquals(5, page.totalCount)
        assertEquals(2, page.items.size)
        assertEquals("권내과의원", page.items[0]["yadmNm"])
        assertEquals("127.06", page.items[0]["XPos"])
    }

    @Test
    fun `키 오류 응답은 예외`() {
        val xml = """
            <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
            <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
        """.trimIndent()
        val e = assertFailsWith<PublicDataException> { PublicDataXml.parse(xml) }
        assertTrue(e.message!!.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR"))
    }

    @Test
    fun `resultCode가 00이 아니면 예외`() {
        assertFailsWith<PublicDataException> {
            PublicDataXml.parse("<response><header><resultCode>22</resultCode><resultMsg>LIMITED</resultMsg></header></response>")
        }
    }

    @Test
    fun `Decoding 키의 +, 슬래시, = 와 한글은 한 번만 인코딩된다`() {
        val client = PublicDataClient("a+b/c==", "https://apis.data.go.kr")
        val uri = client.buildUri("/x/y", mapOf("Q1" to "노원구")).toString()
        assertTrue(uri.contains("serviceKey=a%2Bb%2Fc%3D%3D"), uri)
        assertTrue(uri.contains("Q1=%EB%85%B8%EC%9B%90%EA%B5%AC"), uri)
    }

    @Test
    fun `종별 코드를 앱 분류로 바꾼다`() {
        assertEquals("CLINIC", HospitalTypes.appType("31"))
        assertEquals("DENTAL", HospitalTypes.appType("51"))
        assertEquals("KOREAN_MED", HospitalTypes.appType("93"))
        assertEquals("CARE", HospitalTypes.appType("28"))
        assertEquals("OTHER", HospitalTypes.appType(null))
    }

    @Test
    fun `진료시간을 요일별로 읽고 이상한 값은 버린다`() {
        val hours = HospitalSyncJob.parseHours(
            mapOf(
                "dutyTime1s" to "0830", "dutyTime1c" to "1830",
                "dutyTime6s" to "0900", "dutyTime6c" to "1300",
                "dutyTime7s" to "0900", // 종료 없음 → 버림
                "dutyTime8s" to "2200", "dutyTime8c" to "2600", // 자정 넘김 → 24:00
            ),
        )
        assertEquals(mapOf(1 to ("08:30" to "18:30"), 6 to ("09:00" to "13:00"), 8 to ("22:00" to "24:00")), hours)
    }
}
