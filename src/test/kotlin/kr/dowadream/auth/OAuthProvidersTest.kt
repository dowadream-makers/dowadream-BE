package kr.dowadream.auth

import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 각 회사 문서의 응답 예시 모양으로 회원 번호·별명을 읽는지 확인 */
class OAuthProvidersTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `구글 userinfo`() {
        val p = OAuthProviders.GOOGLE.parse(mapper.readTree("""{"sub":"1098","name":"홍길동","picture":"x"}"""))
        assertEquals(OAuthProfile("1098", "홍길동"), p)
    }

    @Test
    fun `카카오 id는 숫자, 별명은 kakao_account 아래`() {
        val p = OAuthProviders.KAKAO.parse(mapper.readTree("""{"id":3141592653,"kakao_account":{"profile":{"nickname":"어르신"}}}"""))
        assertEquals(OAuthProfile("3141592653", "어르신"), p)
        val old = OAuthProviders.KAKAO.parse(mapper.readTree("""{"id":7,"properties":{"nickname":"예전 모양"}}"""))
        assertEquals(OAuthProfile("7", "예전 모양"), old)
    }

    @Test
    fun `네이버는 response 아래, 별명 없으면 null`() {
        val p = OAuthProviders.NAVER.parse(mapper.readTree("""{"resultcode":"00","response":{"id":"abc-1"}}"""))
        assertEquals(OAuthProfile("abc-1", null), p)
    }

    @Test
    fun `회원 번호가 없으면 null`() {
        assertNull(OAuthProviders.NAVER.parse(mapper.readTree("""{"resultcode":"024","message":"Authentication failed"}""")))
    }

    @Test
    fun `이름으로 찾기`() {
        assertEquals(OAuthProviders.KAKAO, OAuthProviders.of("kakao"))
        assertNull(OAuthProviders.of("apple"))
    }
}
