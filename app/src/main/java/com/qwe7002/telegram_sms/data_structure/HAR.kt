package com.qwe7002.telegram_sms.data_structure

data class HAR (
    val log: Log
)

data class Log (
    val version: String,
    val entries: List<Entry>
)

data class Entry (
    val request: Request
)

data class Request (
    val method: String,
    val url: String,
    val httpVersion: String,
    val cookies: List<Cookie>,
    val headers: List<Header>,
    val queryString: List<Header>,
    val headersSize: Long,
    val bodySize: Long,
    val postData: PostData?
)

data class Header (
    val name: String,
    val value: String
)

data class PostData (
    val mimeType: String,
    val params: List<Param>?,
    val text: String?
)

data class Param (
    val name: String,
    val value: String
)
data class Cookie (
    val name: String,
    val value: String
)
