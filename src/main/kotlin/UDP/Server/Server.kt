package UDP.Server

import java.net.DatagramSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Server(val executorServer: ExecutorService){
    val datagramSocket = DatagramSocket(4445)

    fun run() {
    }
}

fun run(){
    val executorService = Executors.newFixedThreadPool(25)
    val server = Server(executorService)
}