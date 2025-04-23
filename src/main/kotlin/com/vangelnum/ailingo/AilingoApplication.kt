package com.vangelnum.ailingo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties
class AilingoApplication

fun main(args: Array<String>) {
	runApplication<AilingoApplication>(*args)
}