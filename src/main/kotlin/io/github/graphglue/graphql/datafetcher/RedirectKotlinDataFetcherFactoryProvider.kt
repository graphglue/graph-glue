package io.github.graphglue.graphql.datafetcher

import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import com.expediagroup.graphql.server.spring.execution.SpringKotlinDataFetcherFactoryProvider
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.graphglue.model.BaseProperty
import io.github.graphglue.model.Node
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactory
import org.springframework.context.ApplicationContext
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField

class RedirectKotlinDataFetcherFactoryProvider(
    private val objectMapper: ObjectMapper,
    private val applicationContext: ApplicationContext,
    private val delegate: KotlinDataFetcherFactoryProvider = SpringKotlinDataFetcherFactoryProvider(
        objectMapper, applicationContext
    )
) : KotlinDataFetcherFactoryProvider by delegate {

    override fun propertyDataFetcherFactory(kClass: KClass<*>, kProperty: KProperty<*>): DataFetcherFactory<Any?> {
        if (kProperty is KProperty1<*, *>) {
            val field = kProperty.javaField
            if (field != null) {
                val type = field.type.kotlin
                if (type.isSubclassOf(BaseProperty::class)) {
                    return createBasePropertyDataFetcherFactory(kProperty)
                }
            }
        }
        return delegate.propertyDataFetcherFactory(kClass, kProperty)
    }

    private fun createBasePropertyDataFetcherFactory(
        kProperty: KProperty1<*, *>
    ): DataFetcherFactory<Any?> {
        val functionDataFetcherFactory = delegate.functionDataFetcherFactory(null, BaseProperty<*>::getFromGraphQL)
        return DataFetcherFactory { dataFetcherFactoryEnvironment ->
            val functionDataFetcher = functionDataFetcherFactory.get(dataFetcherFactoryEnvironment)
            DataFetcher {
                val node = it.getSource<Node>()
                val environment = DelegateDataFetchingEnvironment(it, node.propertyLookup[kProperty]!!)
                functionDataFetcher.get(environment)
            }
        }
    }
}

