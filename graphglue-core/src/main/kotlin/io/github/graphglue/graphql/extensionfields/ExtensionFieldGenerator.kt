package io.github.graphglue.graphql.extensionfields

import graphql.language.FieldDefinition
import graphql.schema.DataFetchingEnvironment
import kotlin.reflect.KType

abstract class ExtensionFieldGenerator(fieldDefinition: FieldDefinition) {

    abstract fun generateFetcher(nodeType: KType, dfe: DataFetchingEnvironment)

}