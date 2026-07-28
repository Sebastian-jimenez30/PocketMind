package com.pocketmind.assistant.api

import io.ktor.http.HttpStatusCode

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    val publicMessage: String,
) : RuntimeException(publicMessage)
