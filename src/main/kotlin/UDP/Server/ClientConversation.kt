package UDP.Server

import java.io.*
import java.net.*
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

/**
 * READY: notifies that the server is ready to send a file (format: "READY <file_1>;<file_2>;...;<file_n>")
 * SIZES: Message that the server sends containing the sizes of the available files in bytes (format: "SIZES <file_1_size>;<file_2_size>;...;<file_n_size>")
 * HASH: Message that contains the hash of the file for integrity check (format: "HASH <file_hash>")
 * SEND: signals the server to SEND a file (format: "SEND <file_id>")
 * NUM: notifies the client how many packages are to be sent (format: "NUM <num_packages_to_send")
 * ERR: an error in the process or an unexpected message in the protocol
 * OK: Confirmation message
 */
enum class Protocol(val msg: String){
    READY("READY"),
    SIZES( "SIZES"),
    HASH("HASH"),
    SEND("SEND"),
    NUM("NUM"),
    ERR("ERROR"),
    OK("OK")
}
val filesPath = "./src/main/kotlin/UDP/Server/data"
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
 * Class that handles the protocol with the client
 */
class ClientConversation(val address: InetAddress, val port: Int): Runnable {

    // Socked from server instance
    val socket = DatagramSocket()

    // List of available files in /data file
    val files = File(filesPath).listFiles()

    // A string containing the names of the files
    val fileNames = getAvailableFileNames()

    // A String containing the sizes of the files
    val fileSizes = getAvailableFileSizes()

    // Constant size of each of the packets to be sent
    val bufferStandardSize = 548

    // Used when trying to convert an byte array into a string
    val charset = Charsets.UTF_8

    override fun run() {
        try {
            val commStatus = communicateWithClient()
            println("Integriry Check: $commStatus")
        }catch (e: Exception){
            println("Child thread failed ${Thread.currentThread().id}")
            e.printStackTrace()

        }finally {
            socket.close()
        }
    }

    /**
     * Repeats the communication with the client until the file is successfully transfered
     */
    private tailrec fun communicateWithClient(): String{
        val integrityCheck = executeProtocol()
        if (integrityCheck == Protocol.ERR.msg) return communicateWithClient()
        else return integrityCheck
    }

    /**
     * Performs the communication with the client that allows the server to send a file
     */
    private fun executeProtocol(): String{
        // Ask the client for the file
        val fileId = requestFileId()
        val requestedFile = files[fileId - 1]
        println(fileId)

        // Send the number of packages to be sent
        val numPacks: Long = (requestedFile.length() / bufferStandardSize)
        val numPacksPlusReminder = numPacks + when(requestedFile.length() % bufferStandardSize) {
            0L -> 0L
            else -> 1L
        }

        socket.send(packageMessage("NUM $numPacksPlusReminder"))

        // Send the hash of the file
        sendHash(requestedFile)

        // Send the requested  file to the client
        sendFile(requestedFile, numPacks)

        // Waits for the client's confirmation regarding the integrity of the file
        return unpackageMessage(ByteArray(bufferStandardSize)).toString(charset)
    }

    /**
     * Repeats the process of sending the information of the files
     * until the client request a valid file id
     */
    private tailrec fun requestFileId(): Int{
        val fileId = prepareToSend()
        if (fileId != -1) return fileId
        else return requestFileId()
    }

    /**
     * Starts the communication with the client regarding the sending of the file.
     * Returns the requested file or a message with an error
     */
    private fun prepareToSend(): Int{

        // Send available files with the respective file sizes
        socket.send(packageMessage("${Protocol.READY.msg} $fileNames"))
        socket.send(packageMessage("${Protocol.SIZES.msg} $fileSizes"))

        // Awaits for the id of the file that the user wants to get
        val requestedFile = unpackageMessage(ByteArray(bufferStandardSize)).toString(charset).split(" ")

        val fileId = requestedFile[1].toInt()
        println(fileId)


        if (requestedFile[0] != Protocol.SEND.msg || fileId < 1 || fileId > files.size){
            println("error")
            socket.send(packageMessage(Protocol.ERR.msg))
            return -1
        }else{
            println("No error")
            return fileId
        }
    }

    private fun cleanByteArray(bs: ByteArray): ByteArray{
        var firstZero = 0
        var found = false

        while (firstZero < bs.size && !found){
            if (bs[firstZero] == 0.toByte())
                found = true
            else
                firstZero ++
        }

        val newByteArr = ByteArray(firstZero)

        for (i in 0 until  firstZero)
            newByteArr[i] = bs[i]

        return newByteArr
    }

    /**
     * Packages a message string into a Datagram Package.
     */
    private fun packageMessage(msg: String): DatagramPacket{
        val msgBytes = msg.toByteArray()
        return DatagramPacket(msgBytes, msgBytes.size, address, port)
    }

    /**
     * Packages a message in bytes into a Datagram Package.
     */
    private fun packageMessage(msg: ByteArray): DatagramPacket =
            DatagramPacket(msg, msg.size, address, port)

    /**
     * Un-packages a message in the given buffer
     */
    private fun unpackageMessage(toReceive: ByteArray): ByteArray {
        socket.receive(DatagramPacket(toReceive, toReceive.size))
        return cleanByteArray(toReceive)
    }

    /**
     * Sends the hash of the file to the client
     */
    private fun sendHash(file: File){
        val fileBytes = file.readBytes()
        val h = Hashing("MD5").hashInput(fileBytes)
        socket.send(packageMessage(h))
    }


    private fun getAvailableFileNames(): String{
        val extractName: (File) -> String = { f: File ->
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
     * Sends a file to the client vie Datagram Packages
     */
    private fun sendFile(file: File, numPacks: Long){
        println("The file selected by the client is: ${file.name}")
        val n = file.length()
        // buffered stream to read the contents of the file
        val bis = BufferedInputStream(FileInputStream(file))
        var currPack = 0L
        var bytesSentSoFar = 0L
        val before = System.currentTimeMillis()

        while (currPack <= numPacks){

            var buff = bufferStandardSize

            if (n - bytesSentSoFar >= buff) bytesSentSoFar += buff
            else{
                buff = (n - bytesSentSoFar).toInt()
                bytesSentSoFar = n
            }

            val fileContent = ByteArray(buff)
            bis.read(fileContent, 0, buff)

            socket.send(packageMessage(fileContent))
            val confirmation = unpackageMessage(ByteArray(bufferStandardSize)).toString(charset)
            if (confirmation != Protocol.OK.msg){
                throw java.lang.Exception("There was an error while transfering the package: $confirmation")
            }

            currPack ++
            println("Process ${(bytesSentSoFar*100.0/n)}% complete")
        }

        val after = System.currentTimeMillis()
        println("Process finished successfully in ${after - before} ms")

        bis.close()
    }
}