package kr.dowadream.auth

import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HS256 JWT 만들기·검사. 라이브러리 없이 필요한 만큼만 (sub, typ, iat, exp).
 * 운영자 로그인도 typ만 바꿔 같이 쓸 예정.
 */
class Jwt(secret: String, private val objectMapper: ObjectMapper) {
    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val dec = Base64.getUrlDecoder()

    init {
        require(secret.toByteArray().size >= 32) { "JWT 비밀값은 32바이트 이상이어야 해요." }
    }

    fun issue(subject: String, type: String, ttl: Duration, now: Instant = Instant.now()): String {
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val claims = mapOf("sub" to subject, "typ" to type, "iat" to now.epochSecond, "exp" to now.plus(ttl).epochSecond)
        val payload = enc.encodeToString(objectMapper.writeValueAsBytes(claims))
        return "$header.$payload.${sign("$header.$payload")}"
    }

    /** 서명·만료·종류가 맞으면 subject, 아니면 null */
    fun verify(token: String, type: String, now: Instant = Instant.now()): String? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        val expected = sign("${parts[0]}.${parts[1]}")
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[2].toByteArray())) return null
        val claims = runCatching { objectMapper.readTree(dec.decode(parts[1])) }.getOrNull() ?: return null
        if (claims.path("typ").asString() != type) return null
        if (claims.path("exp").asLong() <= now.epochSecond) return null
        return claims.path("sub").asString().ifBlank { null }
    }

    private fun sign(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return enc.encodeToString(mac.doFinal(data.toByteArray()))
    }
}
