package com.hermes.wear.data.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stands in for the gateway: an OkHttp interceptor that records each request
 * and answers it from [respond] without touching the network.
 */
class FakeServer(
    var respond: (Request) -> Pair<Int, String> = { 200 to """{"output":[]}""" },
) {
    val requests = CopyOnWriteArrayList<Request>()
    val bodies = CopyOnWriteArrayList<String>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            requests += request
            bodies += request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            val (code, body) = respond(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        })
        .build()
}
