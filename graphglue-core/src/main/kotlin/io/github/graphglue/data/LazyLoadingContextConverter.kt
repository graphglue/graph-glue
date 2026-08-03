package io.github.graphglue.data

import io.github.graphglue.model.Node
import org.neo4j.driver.Value
import org.neo4j.driver.Values
import org.springframework.data.neo4j.core.convert.Neo4jPersistentPropertyConverter

/**
 * Converter declared on [Node.lazyLoadingContext] so that Spring Data Neo4j does not classify that
 * property as an association.
 *
 * Spring Data Neo4j decides "association or property" purely by whether the driver can write the
 * type, and `AbstractMappingContext` resolves an association's target type *before* it honours
 * `@Transient`. Without this, [LazyLoadingContext] - and transitively the whole internal Graphglue
 * object graph - would be mapped as entities and the mapping context would fail to build.
 *
 * It is never invoked, because the property it is declared on is transient.
 */
class LazyLoadingContextConverter : Neo4jPersistentPropertyConverter<LazyLoadingContext> {

    override fun write(source: LazyLoadingContext?): Value = Values.NULL

    override fun read(source: Value?): LazyLoadingContext? = null
}
