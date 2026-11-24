package kr.dowadream.auth

import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JwtTest {
    private val mapper = JsonMapper.builder().build()
    private val jwt = Jwt("a".repeat(32), mapper)
    private val now = Instant.parse("2026-12-07T01:30:00Z")

    @Test
    fun `만든 토큰을 검사하면 subject가 나온다`() {
        val token = jwt.issue("42", "member", Duration.ofDays(30), now)
        assertEquals("42", jwt.verify(token, "member", now.plusSeconds(60)))
    }

    @Test
    fun `만료되면 null`() {
        val token = jwt.issue("42", "member", Duration.ofMinutes(1), now)
        assertNull(jwt.verify(token, "member", now.plusSeconds(61)))
    }

    @Test
    fun `종류가 다르거나 서명이 다르면 null`() {
        val token = jwt.issue("42", "member", Duration.ofDays(1), now)
        assertNull(jwt.verify(token, "admin", now))
        assertNull(Jwt("b".repeat(32), mapper).verify(token, "member", now))
        assertNull(jwt.verify(token.dropLast(2) + "xx", "member", now))
        assertNull(jwt.verify("garbage", "member", now))
    }

    @Test
    fun `비밀값이 짧으면 거부`() {
        assertFailsWith<IllegalArgumentException> { Jwt("short", mapper) }
    }
}
