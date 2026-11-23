package kr.dowadream.hospital

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface HospitalRepository : JpaRepository<Hospital, Long> {
    @Query(
        value = """
            SELECT h.* FROM hospital h
            WHERE EXISTS (SELECT 1 FROM hospital_department d WHERE d.hospital_id = h.id AND d.dept_code = :deptCode)
        """,
        nativeQuery = true,
    )
    fun findByDeptCode(deptCode: String): List<Hospital>
}
