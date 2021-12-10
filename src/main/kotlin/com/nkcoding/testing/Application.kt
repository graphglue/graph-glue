package com.nkcoding.testing

import com.expediagroup.graphql.generator.SchemaGenerator
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.nkcoding.graphglue.GraphglueAutoConfiguration
import com.nkcoding.testing.model.Root
import com.nkcoding.testing.schema.Query
import graphql.schema.GraphQLSchema
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import kotlin.reflect.full.createType

@SpringBootApplication
@Import(GraphglueAutoConfiguration::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Bean
fun getSchema(config: SchemaGeneratorConfig): GraphQLSchema {
    val generator = SchemaGenerator(config)
    return generator.generateSchema(
    queries = listOf(TopLevelObject(Query::class)),
        additionalTypes = setOf(Root::class.createType())
    )
}
