import java.io.BufferedReader
import java.io.InputStreamReader

class Runner {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val mode = args[0]
            when(mode) {
                "client" -> {
                    val destination = args[1]
                    Client.run(destination, ::selectFileFromConsole)
                }
                "server" -> Server.run()
                "clientHeadless" -> {
                    val destination = args[1]
                    val fileToSelect = args[2].toInt()
                    Client.run(destination){ fileToSelect }
                }
            }
        }

        fun selectFileFromConsole(): Int{
            println("Select one of the above files typing its number (id): ")
            return BufferedReader(InputStreamReader(System.`in`)).readLine().toInt()
        }

    }
}