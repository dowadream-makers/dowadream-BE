package kr.dowadream.device

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/devices")
class DeviceController(private val deviceService: DeviceService) {

    /** ESP32가 1분마다 보내는 집계값. 이 요청이 곧 하트비트 */
    @PostMapping("/{deviceId}/reports")
    fun report(
        @PathVariable deviceId: String,
        @RequestHeader("X-Device-Key") deviceKey: String,
        @Valid @RequestBody req: DeviceReportRequest,
    ): DeviceReportResponse {
        deviceService.saveReport(deviceId, deviceKey, req)
        // 시연 모드(레이더 점 화면)는 아직 없음
        return DeviceReportResponse(ok = true, demoMode = false)
    }
}
