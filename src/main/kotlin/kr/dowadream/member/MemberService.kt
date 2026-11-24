package kr.dowadream.member

import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MemberService(private val memberRepository: MemberRepository) {

    /** 소셜 로그인 성공 시: 처음이면 가입, 있으면 그대로. 탈퇴했던 사람은 다시 가입한 것으로 본다. */
    @Transactional
    fun signIn(provider: AuthProvider, providerId: String, nickname: String?): Member {
        val now = Instant.now()
        val member = memberRepository.findByAuthProviderAndProviderId(provider, providerId)
            ?: Member(nickname = defaultNickname(nickname), authProvider = provider, providerId = providerId, createdAt = now)
        if (member.deletedAt != null) {
            member.deletedAt = null
            member.createdAt = now
            member.nickname = defaultNickname(nickname)
        }
        member.lastSeenAt = now
        return memberRepository.save(member)
    }

    @Transactional
    fun me(memberId: Long): Member {
        val member = active(memberId)
        member.lastSeenAt = Instant.now()
        return member
    }

    @Transactional
    fun withdraw(memberId: Long) {
        active(memberId).deletedAt = Instant.now()
    }

    private fun active(memberId: Long): Member =
        memberRepository.findByIdOrNull(memberId)?.takeIf { it.deletedAt == null }
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

    private fun defaultNickname(nickname: String?) = nickname?.trim()?.take(20)?.ifBlank { null } ?: "도와드림 이용자"
}
