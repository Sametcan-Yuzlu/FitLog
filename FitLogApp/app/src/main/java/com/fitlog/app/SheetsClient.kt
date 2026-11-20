package com.fitlog.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The script answered, but with something we can't use (wrong URL, not deployed for "Anyone", etc.). */
class SheetError(message: String) : Exception(message)

/**
 * Talks to the Apps Script web app. Apps Script answers a POST with a 302 redirect to
 * script.googleusercontent.com, which must be fetched with GET, so redirects are handled by hand.
 */
object SheetsClient {

    suspend fun post(url: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        var conn = open(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        var code = conn.responseCode
        var hops = 0
        while (code in 300..399 && hops < 5) {
            val location = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = open(location).apply { requestMethod = "GET" }
            code = conn.responseCode
            hops++
        }

        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()

        if (code !in 200..299) throw SheetError("Server returned HTTP $code — check the script URL")
        try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw SheetError("Unexpected reply. Is the script deployed as a Web app with access \"Anyone\"?")
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
        }
}
