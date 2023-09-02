package io.github.graphglue.graphql.schema

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import io.github.graphglue.connection.filter.definition.FilterDefinitionCollection
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.connection.generateConnectionFieldDefinition
import io.github.graphglue.connection.generateSearchFieldDefinition
import io.github.graphglue.definition.ExtensionFieldDefinition
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.extensions.springFindAnnotation
import io.github.graphglue.graphql.query.TopLevelQueryProvider
import io.github.graphglue.model.DomainNode
import io.github.graphglue.model.Node
import io.github.graphglue.model.property.BasePropertyDelegate
import io.github.graphglue.util.CacheMap
import org.neo4j.cypherdsl.core.Cypher
import org.neo4j.cypherdsl.core.renderer.Renderer
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions

/**
 * Default implementation of [SchemaTransformer]
 * The transformation result can be found in [schema]
 * The generated filter collection can be found in [filterDefinitionCollection]
 *
 * @param oldSchema the schema to transform
 * @param mappingContext mapping context used to get type information from Neo4j
 * @param nodeDefinitionCollection collection of all [NodeDefinition]s
 * @param dataFetcherFactoryProvider provides function and property data fetchers
 * @param subFilterGenerator used to generate the filter entries
 * @param includeSkipField if true, connections provide the non-standard skip field
 * @param reactiveNeo4jClient used to execute Cypher queries
 */
