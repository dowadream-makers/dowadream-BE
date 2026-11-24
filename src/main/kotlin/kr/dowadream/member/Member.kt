package kr.dowadream.member

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

enum class AuthProvider { GOOGLE, KAKAO, NAVER }

@Entity
@Table(name = "member")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var nickname: String,
    @Enumerated(EnumType.STRING)
    var authProvider: AuthProvider,
    var providerId: String,
    var largeText: Boolean = false,
    var createdAt: Instant = Instant.now(),
    var lastSeenAt: Instant? = null,
    var deletedAt: Instant? = null,
)

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByAuthProviderAndProviderId(authProvider: AuthProvider, providerId: String): Member?
}
