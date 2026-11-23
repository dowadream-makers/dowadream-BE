package kr.dowadream.publicdata

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Duration

/**
 * 공공데이터포털 API 호출. 요청마다 타임아웃 10초, 실패하면 1·3·5·10·20·30초 뒤 여섯 번 더 시도 (apis.data.go.kr 연결이 자주 끊김, 실제로 7번째에 성공한 적 있음).
 * 키는 Decoding 키를 받아 여기서 한 번만 인코딩한다(+, /, = 포함).
 */
@Component
class PublicDataClient(
    @Value("\${app.public-data.key:}") private val serviceKey: String,
    @Value("\${app.public-data.base-url:https://apis.data.go.kr}") private val baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(10))
                setReadTimeout(Duration.ofSeconds(10))
            },
        )
        .build()

    /** 재시도 사이 대기 (테스트에서 0으로 바꿈) */
    internal var retryDelays: List<Duration> = listOf(1L, 3L, 5L, 10L, 20L, 30L).map { Duration.ofSeconds(it) }

    val hasKey: Boolean get() = serviceKey.isNotBlank()

    /** 한 페이지 호출 */
    fun get(path: String, params: Map<String, Any>): PublicDataPage {
        val uri = buildUri(path, params)
        var last: Exception? = null
        for (attempt in 0..retryDelays.size) {
            if (attempt > 0) Thread.sleep(retryDelays[attempt - 1].toMillis())
            try {
                val body = restClient.get().uri(uri).retrieve().body(String::class.java)
                    ?: throw PublicDataException("빈 응답")
                return PublicDataXml.parse(body)
            } catch (e: Exception) {
                last = e
                log.warn("공공 API 호출 실패 ({}회째) {}: {}", attempt + 1, path, e.message)
            }
        }
        throw last!!
    }

    /** 모든 페이지를 100건씩 받아 합친다 */
    fun getAll(path: String, params: Map<String, Any>, pageSize: Int = 100): List<Map<String, String>> {
        val result = mutableListOf<Map<String, String>>()
        var pageNo = 1
        while (true) {
            val page = get(path, params + mapOf("pageNo" to pageNo, "numOfRows" to pageSize))
            result += page.items
            if (page.items.isEmpty() || result.size >= page.totalCount) return result
            pageNo++
        }
    }

    internal fun buildUri(path: String, params: Map<String, Any>): URI {
        val all = mapOf<String, Any>("serviceKey" to serviceKey) + params
        val builder = UriComponentsBuilder.fromUriString(baseUrl + path)
        all.keys.forEach { builder.queryParam(it, "{$it}") }
        // encode() 후 변수로 넣으면 값 안의 +, /, = 까지 인코딩된다.
        return builder.encode().buildAndExpand(all.mapValues { it.value.toString() }).toUri()
    }
}
