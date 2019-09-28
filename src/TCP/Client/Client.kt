package TCP.Client

import java.io.*
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

val filesPath = "./src/TCP/Client/data"

data class Client(val port: Int, val server: String){

    val socket = Socket(server, port)

    // Form Standard Input
    val stdIn = BufferedReader(InputStreamReader(System.`in`))

    public fun requestFile(){

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
        }else{

            val fns = serverReadyMsg[1].split(";")
            val fss = serverSizesMsg[1].split(";")
            showFilesFromConsole(fns, fss)
            val fileId = selectFileFromConsole()

            val bos = BufferedOutputStream(FileOutputStream("$filesPath/${fns[fileId - 1]}"))

            pw.println("${Protocol.SEND.msg} $fileId")

            // receive and save the file
            receiveFile(bos, ins)

            bos.flush()
        }

        socket.close()
    }

    private fun receiveFile(bos: BufferedOutputStream, ins: InputStream){
        val contents = ByteArray(10000)
        var curr = ins.read(contents)

        val before = System.currentTimeMillis()
        while (curr != -1){
            bos.write(contents, 0, curr)
            curr = ins.read(contents)
        }
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