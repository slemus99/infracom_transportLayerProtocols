package TCP.Server

import java.io.*
import java.net.ServerSocket
import java.net.Socket

/**
 * READY: notifies that the server is ready to send a file (format: "READY <file_1>;<file_2>;...;<file_n>")
 * SIZES: Message that the server sends containing the sizes of the available files in bytes (format: "SIZES <file_1_size>;<file_2_size>;...;<file_n_size>")
 * SEND: signals the server to SEND a file (format: "SEND <file_id>")
 * ERR: an error in the process or an unexpected message in the protocol
 */
enum class Protocol(val msg: String){
    READY("READY"),
    SIZES( "SIZES"),
    SEND("SEND"),
    ERR("ERROR")
}

val filesPath = "./src/TCP/Server/data"

/**
 * Based on implementations from
 * http://www.codebytes.in/2014/11/file-transfer-using-tcp-java.html
 * https://stackoverflow.com/questions/9520911/java-sending-and-receiving-file-byte-over-sockets/
 */
data class Server(val clientSocket: Socket, val id: Int): Thread(){


    // List of available files in /data file
    val files = File(filesPath).listFiles()

    // A string containing the names of the files
    val fileNames = getAvailableFileNames()

    // A String containing the sizes of the files
    val fileSizes = getAvailableFileSizes()

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
        var fileId = prepareToSend(br, pw)
        while (fileId == -1){
            fileId = prepareToSend(br, pw)
        }

        // Sends the file
        val file = files[fileId - 1]
        val bisFile = BufferedInputStream(FileInputStream(file))
        sendFile(file, bisFile, bos)

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
    private fun prepareToSend(br: BufferedReader, pw: PrintWriter): Int{

        // Notifies the client that the server is ready so sendFile a file
        pw.println("${Protocol.READY.msg} $fileNames")
        pw.println("${Protocol.SIZES.msg} $fileSizes")

        // Awaits for the file that the user wants to get
        val ans = br.readLine().split(" ")

        if (ans[0] != Protocol.SEND.msg){
            pw.println(Protocol.ERR.msg)
            return -1

        }else{
            val fileId = ans[1].toInt()
            if (fileId > 0 && fileId <= files.size){
                return fileId
            }else{
                pw.println(Protocol.ERR.msg)
                return -1
            }
        }


    }

    private fun getAvailableFileNames(): String{

        val extractName: (File) -> String = {f: File ->
            val path = f.name.split("/")
            path[path.size - 1]
        }

        // Names of the files
        val filesNames = files.map { extractName(it) }
        return filesNames.fold(""){x: String, y: String -> "$x;$y"}.drop(1)
    }

    private fun getAvailableFileSizes(): String{

        // sizes of the files in bytes
        val fileSizes = files.map {it.length()}
        return fileSizes.fold(""){x: String, y: Long -> "$x;$y"}.drop(1)
    }

    /**
     * Sends a file to the client via its Output Stream
     */
    private fun sendFile(file: File, bis: BufferedInputStream, bos: BufferedOutputStream){


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

//    val folder = File("./src/TCP/Server/data")
//    folder.listFiles().forEach(::println)
//
//    val extractName: (File) -> String = {f: File ->
//        val path = f.name.split("/")
//        path[path.size - 1]
//    }
//
//    val filesNames = folder.listFiles().map { extractName(it) }
//    val availableFiles = filesNames.fold(""){x: String, y: String -> "$x;$y"}.drop(1)
//    println(availableFiles)
//
//    val fileSizes = folder.listFiles().map {it.length()}
//    val availableFileSizes = fileSizes.fold(""){x: String, y: Long -> "$x;$y"}.drop(1)
//    println(availableFileSizes)


    while (true){

        // accept method blocks the current method until it accepts a client
        println("Waiting for client")
        val clientSocket = serverSocket.accept()
        print("Client Socket accepted: $clientSocket")
        val serverThread = Server(clientSocket, numThreads)
        serverThread.start()

        numThreads ++
    }

    serverSocket.close()

}