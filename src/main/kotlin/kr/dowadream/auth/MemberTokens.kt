package kr.dowadream.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * 회원 로그인 토큰. 어르신이 자주 다시 로그인하지 않도록 30일.
 * 비밀값(AUTH_JWT_SECRET)이 없으면 켤 때마다 임시 값을 만든다 (로컬 개발용, 서버를 다시 켜면 다시 로그인).
 */
@Component
class MemberTokens(
    @Value("\${app.auth.jwt-secret:}") secret: String,
    objectMapper: ObjectMapper,
) {
    private val jwt = Jwt(secret.ifBlank { temporarySecret() }, objectMapper)

    fun issue(memberId: Long): String = jwt.issue(memberId.toString(), TYPE, TTL)

    /** "Bearer <토큰>" → 회원 id. 틀리면 401 */
    fun memberIdFrom(authorization: String): Long {
        val token = authorization.removePrefix("Bearer ").trim()
        return jwt.verify(token, TYPE)?.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    private fun temporarySecret(): String {
        LoggerFactory.getLogger(javaClass).warn("AUTH_JWT_SECRET이 없어 임시 비밀값을 씁니다. 서버를 다시 켜면 회원이 다시 로그인해야 해요.")
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        const val TYPE = "member"
        val TTL: Duration = Duration.ofDays(30)
    }
}
