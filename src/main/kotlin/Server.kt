package org.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.ServerSocket
import java.net.Socket

// ============ Constants ============

/** HTTP status codes mapped to their standard text descriptions. */
private val statusTexts = mapOf(
    200 to "OK", 201 to "Created", 204 to "No Content", 404 to "Not Found", 500 to "Internal Server Error"
)

// ============ Types ============

/**
 * Represents a parsed HTTP request.
 *
 * @property method The HTTP method (GET, POST, PUT, DELETE, etc.)
 * @property path The request path (e.g., "/users", "/api/data")
 * @property headers Map of header names to values
 * @property body The request body content (empty string if no body)
 */
data class HTTP(
    val method: String, val path: String, val headers: Map<String, String>, val body: String
)

/**
 * Type alias for route handlers.
 *
 * A handler receives the parsed [HTTP] request and the [Socket] to write the response to.
 */
typealias Handler = (HTTP, Socket) -> Unit

// ============ Cached Responses ============

/**
 * Pre-computed HTTP responses for static content.
 *
 * Responses are built once at startup as [ByteArray]s for maximum performance.
 * This avoids string formatting and multiple write calls on each request.
 */
object CachedResponses {
    /** The main index.html page. */
    val index = buildResponse(200, "text/html", loadResource("/index.html") ?: "")

    /** The CSS stylesheet. */
    val css = buildResponse(200, "text/css", loadResource("/css/style.css") ?: "")

    /** The JavaScript file. */
    val js = buildResponse(200, "application/javascript", loadResource("/js/script.js") ?: "")

    /** Standard 404 Not Found response. */
    val notFound = buildResponse(404, "text/html", "<h1>404 Not Found</h1>")

    /** Empty response for favicon requests. */
    val favicon = buildResponse(204, "text/plain", "")
}

// ============ Entry Point ============

/**
 * Main entry point for the HTTP server.
 *
 * Starts a coroutine-based server on port 8000 that:
 * - Accepts incoming TCP connections
 * - Launches a coroutine for each client on [Dispatchers.IO]
 * - Routes requests to appropriate handlers
 *
 * Server configuration:
 * - Port: 8000
 * - Backlog: 1000 pending connections
 * - Parallelism: 512 concurrent coroutines
 */
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

/**
 * Handles a single client connection.
 *
 * Parses the incoming HTTP request, looks up the appropriate handler,
 * and writes the response. The socket is automatically closed when done.
 *
 * @param client The connected client socket
 * @param routes Map of (method, path) pairs to handler functions
 */
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
        // Connection closed or malformed request - silently ignore
    }
}

/**
 * Parses an HTTP request from a buffered reader.
 *
 * Reads and parses:
 * 1. Request line (method, path, version)
 * 2. Headers (until empty line)
 * 3. Body (if Content-Length header present)
 *
 * @param reader The buffered reader connected to the client socket
 * @return Parsed [HTTP] request object
 * @throws IllegalStateException if the request line is empty
 */
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

/**
 * Initializes all route handlers for the server.
 *
 * Defined routes:
 * - `GET /` - Serves the index page
 * - `GET /css/style.css` - Serves the stylesheet
 * - `GET /js/script.js` - Serves the JavaScript
 * - `GET /favicon.ico` - Returns empty 204 response
 * - `POST /echo` - Echoes the request body as JSON
 *
 * @return Immutable map of routes to handlers
 */
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

/**
 * Builds a complete HTTP response as a byte array.
 *
 * Constructs a valid HTTP/1.1 response with:
 * - Status line
 * - Content-Type header
 * - Content-Length header
 * - Empty line separator
 * - Body content
 *
 * @param status HTTP status code (e.g., 200, 404)
 * @param contentType MIME type of the response body (e.g., "text/html")
 * @param body The response body content
 * @return Complete HTTP response ready to write to socket
 */
fun buildResponse(status: Int, contentType: String, body: String): ByteArray {
    val statusText = statusTexts[status] ?: "Unknown"
    val bodyBytes = body.toByteArray()
    val header =
        "HTTP/1.1 $status $statusText\r\nContent-Type: $contentType\r\nContent-Length: ${bodyBytes.size}\r\n\r\n"
    return header.toByteArray() + bodyBytes
}

/**
 * Loads a resource file from the classpath.
 *
 * @param path Path to the resource (e.g., "/index.html", "/css/style.css")
 * @return File contents as a string, or null if not found
 */
fun loadResource(path: String): String? = object {}.javaClass.getResource(path)?.readText()
