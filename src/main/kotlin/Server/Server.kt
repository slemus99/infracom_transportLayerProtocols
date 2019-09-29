package Server

import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Server(val executorService: ExecutorService) {
    val serverSocket: ServerSocket by lazy { createServerSocket() }

    fun createServerSocket() = ServerSocket(3400)

    fun run() {
        while(true){
            println("Waiting new connection")
            val clientSocket = serverSocket.accept()
            println("Client accepted: $clientSocket")
            executorService.submit(ClientConversation(clientSocket))
        }
    }
}

fun run() {
    val executorService = Executors.newFixedThreadPool(25)
    val server = Server(executorService)
    server.run()
}

fun main(){
    run()
}