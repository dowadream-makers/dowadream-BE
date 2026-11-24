package kr.dowadream.auth

import kr.dowadream.member.AuthProvider
import tools.jackson.databind.JsonNode

/** 회사가 준 회원 번호와 별명 */
data class OAuthProfile(val providerId: String, val nickname: String?)

/** 소셜 로그인 회사별 주소와 응답 읽는 법 */
data class OAuthSpec(
    val provider: AuthProvider,
    val authorizeUrl: String,
    val tokenUrl: String,
    val userUrl: String,
    /** 이름·이메일·전화번호는 요청하지 않고 회원 번호와 별명만 */
    val scope: String,
    val parse: (JsonNode) -> OAuthProfile?,
)

object OAuthProviders {
    val GOOGLE = OAuthSpec(
        provider = AuthProvider.GOOGLE,
        authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenUrl = "https://oauth2.googleapis.com/token",
        userUrl = "https://openidconnect.googleapis.com/v1/userinfo",
        scope = "openid profile",
        parse = { j -> text(j.path("sub"))?.let { OAuthProfile(it, text(j.path("name"))) } },
    )

    val KAKAO = OAuthSpec(
        provider = AuthProvider.KAKAO,
        authorizeUrl = "https://kauth.kakao.com/oauth/authorize",
        tokenUrl = "https://kauth.kakao.com/oauth/token",
        userUrl = "https://kapi.kakao.com/v2/user/me",
        scope = "profile_nickname",
        parse = { j ->
            text(j.path("id"))?.let {
                OAuthProfile(it, text(j.path("kakao_account").path("profile").path("nickname")) ?: text(j.path("properties").path("nickname")))
            }
        },
    )

    val NAVER = OAuthSpec(
        provider = AuthProvider.NAVER,
        authorizeUrl = "https://nid.naver.com/oauth2.0/authorize",
        tokenUrl = "https://nid.naver.com/oauth2.0/token",
        userUrl = "https://openapi.naver.com/v1/nid/me",
        scope = "", // 네이버는 개발자센터에서 고른 항목(별명)만 준다
        parse = { j ->
            val r = j.path("response")
            text(r.path("id"))?.let { OAuthProfile(it, text(r.path("nickname")) ?: text(r.path("name"))) }
        },
    )

    val ALL = listOf(GOOGLE, KAKAO, NAVER)

    fun of(name: String): OAuthSpec? = ALL.find { it.provider.name.equals(name, ignoreCase = true) }

    /** 숫자 id(카카오)도 문자열로 */
    private fun text(node: JsonNode): String? = when {
        node.isNumber -> node.asLong().toString()
        node.isString -> node.asString().ifBlank { null }
        else -> null
    }
}
