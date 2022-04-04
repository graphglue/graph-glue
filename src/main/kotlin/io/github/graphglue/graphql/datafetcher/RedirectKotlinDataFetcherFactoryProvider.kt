package io.github.graphglue.graphql.datafetcher

import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import com.expediagroup.graphql.server.spring.execution.SpringKotlinDataFetcherFactoryProvider
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactory
import io.github.graphglue.model.BaseProperty
import io.github.graphglue.model.Node
import org.springframework.context.ApplicationContext
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField

/**
 * [KotlinDataFetcherFactoryProvider] which provides a [DataFetcherFactory] for properties which automatically
 * redirects properties based on delegated [BaseProperty]s
 * Delegates those data fetches to the `getFromGraphQL` function of the delegate obtained from
 * [Node.propertyLookup]
 *
 * @param objectMapper necessary for the delegated [SpringKotlinDataFetcherFactoryProvider]
 * @param applicationContext used for the delegated [SpringKotlinDataFetcherFactoryProvider]
 * @param delegate the [KotlinDataFetcherFactoryProvider] used as delegate, defaults to a [SpringKotlinDataFetcherFactoryProvider]
 */
class RedirectKotlinDataFetcherFactoryProvider(
    private val objectMapper: ObjectMapper,
    private val applicationContext: ApplicationContext,
    private val delegate: KotlinDataFetcherFactoryProvider = SpringKotlinDataFetcherFactoryProvider(
        objectMapper, applicationContext
    )
) : KotlinDataFetcherFactoryProvider by delegate {

    /**
     * Property data fetcher factory which uses [createBasePropertyDataFetcherFactory] of the property is
     * backed by a delegated [BaseProperty], otherwise uses [delegate] to resolve the data fetcher factory
     *
     * @param kClass parent class that contains this property
     * @param kProperty Kotlin property that should be resolved
     * @return the created [DataFetcherFactory] for the property
     */
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

    /**
     * Creates a [DataFetcherFactory] for a [BaseProperty] backed property which uses [Node.propertyLookup]
     * to get the delegate, and then use [BaseProperty.getFromGraphQL] for data fetching
     *
     * @param kProperty Kotlin property that should be resolved
     * @return the created [DataFetcherFactory] for the property
     */
    private fun createBasePropertyDataFetcherFactory(kProperty: KProperty1<*, *>): DataFetcherFactory<Any?> {
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

