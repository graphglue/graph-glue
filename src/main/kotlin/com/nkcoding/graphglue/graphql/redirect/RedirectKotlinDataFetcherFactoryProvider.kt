package com.nkcoding.graphglue.graphql.redirect

import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import com.expediagroup.graphql.server.spring.execution.SpringDataFetcher
import com.expediagroup.graphql.server.spring.execution.SpringKotlinDataFetcherFactoryProvider
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.ApplicationContext
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
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
                if (type.hasAnnotation<RedirectPropertyDelegateClass>()) {
                    return createRedirectDataFetcherFactory(type, kProperty)
                }
            }
        }
        return delegate.propertyDataFetcherFactory(kClass, kProperty)
    }

    private fun createRedirectDataFetcherFactory(
        type: KClass<out Any>,
        kProperty: KProperty1<*, *>
    ): DataFetcherFactory<Any?> {
        val function = type.memberFunctions.first {
            it.hasAnnotation<RedirectPropertyFunction>()
        }
        val functionDataFetcherFactory = delegate.functionDataFetcherFactory(null, function)
        return DataFetcherFactory { dataFetcherFactoryEnvironment ->
            val functionDataFetcher = functionDataFetcherFactory.get(dataFetcherFactoryEnvironment)
            DataFetcher {
                val environment = DelegateDataFetchingEnvironment(it, kProperty)
                functionDataFetcher.get(environment)
            }
        }
    }
}

private class DelegateDataFetchingEnvironment(
    private val parent: DataFetchingEnvironment, private val property: KProperty1<*, *>
) : DataFetchingEnvironment by parent {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSource(): T {
        property as KProperty1<Any, *>
        property.isAccessible = true
        return property.getDelegate(parent.getSource()) as T
    }
}