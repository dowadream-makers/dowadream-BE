package kr.dowadream.hospital

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hospitals")
class HospitalController(private val hospitalService: HospitalService) {

    /** 예: GET /api/hospitals?dept=01&lat=37.62&lng=127.06 (위치는 거리 계산에만 쓰고 저장하지 않음) */
    @GetMapping
    fun list(
        @RequestParam(required = false) dept: String?,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
    ): List<HospitalResponse> = hospitalService.list(dept, lat, lng)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
    ): HospitalResponse = hospitalService.get(id, lat, lng)
}
