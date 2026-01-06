package org.example

import java.net.ServerSocket
import java.util.concurrent.Executors

// ============ Original Virtual Thread Server (Port 8000) ============

/**
 * Main entry point for the original blocking I/O virtual thread HTTP server.
 * Uses traditional Java ServerSocket with virtual threads for concurrent handling.
 * Server listens on port 8000.
 */
fun main() {
    val server = ServerSocket(8000, 1000)
    server.reuseAddress = true
    val routes = initBlockingRoutes()
    val executor = Executors.newVirtualThreadPerTaskExecutor()

    println("Server running on http://localhost:8000 (original virtual threads)")

    while (true) {
        val client = server.accept()
        executor.submit { handleClientBlocking(client, routes) }
    }
}