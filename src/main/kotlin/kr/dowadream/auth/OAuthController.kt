package kr.dowadream.auth

import jakarta.servlet.http.HttpServletResponse
import kr.dowadream.member.MemberService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * 소셜 로그인 (구글·카카오·네이버, OAuth 2.0 인가 코드 방식).
 * 1) 프론트가 브라우저를 /api/auth/{회사}/start 로 보냄 → 회사 로그인 화면
 * 2) 회사가 /api/auth/{회사}/callback?code=..&state=.. 로 돌려보냄
 * 3) 서버가 code로 회원 번호를 받아 가입/로그인 → 프론트 /login/callback#token=.. 로 보냄
 * 토큰은 주소의 # 뒤에 넣어서 서버 로그·Referer에 남지 않게 한다.
 */
@RestController
@RequestMapping("/api/auth")
class OAuthController(
    private val env: Environment,
    private val memberService: MemberService,
    private val memberTokens: MemberTokens,
    @Value("\${app.auth.backend-url}") private val backendUrl: String,
    @Value("\${app.auth.frontend-url}") private val frontendUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()
    private val restClient = RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(10))
                setReadTimeout(Duration.ofSeconds(10))
            },
        )
        .build()

    /** 키가 설정된 회사만 (프론트가 버튼을 보여 줄지 정함) */
    @GetMapping("/providers")
    fun providers(): List<String> = OAuthProviders.ALL.filter { clientId(it).isNotBlank() }.map { it.provider.name.lowercase() }

    @GetMapping("/{provider}/start")
    fun start(@PathVariable provider: String, response: HttpServletResponse) {
        val spec = enabledSpec(provider)
        val state = ByteArray(24).also { random.nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val url = UriComponentsBuilder.fromUriString(spec.authorizeUrl)
            .queryParam("response_type", "code")
            .queryParam("client_id", clientId(spec))
            .queryParam("redirect_uri", redirectUri(spec))
            .queryParam("state", state)
            .apply { if (spec.scope.isNotBlank()) queryParam("scope", spec.scope) }
            .encode()
            .build()
            .toUriString()
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(state, Duration.ofMinutes(10)).toString())
        response.sendRedirect(url)
    }

    @GetMapping("/{provider}/callback")
    fun callback(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
        @CookieValue(STATE_COOKIE, required = false) savedState: String?,
        response: HttpServletResponse,
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO).toString())
        val result = try {
            val spec = enabledSpec(provider)
            when {
                error != null || code == null -> "error=cancelled"
                state == null || savedState == null || !MessageDigest.isEqual(state.toByteArray(), savedState.toByteArray()) -> "error=state"
                else -> {
                    val profile = fetchProfile(spec, code, state)
                    val member = memberService.signIn(spec.provider, profile.providerId, profile.nickname)
                    "token=${memberTokens.issue(member.id!!)}"
                }
            }
        } catch (e: Exception) {
            log.warn("소셜 로그인 실패 ({}): {}", provider, e.message)
            "error=failed"
        }
        response.sendRedirect("${frontendUrl.trimEnd('/')}/login/callback#$result")
    }

    private fun fetchProfile(spec: OAuthSpec, code: String, state: String): OAuthProfile {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId(spec))
            clientSecret(spec).takeIf { it.isNotBlank() }?.let { add("client_secret", it) }
            add("code", code)
            add("redirect_uri", redirectUri(spec))
            add("state", state)
        }
        val tokenRes = restClient.post().uri(spec.tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode::class.java)
        val accessToken = tokenRes?.path("access_token")?.asString()?.ifBlank { null }
            ?: error("access_token 없음")
        val user = restClient.get().uri(spec.userUrl)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            .retrieve()
            .body(JsonNode::class.java)
        return user?.let(spec.parse) ?: error("회원 번호 없음")
    }

    private fun enabledSpec(name: String): OAuthSpec =
        OAuthProviders.of(name)?.takeIf { clientId(it).isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "지원하지 않는 로그인이에요.")

    private fun key(spec: OAuthSpec) = spec.provider.name.lowercase()
    private fun clientId(spec: OAuthSpec) = env.getProperty("app.oauth.${key(spec)}.client-id", "")
    private fun clientSecret(spec: OAuthSpec) = env.getProperty("app.oauth.${key(spec)}.client-secret", "")
    private fun redirectUri(spec: OAuthSpec) = "${backendUrl.trimEnd('/')}/api/auth/${key(spec)}/callback"

    private fun stateCookie(value: String, maxAge: Duration) = ResponseCookie.from(STATE_COOKIE, value)
        .httpOnly(true)
        .secure(backendUrl.startsWith("https"))
        .sameSite("Lax")
        .path("/api/auth")
        .maxAge(maxAge)
        .build()

    companion object {
        const val STATE_COOKIE = "dw_oauth_state"
    }
}
