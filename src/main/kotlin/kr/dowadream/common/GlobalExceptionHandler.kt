package kr.dowadream.common

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException

/** 오류를 ErrorResponse 한 가지 모양으로 바꿔 준다. code 목록은 docs/api-conventions.md 참고 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(e: ResponseStatusException, req: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val status = HttpStatus.valueOf(e.statusCode.value())
        return respond(status, codeOf(status), e.reason ?: defaultMessage(status), req)
    }

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleBadRequest(e: Exception, req: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = (e as? MethodArgumentNotValidException)?.bindingResult?.fieldErrors
            ?.joinToString { "${it.field}: ${it.defaultMessage}" }
            ?: "요청 값이 올바르지 않아요."
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, req)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(e: MissingRequestHeaderException, req: HttpServletRequest) =
        respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "${e.headerName} 헤더가 필요해요.", req)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException, req: HttpServletRequest) =
        respond(HttpStatus.NOT_FOUND, "NOT_FOUND", defaultMessage(HttpStatus.NOT_FOUND), req)

    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception, req: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("처리하지 못한 오류: {} {}", req.method, req.requestURI, e)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", defaultMessage(HttpStatus.INTERNAL_SERVER_ERROR), req)
    }

    private fun respond(status: HttpStatus, code: String, message: String, req: HttpServletRequest) =
        ResponseEntity.status(status).body(ErrorResponse(status.value(), code, message, req.requestURI))

    private fun codeOf(status: HttpStatus) = when (status) {
        HttpStatus.BAD_REQUEST -> "INVALID_REQUEST"
        HttpStatus.UNAUTHORIZED -> "UNAUTHORIZED"
        HttpStatus.FORBIDDEN -> "FORBIDDEN"
        HttpStatus.NOT_FOUND -> "NOT_FOUND"
        else -> if (status.is5xxServerError) "INTERNAL_ERROR" else status.name
    }

    private fun defaultMessage(status: HttpStatus) = when (status) {
        HttpStatus.UNAUTHORIZED -> "인증 정보가 올바르지 않아요."
        HttpStatus.NOT_FOUND -> "찾는 정보가 없어요."
        HttpStatus.INTERNAL_SERVER_ERROR -> "서버에서 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요."
        else -> status.reasonPhrase
    }
}
