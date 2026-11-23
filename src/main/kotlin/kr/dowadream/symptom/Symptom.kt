package kr.dowadream.symptom

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 증상 → 진료과 안내 규칙. 진단이 아니라 진료과 안내용 */
@Entity
@Table(name = "symptom_rule")
class SymptomRule(
    @Id
    var id: Long,
    var symptom: String,
    var category: String,
    var deptCode: String?,
    var deptName: String?,
    var isEmergency: Boolean,
    var priority: Int,
)

interface SymptomRuleRepository : JpaRepository<SymptomRule, Long>

data class SymptomResponse(
    val id: Long,
    val name: String,
    val category: String,
    val deptCode: String?,
    val deptName: String?,
    val emergency: Boolean,
)

@RestController
class SymptomController(private val repository: SymptomRuleRepository) {
    /** 증상 칩 목록. category = "자주 찾는 증상"이 03 화면 첫 칩들, 나머지는 04 화면 분류 */
    @GetMapping("/api/symptoms")
    fun list(): List<SymptomResponse> =
        repository.findAll()
            .sortedWith(compareByDescending<SymptomRule> { it.priority }.thenBy { it.id })
            .map { SymptomResponse(it.id, it.symptom, it.category, it.deptCode, it.deptName, it.isEmergency) }
}
