package org.example

import java.net.ServerSocket

fun main() {

    val server = ServerSocket(8000)

    val client = server.accept()

    val reader = client.getInputStream().bufferedReader()
    val message = reader.readLine()
    print("Client message: $message")

    server.close()
    client.close()
}
