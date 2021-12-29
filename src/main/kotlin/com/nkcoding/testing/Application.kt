package com.nkcoding.testing

import com.nkcoding.graphglue.GraphglueAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(GraphglueAutoConfiguration::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
