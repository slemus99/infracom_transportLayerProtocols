package TCP.Server

import java.io.*
import java.net.ServerSocket
import java.net.Socket

/**
 * READY: notifies that the server is ready to send a file (format: "READY <file_1>;<file_2>;...;<file_n>")
 * SEND: signals the server to SEND a file (format: "SEND <file_name>")
 * ERR: an error in the process or an unexpected message in the protocol
 */
enum class Protocol(val msg: String){
    READY("READY"),
    SEND("SEND"),
    ERR("ERROR")
}

val filesPath = "./src/TCP/Server/data"

/**
 * Based on an implementation from http://www.codebytes.in/2014/11/file-transfer-using-tcp-java.html
 */
data class Server(val clientSocket: Socket, val id: Int): Thread(){

    override fun run() {

        val ips = clientSocket.getInputStream()
        val ops = clientSocket.getOutputStream()

        // Streams used to send the file
        val bis = BufferedInputStream(ips)
        val bos = BufferedOutputStream(ops)


        // Streams used to send messages
        val br = BufferedReader(InputStreamReader(ips))
        val pw = PrintWriter(ops, true)

        // Extract the requested file by the client (while valid)
        var fileName = prepareToSend(br, pw)
        while (fileName != Protocol.ERR.msg){
            fileName = prepareToSend(br, pw)
        }

        // Sends the file
        val file = File("$filesPath/$fileName")
        val bisFile = BufferedInputStream(FileInputStream(file))
        send(file, bisFile, bos)

        // Closes the socket and streams
        br.close()
        pw.close()
        bos.close()
        bisFile.close()
        clientSocket.close()
    }

    /**
     * Starts the communication with the client regarding the sending of the file.
     * Returns the requested file or a message with an error
     */
    private fun prepareToSend(br: BufferedReader, pw: PrintWriter): String{

        // List of available files in /data file
        val files = File(filesPath).listFiles()

        val extractName: (File) -> String = {f: File ->
            val path = f.name.split("/")
            path[path.size - 1]
        }
        val filesNames = files.map { extractName(it) }
        val availableFiles = filesNames.fold(""){x: String, y: String -> "$x;$y"}.drop(1)

        // Notifies the client that the server is ready so send a f ile
        pw.println("${Protocol.READY.msg} $availableFiles")

        // Awaits for the file that the user wants to get
        val ans = br.readLine().split(" ")

        if (ans[0] != Protocol.SEND.msg){
            pw.println(Protocol.ERR.msg)
            return Protocol.ERR.msg

        }else{
            val fileName = ans[1]
            if (fileName in filesNames){
                return fileName
            }else{
                pw.println(Protocol.ERR.msg)
                return Protocol.ERR.msg
            }
        }


    }


    /**
     * Sends a file to the client via its Output Stream
     */
    private fun send(file: File, bis: BufferedInputStream, bos: BufferedOutputStream){


        println("The file selected by the client is: ${file.name}")

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
            bos.write(fileContent)

            println("Process ${(curr*100/fileLength)} complete")
        }

        val after = System.currentTimeMillis()

        bos.flush()

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

    val folder = File("./src/TCP/Server/data")
    folder.listFiles().forEach(::println)


    while (true){

        // accept method blocks the current method until it accepts a client
        val clientSocket = serverSocket.accept()
        val serverThread = Server(clientSocket, numThreads)
        serverThread.start()

        numThreads ++
    }

    serverSocket.close()

}