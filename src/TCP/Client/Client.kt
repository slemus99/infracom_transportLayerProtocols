package TCP.Client

import TCP.Server.Hashing
import java.io.*
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

val filesPath = "./src/TCP/Client/data"

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

data class Client(val port: Int, val server: String){

    val socket = Socket(server, port)

    // Form Standard Input
    val stdIn = BufferedReader(InputStreamReader(System.`in`))

    fun clientProcess(){
        var result = requestFile()
        while (result == Protocol.ERR.msg){
            result = requestFile()
        }
    }

    fun requestFile(): String{

        println("Connected to Server: $socket")

        // Streams used to receive the file
        val ins = socket.getInputStream()

        // Streams used to send messages
        val br = BufferedReader(InputStreamReader(ins))
        val pw = PrintWriter(socket.getOutputStream(), true)

        // Files available in the server
        val serverReadyMsg = br.readLine().split(" ")
        val serverSizesMsg = br.readLine().split(" ")

        if (serverReadyMsg[0] != Protocol.READY.msg || serverSizesMsg[0] != Protocol.SIZES.msg){
            pw.println(Protocol.ERR.msg)
            br.close()
            pw.close()
            socket.close()
            return Protocol.ERR.msg
        }else {

            val fns = serverReadyMsg[1].split(";")
            val fss = serverSizesMsg[1].split(";")
            showFilesFromConsole(fns, fss)
            val fileId = selectFileFromConsole()

            val bos = BufferedOutputStream(FileOutputStream("$filesPath/${fns[fileId - 1]}"))

            pw.println("${Protocol.SEND.msg} $fileId")

            // receives the hash of the file for the integrity check
            val hash = br.readLine().split(" ")[1]

            // receive and save the file
            receiveFile(bos, ins)
            bos.flush()


            // Verifies the hash
            val fileBytes = File("$filesPath/${fns[fileId - 1]}").readBytes()
            val calculatedHash = Hashing("MD5").hashInput(fileBytes)

            if (calculatedHash == hash){
                pw.println(Protocol.OK.msg)
                println("Integrity check: ${Protocol.OK.msg}")
                br.close()
                pw.close()
                socket.close()
                return Protocol.OK.msg
            }else{
                pw.println(Protocol.ERR.msg)
                println("Integrity check: ${Protocol.ERR.msg}")
                br.close()
                pw.close()
                socket.close()
                return Protocol.ERR.msg
            }
        }
    }

    private fun receiveFile(bos: BufferedOutputStream, ins: InputStream){
        val contents = ByteArray(10001)
        var curr = ins.read(contents)

        val before = System.currentTimeMillis()
        while (contents[0] != 1.toByte()){
            bos.write(contents, 1, curr - 1)
            curr = ins.read(contents)
            println(curr)
        }
        bos.write(contents, 1, curr - 1)

        val after = System.currentTimeMillis()

        println("Process finished successfully in ${after - before} ms")
    }

    private fun showFilesFromConsole(filesNames: List<String>, filesSizes: List<String>){

        for (i in 0 until filesNames.size){
            println("${i+1}) File Name: ${filesNames[i]} | File Size: ${filesSizes[i]}")
        }
    }

    private fun selectFileFromConsole(): Int{
        println("Select one of the above files typing its number (id): ")
        return stdIn.readLine().toInt()
    }
}

fun main(args: Array<String>) {
    val c = Client(3400, "localhost")
    c.requestFile()
}