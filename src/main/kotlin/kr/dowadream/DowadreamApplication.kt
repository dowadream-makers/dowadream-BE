package kr.dowadream

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DowadreamApplication

fun main(args: Array<String>) {
    runApplication<DowadreamApplication>(*args)
}
