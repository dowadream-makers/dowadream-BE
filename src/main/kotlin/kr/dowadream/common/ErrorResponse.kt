package kr.dowadream.common

import java.time.Instant

/** 모든 오류 응답의 공통 모양 (docs/api-conventions.md 4장) */
data class ErrorResponse(
    val status: Int,
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Instant = Instant.now(),
)
