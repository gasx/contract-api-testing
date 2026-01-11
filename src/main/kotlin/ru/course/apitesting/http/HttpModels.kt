package ru.course.apitesting.http

data class HttpResult(
    val ok: Boolean,
    val status: Int?,
    val bodyText: String?,
    val error: String?
)
