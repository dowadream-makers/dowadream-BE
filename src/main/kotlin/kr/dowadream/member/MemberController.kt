package kr.dowadream.member

import kr.dowadream.auth.MemberTokens
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class MeResponse(val id: Long, val nickname: String, val provider: AuthProvider, val createdAt: Instant)

@RestController
@RequestMapping("/api/me")
class MemberController(
    private val memberService: MemberService,
    private val memberTokens: MemberTokens,
) {
    @GetMapping
    fun me(@RequestHeader("Authorization") authorization: String): MeResponse {
        val m = memberService.me(memberTokens.memberIdFrom(authorization))
        return MeResponse(m.id!!, m.nickname, m.authProvider, m.createdAt)
    }

    /** 탈퇴: deleted_at만 채운다 */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@RequestHeader("Authorization") authorization: String) {
        memberService.withdraw(memberTokens.memberIdFrom(authorization))
    }
}
