package com.fastaccess.provider.rest.interceptors

import android.net.Uri
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException

class PaginationInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful)
            return response

        val headers = chain.request().headers()
        if (headers != null) {
            if (headers.values("Accept").contains("application/vnd.github.html")
                    || headers.values("Accept").contains("application/vnd.github.VERSION.raw")) {
                return response //return them as they are.
            }
        }

        // Fetch link collection if it exists
        val linkBuilder = StringBuilder()

        response.header("link")?.apply {
            split(",")
                    .filter(String::isNotBlank)
                    .map { it.split(";") }
                    .filter { it.size >= 2 }
                    .mapNotNull { (link1, link2) ->
                        val page = Uri.parse(link1.replace("[<>]".toRegex(), ""))
                                .getQueryParameter("page") ?: return@mapNotNull null
                        val rel = link2.replace("\"", "").replace("rel=", "")
                        page to rel.trim()
                    }.forEach { (page, rel) ->
                        linkBuilder.append("\"$rel\":\"$page\",")
                    }
        }


        if (response.peekBody(1).string() == "[") {
            val body = response.body() ?: return response
            val json = "{$linkBuilder \"items\":$body}"
            return response.newBuilder()
                    .body(ResponseBody.create(
                            body.contentType(),
                            json)).build()
        }

        if (linkBuilder.isNotEmpty()) {
            val body = response.body() ?: return response
            val pagination = "{$linkBuilder ${body.string()?.substring(1)}"
            return response.newBuilder()
                    .body(ResponseBody.create(body.contentType(), pagination))
                    .build()
        }
        return response
    }

}
