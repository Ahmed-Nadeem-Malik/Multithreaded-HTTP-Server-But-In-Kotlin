package org.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.ServerSocket
import java.net.Socket

// ============ Constants ============

private val statusTexts = mapOf(
    200 to "OK", 201 to "Created", 204 to "No Content", 404 to "Not Found", 500 to "Internal Server Error"
)

// ============ Types ============

data class HTTP(
    val method: String, val path: String, val headers: Map<String, String>, val body: String
)

typealias Handler = (HTTP, Socket) -> Unit

// ============ Cached Responses ============

object CachedResponses {
    val index = buildResponse(200, "text/html", loadResource("/index.html") ?: "")
    val css = buildResponse(200, "text/css", loadResource("/css/style.css") ?: "")
    val js = buildResponse(200, "application/javascript", loadResource("/js/script.js") ?: "")
    val notFound = buildResponse(404, "text/html", "<h1>404 Not Found</h1>")
    val favicon = buildResponse(204, "text/plain", "")
}

// ============ Entry Point ============

fun main() = runBlocking {
    val server = ServerSocket(8000, 1000)
    server.reuseAddress = true
    val routes = initRoutes()
    val dispatcher = Dispatchers.IO.limitedParallelism(512)

    println("Server running on http://localhost:8000")

    while (true) {
        val client = server.accept()
        launch(dispatcher) {
            handleClient(client, routes)
        }
    }
}

// ============ Request Handling ============

suspend fun handleClient(client: Socket, routes: Map<Pair<String, String>, Handler>) = withContext(Dispatchers.IO) {
    try {
        client.tcpNoDelay = true
        client.use { socket ->
            val reader = socket.getInputStream().bufferedReader()
            val request = parseRequest(reader)
            val route = request.method.uppercase() to request.path

            val handler = routes[route]
            if (handler != null) {
                handler(request, socket)
            } else {
                socket.getOutputStream().write(CachedResponses.notFound)
            }
        }
    } catch (_: Exception) {
    }
}

fun parseRequest(reader: BufferedReader): HTTP {
    val requestLine = reader.readLine() ?: throw IllegalStateException("Empty request")
    val firstSpace = requestLine.indexOf(' ')
    val secondSpace = requestLine.indexOf(' ', firstSpace + 1)
    val method = requestLine.substring(0, firstSpace)
    val path = requestLine.substring(firstSpace + 1, secondSpace)

    val headers = mutableMapOf<String, String>()
    var headerLine = reader.readLine()
    while (!headerLine.isNullOrEmpty()) {
        val colon = headerLine.indexOf(':')
        headers[headerLine.substring(0, colon)] = headerLine.substring(colon + 2)
        headerLine = reader.readLine()
    }

    val body = headers["Content-Length"]?.toIntOrNull()?.let { len ->
        CharArray(len).also { reader.read(it, 0, len) }.let(::String)
    } ?: ""

    return HTTP(method, path, headers, body)
}

// ============ Routes ============

fun initRoutes(): Map<Pair<String, String>, Handler> = mapOf(("GET" to "/") to { _, socket ->
    socket.getOutputStream().write(CachedResponses.index)
}, ("GET" to "/css/style.css") to { _, socket ->
    socket.getOutputStream().write(CachedResponses.css)
}, ("GET" to "/js/script.js") to { _, socket ->
    socket.getOutputStream().write(CachedResponses.js)
}, ("GET" to "/favicon.ico") to { _, socket ->
    socket.getOutputStream().write(CachedResponses.favicon)
}, ("POST" to "/echo") to { request, socket ->
    val response = buildResponse(201, "application/json", """{"received":"${request.body}"}""")
    socket.getOutputStream().write(response)
})

// ============ Helpers ============

fun buildResponse(status: Int, contentType: String, body: String): ByteArray {
    val statusText = statusTexts[status] ?: "Unknown"
    val bodyBytes = body.toByteArray()
    val header =
        "HTTP/1.1 $status $statusText\r\nContent-Type: $contentType\r\nContent-Length: ${bodyBytes.size}\r\n\r\n"
    return header.toByteArray() + bodyBytes
}

fun loadResource(path: String): String? = object {}.javaClass.getResource(path)?.readText()
