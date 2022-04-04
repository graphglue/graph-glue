package io.github.graphglue.graphql.extensions

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.AnnotationUtils
import kotlin.reflect.KClass

/**
 * Uses [AnnotationUtils.findAnnotation] to find an annotation of type [A]
 *
 * @param A the type of the [Annotation] to find
 * @return the found [Annotation] or `null` if not found
 */
inline fun <reified A : Annotation> KClass<*>.springFindAnnotation(): A? {
    return AnnotationUtils.findAnnotation(this.java, A::class.java)
}

/**
 * Uses [AnnotatedElementUtils.findMergedRepeatableAnnotations] to find all repeatable annotations of type [A]
 *
 * @param A the type of the [Annotation]s to find
 * @return the found [Annotation]s (can be empty)
 */
inline fun <reified A : Annotation> KClass<*>.springFindRepeatableAnnotations(): Collection<A> {
    return AnnotatedElementUtils.findMergedRepeatableAnnotations(this.java, A::class.java)
}