package com.nkcoding.graphglue.graphql.extensions

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.AnnotationUtils
import kotlin.reflect.KClass

inline fun <reified A : Annotation> KClass<*>.springFindAnnotation(): A? {
    return AnnotationUtils.findAnnotation(this.java, A::class.java)
}

inline fun <reified A : Annotation> KClass<*>.springFindRepeatableAnnotations(): Collection<A> {
    return AnnotatedElementUtils.findMergedRepeatableAnnotations(this.java, A::class.java)
}