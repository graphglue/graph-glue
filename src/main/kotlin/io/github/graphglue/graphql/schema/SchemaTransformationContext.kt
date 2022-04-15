package io.github.graphglue.graphql.schema

import graphql.schema.DataFetcherFactory
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldsContainer
import graphql.util.TraverserContext
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

class SchemaTransformationContext(
    context: TraverserContext<*>, schemaTransformer: SchemaTransformer
) : SchemaTransformer by schemaTransformer {
    /**
     * Code Registry to register new [DataFetcher]s
     */
    val codeRegistry = context.getVarFromParents(GraphQLCodeRegistry.Builder::class.java)!!

    /**
     * Registers a data fetcher for a specific property
     *
     * @param type the container type on which to register the data fetcher
     * @param fieldName the name of the property
     * @param kClass the class which contains the property to use for data fetching
     */
    fun registerPropertyDataFetcher(type: GraphQLFieldsContainer, fieldName: String, kClass: KClass<*>) {
        val property = kClass.memberProperties.first { it.name == fieldName }
        val dataFetcherFactory = dataFetcherFactoryProvider.propertyDataFetcherFactory(kClass, property)
        registerDataFetcher(type, fieldName, dataFetcherFactory)
    }

    /**
     * Registers a data fetcher for a specific function
     *
     * @param type the container type on which to register the data fetcher
     * @param fieldName the name of the function
     * @param kClass the class which contains the function to use for data fetching
     */
    fun registerFunctionDataFetcher(type: GraphQLFieldsContainer, fieldName: String, kClass: KClass<*>) {
        val function = kClass.memberFunctions.first { it.name == fieldName }
        val dataFetcherFactory = dataFetcherFactoryProvider.functionDataFetcherFactory(null, function)
        registerDataFetcher(type, fieldName, dataFetcherFactory)
    }

    /**
     * Registers a [DataFetcherFactory] for a specific [fieldName] on a specific [GraphQLFieldsContainer]
     *
     * @param type the container for which to register the data fetcher
     * @param fieldName the name of the field for which to register the data fetcher
     * @param dataFetcherFactory the data fetcher to register
     */
    private fun registerDataFetcher(
        type: GraphQLFieldsContainer, fieldName: String, dataFetcherFactory: DataFetcherFactory<Any?>
    ) {
        val fieldCoordinates = FieldCoordinates.coordinates(type, fieldName)
        codeRegistry.dataFetcher(fieldCoordinates, dataFetcherFactory)
    }
}