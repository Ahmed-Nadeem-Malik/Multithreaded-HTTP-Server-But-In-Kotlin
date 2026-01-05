package org.example

import java.net.Socket

fun main() {
    val socket = Socket("localhost",8000)

    val writer = socket.getOutputStream().bufferedWriter()
    writer.write("this is my name")
    writer.newLine() // submits it to the buffer
    writer.flush() // sends buffer to the server

    socket.close()
}
