package org.example

import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.ServerSocketChannel
import java.util.concurrent.Executors

// ============ NIO Virtual Thread Server (Port 8001) ============

/**
 * Main entry point for NIO-based virtual thread HTTP server.
 * Uses Java NIO channels with virtual threads for efficient I/O handling.
 * Server listens on port 8001.
 */
fun main() {
    val serverChannel = ServerSocketChannel.open()
    serverChannel.bind(InetSocketAddress(8001))
    serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true)

    val routes = initBlockingRoutes()
    val executor = Executors.newVirtualThreadPerTaskExecutor()

    println("Server running on http://localhost:8001 (virtual threads + NIO)")

    while (true) {
        val client = serverChannel.accept()
        if (client != null) {
            executor.submit { handleClientNIO(client, routes) }
        }
    }
}
