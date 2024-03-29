package UDP.Server

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Server(val executorService: ExecutorService){
    val datagramSocket by lazy { DatagramSocket(4445) }

    fun run() {
        while (true){
            println("Waiting for client request.")
            val clientRequest = DatagramPacket(ByteArray(1), 1)
            // Blocking operation
            datagramSocket.receive(clientRequest)

            // Destination mainPort and mainAddress
            val clientAddress = clientRequest.address
            val clientPort = clientRequest.port

            println("Client accepted with address: $clientAddress and port: $clientPort")

            executorService.submit(ClientConversation(clientAddress, clientPort))

        }
    }
}

fun run(){
    val executorService = Executors.newFixedThreadPool(25)
    val server = Server(executorService)
    server.run()
}

fun main() {
    run()
}