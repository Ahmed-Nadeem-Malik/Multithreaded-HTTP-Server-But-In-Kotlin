package org.example

import java.net.Socket
import java.time.LocalDateTime
import java.time.temporal.TemporalAmount

fun main() {
    val socket = Socket("learnxinyminutes.com", 80)

    val reader = socket.getInputStream().bufferedReader()
    val writer = socket.getOutputStream().bufferedWriter()


    writer.write("GET /kotlin/ HTTP/1.1\r\n")
    writer.write("Host: learnxinyminutes.com\r\n")
    writer.write("Connection: close\r\n")
    writer.write("\r\n")
    val startTime = System.nanoTime()
    writer.flush()
    val finishTime = System.nanoTime()

    reader.forEachLine { println(it) }
    println("---------------------------")
    println(finishTime - startTime)

    socket.close()

    /*

    val writer = socket.getOutputStream().bufferedWriter()
    writer.write("hello world")
    writer.newLine() // submits it to the buffer
    writer.flush() // sends buffer to the server

    socket.close()

     */
}
