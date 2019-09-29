package TCP.Server

import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

/**
 * READY: notifies that the server is ready to send a file (format: "READY <file_1>;<file_2>;...;<file_n>")
 * SIZES: Message that the server sends containing the sizes of the available files in bytes (format: "SIZES <file_1_size>;<file_2_size>;...;<file_n_size>")
 * HASH: Message that contains the hash of the file for integrity check (format: "HASH <file_hash>")
 * SEND: signals the server to SEND a file (format: "SEND <file_id>")
 * ERR: an error in the process or an unexpected message in the protocol
 * OK: Confirmation message
 */
enum class Protocol(val msg: String){
    READY("READY"),
    SIZES( "SIZES"),
    HASH("HASH"),
    SEND("SEND"),
    ERR("ERROR"),
    OK("OK")
}

val filesPath = "./src/TCP/Server/data"

/**
 * Based on an implementation of https://www.javacodemonk.com/calculating-md5-hash-in-java-kotlin-and-android-96ed9628
 */
data class Hashing(val algorithm: String){

    fun hashInput(input: ByteArray): String{
        val bytes = MessageDigest
            .getInstance(algorithm)
            .digest(input)
        return DatatypeConverter.printHexBinary(bytes).toUpperCase()
    }
}


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
        val file = files[fileId - 1]

        // Sends the hash of the file for integrity check
        sendHash(file, pw)

        // Sends the file
        val bisFile = BufferedInputStream(FileInputStream(file))
        sendFile(file, bisFile, bos)


        // Waits for confirmation from the client
        val integriyCheck = br.readLine()
        println("Integrity check: $integriyCheck")

        // Closes the socket and streams
        br.close()
        pw.close()
        bos.close()
        bisFile.close()
        clientSocket.close()
    }

    private fun sendHash(file: File, pw: PrintWriter){
        val fileBytes = file.readBytes()
        val h = Hashing("MD5").hashInput(fileBytes)
        pw.println("${Protocol.HASH.msg} $h")
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
        var endOfFile = false

        while (curr != fileLength){
            var size = 10000
            if (fileLength - curr >= size) curr += size
            else{
                size = (fileLength - curr).toInt()
                curr = fileLength
                endOfFile = true
            }
            val fileContent = ByteArray(size)

            bis.read(fileContent, 0, size)
            val toSend = if (endOfFile){
                byteArrayOf(1) + fileContent
            }else{
                byteArrayOf(0) + fileContent
            }
            bos.write(toSend)

            println("Process ${(curr*100.0/fileLength)}% complete")
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