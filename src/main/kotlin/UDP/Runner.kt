package UDP

import java.io.BufferedReader
import java.io.InputStreamReader

import UDP.Client.run as clientRun
import UDP.Server.run as serverRun

class Runner {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val mode = args[0]
            when(mode) {
                "client" -> {
                    val destination = args[1]
                    clientRun(destination, ::selectFileFromConsole)
                }
                "server" -> serverRun()
                "clientHeadless" -> {
                    val destination = args[1]
                    val fileToSelect = args[2].toInt()
                    clientRun(destination){ fileToSelect }
                }
            }
        }

        fun selectFileFromConsole(): Int{
            println("Select one of the above files typing its number (id): ")
            return BufferedReader(InputStreamReader(System.`in`)).readLine().toInt()
        }

    }
}