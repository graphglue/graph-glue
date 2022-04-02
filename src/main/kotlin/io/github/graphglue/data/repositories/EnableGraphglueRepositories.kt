package io.github.graphglue.data.repositories

import io.github.graphglue.data.GRAPHGLUE_NEO4J_OPERATIONS_BEAN_NAME
import org.springframework.data.neo4j.repository.config.EnableReactiveNeo4jRepositories
import java.lang.annotation.Inherited

/**
 * Annotation to activate reactive Neo4j repositories with the necessary default configuration
 * for graph-glue
 * Alternatively, add [EnableReactiveNeo4jRepositories] with neo4jTemplateRef = GRAPHGLUE_NEO4J_OPERATIONS_BEAN_NAME
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
@EnableReactiveNeo4jRepositories(neo4jTemplateRef = GRAPHGLUE_NEO4J_OPERATIONS_BEAN_NAME)
annotation class EnableGraphglueRepositories
