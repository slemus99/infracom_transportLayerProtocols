package TCP

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.ServerSocket
import java.net.Socket


/**
 * Based on http://www.codebytes.in/2014/11/file-transfer-using-tcp-java.html
 */
data class Server(val clientSocket: Socket, val id: Int, val fileName: String): Thread(){

    override fun run() {
        val file = File(fileName)
        val bis = BufferedInputStream(FileInputStream(file))
        val out = BufferedOutputStream(clientSocket.getOutputStream())
        send(file, bis, out)

        bis.close()
        clientSocket.close()
    }

    /**
     * Sends a file to the client via its Output Stream
     */
    private fun send(file: File, bis: BufferedInputStream, out: BufferedOutputStream){


        // Transform File contents into byte array
        val fileLength = file.length()
        var curr = 0.toLong()

        val before = System.currentTimeMillis()

        while (curr != fileLength){
            var size = 10000
            if (fileLength - curr >= size) curr += size
            else{
                size = (fileLength - curr).toInt()
                curr = fileLength
            }
            val fileContent = ByteArray(size)
            bis.read(fileContent, 0, size)
            out.write(fileContent)

            println("Process ${(curr*100/fileLength)} complete")
        }

        val after = System.currentTimeMillis()

        out.flush()

        println("Process finished successfully in ${after - before} ms")
    }
}


/**
 * Main execution thread for the server. Waits and accepts multiple clients
 */
fun main(args: Array<String>) {

    var numThreads = 0

    println("Initializing server")
    val serverSocket = ServerSocket(3400)

    while (true){

        // accept method blocks the current method until it accepts a client
        val clientSocket = serverSocket.accept()
        
        numThreads ++
    }

}