class DefaultSchemaTransformer(
    private val oldSchema: GraphQLSchema,
    override val mappingContext: Neo4jMappingContext,
    override val nodeDefinitionCollection: NodeDefinitionCollection,
    override val dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider,
    override val subFilterGenerator: SubFilterGenerator,
    override val includeSkipField: Boolean,
    private val reactiveNeo4jClient: ReactiveNeo4jClient
) : SchemaTransformer {

    override val inputTypeCache = CacheMap<String, GraphQLInputType>()
    override val outputTypeCache = CacheMap<String, GraphQLOutputType>()
    override val schema: GraphQLSchema
    override val filterDefinitionCollection: FilterDefinitionCollection

    /**
     * Lookup from [NodeDefinition] graphql name to [NodeDefinition]
     */
    private val nodeDefinitionLookup = nodeDefinitionCollection.associateBy { it.name }

    /**
     * Visitor used to transform the GraphQL schema
     * Transforms the query type, and all [Node] types and interfaces
     */
    private val schemaTransformationVisitor = object : GraphQLTypeVisitorStub() {
        override fun visitGraphQLObjectType(
            node: GraphQLObjectType, context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            val transformationContext = SchemaTransformationContext(
                context, this@DefaultSchemaTransformer
            )

            return if (node == oldSchema.queryType) {
                changeNode(context, updateQueryType(node, transformationContext))
            } else if (node.name in nodeDefinitionLookup) {
                changeNode(
                    context, updateObjectNodeType(node, nodeDefinitionLookup[node.name]!!, transformationContext)
                )
            } else {
                super.visitGraphQLObjectType(node, context)
            }
        }

        override fun visitGraphQLInterfaceType(
            node: GraphQLInterfaceType, context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            val transformationContext = SchemaTransformationContext(
                context, this@DefaultSchemaTransformer
            )

            return if (node.name in nodeDefinitionLookup) {
                changeNode(
                    context, updateInterfaceNodeType(node, nodeDefinitionLookup[node.name]!!, transformationContext)
                )
            } else {
                super.visitGraphQLInterfaceType(node, context)
            }
        }
    }

    init {
        schema = this.completeSchema()
        filterDefinitionCollection = FilterDefinitionCollection(subFilterGenerator.filterDefinitionCache)
    }

    /**
     * Adds the missing connection like queries for [Node] types declared using the [DomainNode] annotation
     *
     * @return a builder for the new schema with the additional queries
     */
    private fun completeSchema(): GraphQLSchema {
        return graphql.schema.SchemaTransformer.transformSchema(oldSchema, schemaTransformationVisitor)
    }

    /**
     * Updates an [GraphQLObjectType], by adding all fields defined by [NodeDefinition.relationshipDefinitions]
     *
     * @param type the GraphQL type to modify
     * @param nodeDefinition the associated [NodeDefinition] used to retrieve relationship information
     * @param context context of the transformation, used to access necessary caches, builders etc.
     * @return the updated type
     */
    private fun updateObjectNodeType(
        type: GraphQLObjectType, nodeDefinition: NodeDefinition, context: SchemaTransformationContext
    ): GraphQLObjectType {
        return type.transform {
            for (relationshipDefinition in nodeDefinition.relationshipDefinitions.values) {
                if (relationshipDefinition.isGraphQLVisible) {
                    val field = relationshipDefinition.generateFieldDefinition(context)
                    it.field(field)
                    registerRelationshipDataFetcher(relationshipDefinition, context, nodeDefinition)
                }
            }
            for (extensionFieldDefinition in nodeDefinition.extensionFieldDefinitions.values) {
                it.field(extensionFieldDefinition.field)
                registerExtensionFieldDataFetcher(extensionFieldDefinition, context, nodeDefinition)
            }
            getNodeInterfaces(nodeDefinition).forEach(it::withInterface)
        }
    }

    /**
     * Updates an [GraphQLInterfaceType], by adding all fields defined by [NodeDefinition.relationshipDefinitions]
     *
     * @param type the GraphQL type to modify
     * @param nodeDefinition the associated [NodeDefinition] used to retrieve relationship information
     * @param context context of the transformation, used to access necessary caches, builders etc.
     * @return the updated type
     */
    private fun updateInterfaceNodeType(
        type: GraphQLInterfaceType, nodeDefinition: NodeDefinition, context: SchemaTransformationContext
    ): GraphQLInterfaceType {
        return type.transform {
            for (relationshipDefinition in nodeDefinition.relationshipDefinitions.values) {
                val field = relationshipDefinition.generateFieldDefinition(context)
                it.field(field)
                registerRelationshipDataFetcher(relationshipDefinition, context, nodeDefinition)
            }
            for (extensionFieldDefinition in nodeDefinition.extensionFieldDefinitions.values) {
                it.field(extensionFieldDefinition.field)
                registerExtensionFieldDataFetcher(extensionFieldDefinition, context, nodeDefinition)
            }
            getNodeInterfaces(nodeDefinition).forEach(it::withInterface)
        }
    }

    /**
     * Gets a set of all valid [Node] supertypes for a [NodeDefinition]
     *
     * @param nodeDefinition used to find valid superclasses
     * @return a set with [GraphQLTypeReference]s for all found valid superclasses
     */
    private fun getNodeInterfaces(nodeDefinition: NodeDefinition): Set<GraphQLTypeReference> {
        return nodeDefinition.nodeType.allSuperclasses.filter {
            it.isSubclassOf(Node::class) && !it.hasAnnotation<GraphQLIgnore>()
        }.map {
            GraphQLTypeReference(it.getSimpleName())
        }.toSet()
    }

    /**
     * Registers a DataFetcher for a [RelationshipDefinition]
     * Creates a new [DataFetcherFactory] and registers it to the [GraphQLCodeRegistry]
     * Uses the [Node.propertyLookup] to access the delegate for the property
     *
     * @param relationshipDefinition the definition of the relationship to create the data fetcher for
     * @param context provides the [GraphQLCodeRegistry]
     * @param nodeDefinition the parent [NodeDefinition] of the relationship
     */
    private fun registerRelationshipDataFetcher(
        relationshipDefinition: RelationshipDefinition,
        context: SchemaTransformationContext,
        nodeDefinition: NodeDefinition
    ) {
        val kProperty = relationshipDefinition.property
        val functionDataFetcherFactory =
            dataFetcherFactoryProvider.functionDataFetcherFactory(null, BasePropertyDelegate<*, *>::getFromGraphQL)
        val dataFetcherFactory = DataFetcherFactory { dataFetcherFactoryEnvironment ->
            val functionDataFetcher = functionDataFetcherFactory.get(dataFetcherFactoryEnvironment)
            DataFetcher {
                val node = it.getSource<Node>()
                val environment = DelegateDataFetchingEnvironment(it, node.getProperty<Node>(kProperty))
                functionDataFetcher.get(environment)
            }
        }
        val coordinates = FieldCoordinates.coordinates(nodeDefinition.name, relationshipDefinition.graphQLName)
        context.codeRegistry.dataFetcher(coordinates, dataFetcherFactory)
    }

    /**
     * Registers a DataFetcher for a [ExtensionFieldDefinition]
     *
     * @param extensionFieldDefinition the definition of the extension field to create the data fetcher for
     * @param context provides the [GraphQLCodeRegistry]
     * @param nodeDefinition the parent [NodeDefinition] of the extension field
     */
    private fun registerExtensionFieldDataFetcher(
        extensionFieldDefinition: ExtensionFieldDefinition,
        context: SchemaTransformationContext,
        nodeDefinition: NodeDefinition
    ) {
        val dataFetcherFactory = DataFetcherFactory { _ ->
            DataFetcher {
                val node = it.getSource<Node>()
                val extensionFields = node.extensionFields
                if (extensionFields != null) {
                    return@DataFetcher extensionFields[it.field.resultKey]
                } else {
                    val nodeName = Cypher.name("a_node")
                    val cypherNode = nodeDefinition.node().named(nodeName)
                        .withProperties(mapOf("id" to Cypher.parameter("a_id", node.rawId!!)))
                    val expression =
                        extensionFieldDefinition.generateFetcher(it, it.arguments, cypherNode, nodeDefinition)
                    val resultName = "a_result"
                    val statement = Cypher.match(cypherNode).returning(expression.`as`(resultName)).build()
                    val queryResult = reactiveNeo4jClient.query(Renderer.getDefaultRenderer().render(statement))
                        .bindAll(statement.catalog.parameters)
                    return@DataFetcher queryResult.fetchAs(Any::class.java).mappedBy { _, record ->
                        extensionFieldDefinition.transformResult(record[resultName])
                    }.one().toFuture()
                }
            }
        }
        val coordinates = FieldCoordinates.coordinates(nodeDefinition.name, extensionFieldDefinition.graphQLName)
        context.codeRegistry.dataFetcher(coordinates, dataFetcherFactory)
    }

    /**
     * Adds the missing connection like queries for [Node] types declared using the [DomainNode] annotation
     *
     * @param queryType the existing query type
     * @param context used to access the [GraphQLCodeRegistry.Builder]
     * @return the new query type
     */
    private fun updateQueryType(queryType: GraphQLObjectType, context: SchemaTransformationContext): GraphQLObjectType {
        val newQueryType = queryType.transform {
            for ((nodeDefinition, queryName) in topLevelQueries) {
                val nodeClass = nodeDefinition.nodeType
                val field = generateConnectionFieldDefinition(
                    nodeClass, queryName, "Query for nodes of type ${nodeClass.getSimpleName()}", context
                )
                it.field(field)
                val coordinates = FieldCoordinates.coordinates(queryType.name, queryName)
                val dataFetcherFactory = dataFetcherFactoryProvider.functionDataFetcherFactory(
                    TopLevelQueryProvider<Node>(nodeDefinition), TopLevelQueryProvider<*>::getNodeQuery
                )
                context.codeRegistry.dataFetcher(coordinates, dataFetcherFactory)
            }
            for ((nodeDefinition, queryName) in searchQueries) {
                val nodeClass = nodeDefinition.nodeType
                val field = generateSearchFieldDefinition(
                    nodeClass, queryName, "Search for nodes of type ${nodeClass.getSimpleName()}", context
                )
                it.field(field)
                val coordinates = FieldCoordinates.coordinates(queryType.name, queryName)
                val dataFetcherFactory = dataFetcherFactoryProvider.functionDataFetcherFactory(
                    TopLevelQueryProvider<Node>(nodeDefinition), TopLevelQueryProvider<*>::getSearchQuery
                )
                context.codeRegistry.dataFetcher(coordinates, dataFetcherFactory)
            }
        }
        return newQueryType
    }

    /**
     * Gets all [NodeDefinition] which define a top level query
     * The name of the query is provided as value of the map
     */
    private val topLevelQueries: Map<NodeDefinition, String>
        get() = nodeDefinitionCollection.mapNotNull {
            val nodeClass = it.nodeType
            val domainNodeAnnotation = nodeClass.springFindAnnotation<DomainNode>()
            val topLevelFunctionName = domainNodeAnnotation?.topLevelQueryName
            if (topLevelFunctionName?.isNotBlank() == true) {
                it to topLevelFunctionName
            } else {
                null
            }
        }.toMap()

    /**
     * Gets all [NodeDefinition] which define a search query
     * The name of the query is provided as value of the map
     */
    private val searchQueries: Map<NodeDefinition, String>
        get() = nodeDefinitionCollection.mapNotNull {
            val nodeClass = it.nodeType
            val domainNodeAnnotation = nodeClass.springFindAnnotation<DomainNode>()
            val searchFunctionName = domainNodeAnnotation?.searchQueryName
            if (searchFunctionName?.isNotBlank() == true) {
                it to searchFunctionName
            } else {
                null
            }
        }.toMap()
}