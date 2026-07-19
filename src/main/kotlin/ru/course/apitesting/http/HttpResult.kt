package ru.course.apitesting.http

import ru.course.apitesting.report.FileTransferInfo

data class HttpResult(
    val ok: Boolean,
    val status: Int?,
    val bodyText: String? = null,
    val bodyBytes: ByteArray? = null,
    val contentType: String? = null,
    val downloadedFile: FileTransferInfo? = null,
    val error: String? = null
)
