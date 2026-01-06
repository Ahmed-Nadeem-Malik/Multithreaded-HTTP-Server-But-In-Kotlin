package org.example

import java.io.BufferedReader
import java.io.StringReader
import java.net.Socket
import java.nio.channels.SocketChannel

/**
 * HTTP status text mappings for response building.
 */
val statusTexts = mapOf(
    200 to "OK", 201 to "Created", 204 to "No Content", 404 to "Not Found", 500 to "Internal Server Error"
)

/**
 * Represents an HTTP request with method, path, headers, and body.
 *
 * @property method HTTP method (GET, POST, etc.)
 * @property path Request path (/, /echo, etc.)
 * @property headers Map of HTTP headers
 * @property body Request body content
 */
data class HTTP(
    val method: String, val path: String, val headers: Map<String, String>, val body: String
)

/**
 * Pre-computed HTTP responses for static content.
 * These are cached at startup for maximum performance.
 */
object CachedResponses {
    /** Main page response */
    val index = buildResponse(200, "text/html", loadResource("/index.html") ?: "")

    /** CSS stylesheet response */
    val css = buildResponse(200, "text/css", loadResource("/css/style.css") ?: "")

    /** JavaScript file response */
    val js = buildResponse(200, "application/javascript", loadResource("/js/script.js") ?: "")

    /** 404 Not Found response */
    val notFound = buildResponse(404, "text/html", "<h1>404 Not Found</h1>")

    /** Empty favicon response (204 No Content) */
    val favicon = buildResponse(204, "text/plain", "")
}

/**
 * Builds a complete HTTP response with headers and body.
 *
 * @param status HTTP status code (200, 404, etc.)
 * @param contentType MIME type of the response content
 * @param body Response body content
 * @return Complete HTTP response as ByteArray ready to send
 */
fun buildResponse(status: Int, contentType: String, body: String): ByteArray {
    val statusText = statusTexts[status] ?: "Unknown"
    val bodyBytes = body.toByteArray()
    val header =
        "HTTP/1.1 $status $statusText\r\nContent-Type: $contentType\r\nContent-Length: ${bodyBytes.size}\r\n\r\n"
    return header.toByteArray() + bodyBytes
}

/**
 * Loads a resource file from classpath.
 *
 * @param path Resource path (e.g., "/index.html")
 * @return Resource content as String, or null if not found
 */
fun loadResource(path: String): String? = object {}.javaClass.getResource(path)?.readText()

// ============ Blocking Request Parsing (Virtual Threads) ============

/**
 * Handler function type for blocking I/O operations.
 * Takes an HTTP request and Socket for writing response.
 */
typealias BlockingHandler = (HTTP, Socket) -> Unit

/**
 * Parses HTTP request from BufferedReader using blocking I/O.
 * Used by virtual thread servers.
 *
 * @param reader BufferedReader for reading request data
 * @return Parsed HTTP request object
 */
fun parseRequestBlocking(reader: BufferedReader): HTTP {
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

/**
 * Handles HTTP client connection using blocking I/O.
 * Used by virtual thread servers.
 *
 * @param client Socket connection from client
 * @param routes Map of available route handlers
 */
fun handleClientBlocking(client: Socket, routes: Map<Pair<String, String>, BlockingHandler>) {
    try {
        client.tcpNoDelay = true
        client.use { socket ->
            val reader = socket.getInputStream().bufferedReader()
            val request = parseRequestBlocking(reader)
            val route = request.method.uppercase() to request.path

            val handler = routes[route]
            if (handler != null) {
                handler(request, socket)
            } else {
                socket.getOutputStream().write(CachedResponses.notFound)
            }
        }
    } catch (_: Exception) {
        // Ignore exceptions for simplicity
    }
}

// ============ NIO Request Handling (Virtual Threads + NIO) ============

/**
 * Handles HTTP client connection using NIO channels with virtual threads.
 * Combines efficiency of NIO with simplicity of virtual threads.
 *
 * @param client SocketChannel connection from client
 * @param routes Map of available route handlers
 */
fun handleClientNIO(client: SocketChannel, routes: Map<Pair<String, String>, BlockingHandler>) {
    try {
        client.socket().tcpNoDelay = true

        val buffer = java.nio.ByteBuffer.allocate(8192)
        client.read(buffer)
        buffer.flip()

        val requestStr = String(buffer.array(), 0, buffer.limit())
        val reader = BufferedReader(StringReader(requestStr))
        val request = parseRequestBlocking(reader)
        val route = request.method.uppercase() to request.path

        val handler = routes[route]
        if (handler != null) {
            // Convert to Socket for existing handlers
            val socket = client.socket()
            handler(request, socket)
        } else {
            val responseBuffer = java.nio.ByteBuffer.wrap(CachedResponses.notFound)
            client.write(responseBuffer)
        }
    } catch (_: Exception) {
        // Ignore exceptions for simplicity
    } finally {
        client.close()
    }
}

// ============ Route Definitions ============

/**
 * Initializes route handlers for blocking I/O servers.
 * Used by both original virtual thread and NIO virtual thread servers.
 *
 * @return Map of (method, path) to handler functions
 */
fun initBlockingRoutes(): Map<Pair<String, String>, BlockingHandler> = mapOf(
    ("GET" to "/") to { _, socket -> socket.getOutputStream().write(CachedResponses.index) },
    ("GET" to "/css/style.css") to { _, socket -> socket.getOutputStream().write(CachedResponses.css) },
    ("GET" to "/js/script.js") to { _, socket -> socket.getOutputStream().write(CachedResponses.js) },
    ("GET" to "/favicon.ico") to { _, socket -> socket.getOutputStream().write(CachedResponses.favicon) },
    ("POST" to "/echo") to { request, socket ->
        val response = buildResponse(201, "application/json", """{"received":"${request.body}"}""")
        socket.getOutputStream().write(response)
    })
