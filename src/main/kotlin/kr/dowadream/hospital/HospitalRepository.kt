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

    @Query(
        value = """
            SELECT hospital_id AS hospitalId, dept_code AS deptCode, dept_name AS deptName
            FROM hospital_department WHERE hospital_id IN (:hospitalIds) ORDER BY dept_code
        """,
        nativeQuery = true,
    )
    fun findDepartments(hospitalIds: Collection<Long>): List<DepartmentRow>
}

interface DepartmentRow {
    val hospitalId: Long
    val deptCode: String
    val deptName: String
}
