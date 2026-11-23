package kr.dowadream.publicdata

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** 공공 API 한 페이지 응답: 항목(필드 이름 → 값)과 전체 건수 */
data class PublicDataPage(val items: List<Map<String, String>>, val totalCount: Int)

class PublicDataException(message: String) : RuntimeException(message)

/**
 * 공공데이터포털 API의 기본 응답(XML)을 읽는다.
 * 정상: <response><header><resultCode>00</resultCode>...</header><body><items><item>...</item></items><totalCount>..</totalCount></body></response>
 * 키 오류 등: <OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>..</returnReasonCode><returnAuthMsg>..</returnAuthMsg>...
 */
object PublicDataXml {
    fun parse(xml: String): PublicDataPage {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement

        if (root.tagName == "OpenAPI_ServiceResponse") {
            val code = root.text("returnReasonCode")
            val msg = root.text("returnAuthMsg") ?: root.text("errMsg")
            throw PublicDataException("공공 API 오류 $code: $msg")
        }
        val resultCode = root.text("resultCode")
        if (resultCode != null && resultCode != "00" && resultCode != "0000") {
            throw PublicDataException("공공 API 오류 $resultCode: ${root.text("resultMsg")}")
        }

        val itemNodes = root.getElementsByTagName("item")
        val items = (0 until itemNodes.length).map { i ->
            val item = itemNodes.item(i) as Element
            val fields = item.childNodes
            buildMap {
                for (j in 0 until fields.length) {
                    val f = fields.item(j)
                    if (f is Element) put(f.tagName, f.textContent.trim())
                }
            }
        }
        return PublicDataPage(items, root.text("totalCount")?.toIntOrNull() ?: items.size)
    }

    private fun Element.text(tag: String): String? =
        getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifEmpty { null }
}
