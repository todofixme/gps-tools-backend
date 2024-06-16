package org.devshred.gpstools.web

import org.devshred.gpstools.api.model.ProblemDTO
import org.devshred.gpstools.common.orElse
import org.springframework.http.HttpStatus

data class Problem(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
) {
    fun toDTO(): ProblemDTO = ProblemDTO(type, title, status, detail.orElse { "Something went wrong." })
}

fun createProblem(status: HttpStatus): Problem = Problem(title = status.reasonPhrase, status = status.value())

fun createProblem(
    status: HttpStatus,
    ex: Exception,
): Problem = Problem(title = status.reasonPhrase, status = status.value(), detail = ex.message)

fun createProblem(
    status: Int,
    ex: Exception,
): Problem = Problem(title = HttpStatus.valueOf(status).reasonPhrase, status = status, detail = ex.message)

fun createProblem(
    status: HttpStatus,
    detail: String,
): Problem = Problem(title = status.reasonPhrase, status = status.value(), detail = detail)

fun createProblem(
    status: Int,
    detail: String,
): Problem = Problem(title = HttpStatus.valueOf(status).reasonPhrase, status = status, detail = detail)
