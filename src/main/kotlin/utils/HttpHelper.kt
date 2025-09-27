package com.github.risboo6909.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import org.springframework.stereotype.Component

@Component
class HttpHelper: HttpHelperInterface {

    override suspend fun queryGet(url: String): String {
        val client = HttpClient(CIO)
        val response: HttpResponse = client.get(url)
        client.close()
        return response.bodyAsText()
    }

}