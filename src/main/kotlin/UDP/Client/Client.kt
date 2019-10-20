package UDP.Client

import java.io.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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

val filesPath = "./src/main/kotlin/UDP/Client/data"

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

data class Client(val socket: DatagramSocket, val mainPort: Int, val mainAddress: InetAddress, val fileDestination: String, val fileSelectorFun: () -> Int){

    // Constant size of each of the packets to be sent
    val bufferStandardSize = 548

    // Used when trying to convert an byte array into a string
    val charset = Charsets.UTF_8

    lateinit var address: InetAddress

    var port = -1

    /**
     * Executes the protocol and repeats it until the file has  been received successfully
     */
    fun execute(){
        requestConnection()
        val commStatus = communicateWithServer()
        println(commStatus)
        socket.close()
    }

    private fun communicateWithServer(): String{
        val commStatus = requestFile()
        if (commStatus == Protocol.ERR.msg) return communicateWithServer()
        else return commStatus
    }

    /**
     * Sends an empty message to establish a connection with the server.
     */
    private fun requestConnection(){
        socket.send(packageMessage(ByteArray(1){ x -> 1}, mainAddress, mainPort))
    }

    /**
     * Performs the protocol of communication with the server.
     * Returs ERR if the protocol failed or OK otherwise.
     */
    private fun requestFile(): String{
        println("Connected to Server: $socket")

        // First message
        // Receive file names and sizes
        val serverReadyBytes = ByteArray(bufferStandardSize)
        val serverReadyPack = DatagramPacket(serverReadyBytes, bufferStandardSize)
        socket.receive(serverReadyPack)
        val serverReadyMsg = serverReadyBytes.toString(charset).split(" ")

        // Initialize new dedicated channel for transfer
        address = serverReadyPack.address
        port = serverReadyPack.port

        val serverSizesMsg = cleanByteArray(unpackageMessage(ByteArray(bufferStandardSize))).toString(charset).split(" ")

        if (serverReadyMsg[0] != Protocol.READY.msg || serverSizesMsg[0] != Protocol.SIZES.msg) return Protocol.ERR.msg
        else{
            // Display file information
            val fileNames = serverReadyMsg[1].split(";")
            val fileSizes = serverSizesMsg[1].split(";")
            showFilesFromConsole(fileNames, fileSizes)

            // Ask the user for a file
            val fileId = fileSelectorFun()
            socket.send(packageMessage("${Protocol.SEND.msg} $fileId"))

            print("before numpacks")
            val numPacksMsg = cleanByteArray(unpackageMessage(ByteArray(bufferStandardSize))).toString(charset).split(" ")
            println(numPacksMsg)

            if (numPacksMsg[0] != Protocol.NUM.msg) return Protocol.ERR.msg
            else{

                // Number of packages to be sent by the server and file size
                val numPacks = numPacksMsg[1].toLong()
                val fileSize = fileSizes[fileId - 1].toLong()

                // Buffered stream used to write the file in the given path
                val bos = BufferedOutputStream(FileOutputStream("$fileDestination/${fileNames[fileId - 1]}"))

                // Receive hash
                val receivedHash = cleanByteArray(unpackageMessage(ByteArray(bufferStandardSize))).toString(charset)

                // Receive file
                receiveFile(bos, numPacks, fileSize)
                bos.close()

                // Integrity Check
                val fileBytes = File("$fileDestination/${fileNames[fileId - 1]}").readBytes()
                val calculatedHash = Hashing("MD5").hashInput(fileBytes)
                val integrityCheck = if (receivedHash == calculatedHash)Protocol.OK.msg else Protocol.ERR.msg
                socket.send(packageMessage(integrityCheck))

                return "Integrity Check: $integrityCheck"
            }
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


    private fun receiveFile(bos: BufferedOutputStream, numPacks: Long, fileSize: Long){
        var currPack = 1L
        var receivedBytes = 0L
        val before = System.currentTimeMillis()

        while (currPack <= numPacks){

            var buff = bufferStandardSize
            if (fileSize - receivedBytes >= buff) receivedBytes += buff
            else{
                buff = (fileSize - receivedBytes).toInt()
                receivedBytes = fileSize
            }
            // Receive bytes from server
            val content = unpackageMessage(ByteArray(buff))

            // Write file
            bos.write(content, 0, buff)
            socket.send(packageMessage(Protocol.OK.msg))

            currPack ++
            println("Process ${(receivedBytes*100.0/fileSize)}% complete")

        }
        val after = System.currentTimeMillis()

        println("Process finished successfully in ${after - before} ms")
    }

    /**
     * Packages a message string into a Datagram Package.
     */
    private fun packageMessage(msg: String): DatagramPacket {
        val msgBytes = msg.toByteArray()
        return DatagramPacket(msgBytes, msgBytes.size, address, port)
    }

    /**
     * Packages a message in bytes into a Datagram Package.
     */
    private fun packageMessage(msg: ByteArray): DatagramPacket =
        DatagramPacket(msg, msg.size, address, port)

    private fun packageMessage(msg: ByteArray, address: InetAddress, port: Int) =
        DatagramPacket(msg, msg.size, address, port)

    /**
     * Un-packages a message in the given buffer
     */
    private fun unpackageMessage(toReceive: ByteArray): ByteArray {
        socket.receive(DatagramPacket(toReceive, toReceive.size, address, port))
        return toReceive
    }

    private fun showFilesFromConsole(filesNames: List<String>, filesSizes: List<String>){

        for (i in 0 until filesNames.size){
            println("${i+1}) File Name: ${filesNames[i]} | File Size: ${filesSizes[i]}")
        }
    }
}

fun run(fileDestination: String, fileSelectorFun: () -> Int) =
        Client(DatagramSocket(), 4445, InetAddress.getLocalHost(), fileDestination, fileSelectorFun)
                .execute()

fun stdinSelection(): Int = BufferedReader(InputStreamReader(System.`in`)).readLine().toInt()

fun main() {
    run(filesPath, ::stdinSelection)
}