package io.github.graphglue.graphql

import com.expediagroup.graphql.generator.execution.KotlinDataFetcherFactoryProvider
import graphql.schema.*
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import io.github.graphglue.connection.filter.definition.FilterDefinitionCollection
import io.github.graphglue.connection.filter.definition.SubFilterGenerator
import io.github.graphglue.connection.generateConnectionFieldDefinition
import io.github.graphglue.definition.NodeDefinition
import io.github.graphglue.definition.NodeDefinitionCollection
import io.github.graphglue.definition.RelationshipDefinition
import io.github.graphglue.graphql.datafetcher.DelegateDataFetchingEnvironment
import io.github.graphglue.graphql.extensions.getSimpleName
import io.github.graphglue.graphql.extensions.springFindAnnotation
import io.github.graphglue.graphql.query.TopLevelQueryProvider
import io.github.graphglue.model.BaseProperty
import io.github.graphglue.model.DomainNode
import io.github.graphglue.model.Node
import io.github.graphglue.util.CacheMap
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

interface SchemaTransformer {
    val mappingContext: Neo4jMappingContext
    val nodeDefinitionCollection: NodeDefinitionCollection
    val dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider
    val subFilterGenerator: SubFilterGenerator

    /**
     * Cache for [GraphQLInputType]s
     */
    val inputTypeCache: CacheMap<String, GraphQLInputType>

    /**
     * Cache for [GraphQLOutputType]s
     */
    val outputTypeCache: CacheMap<String, GraphQLOutputType>

    val schema: GraphQLSchema

    val filterDefinitionCollection: FilterDefinitionCollection
}

class DefaultSchemaTransformer(
    private val oldSchema: GraphQLSchema,
    override val mappingContext: Neo4jMappingContext,
    override val nodeDefinitionCollection: NodeDefinitionCollection,
    override val dataFetcherFactoryProvider: KotlinDataFetcherFactoryProvider,
    override val subFilterGenerator: SubFilterGenerator
) : SchemaTransformer {
    /**
     * Cache for [GraphQLInputType]s
     */
    override val inputTypeCache = CacheMap<String, GraphQLInputType>()

    /**
     * Cache for [GraphQLOutputType]s
     */
    override val outputTypeCache = CacheMap<String, GraphQLOutputType>()

    override val schema: GraphQLSchema

    override val filterDefinitionCollection: FilterDefinitionCollection

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
        val nodeDefinitionLookup = nodeDefinitionCollection.associateBy { it.name }
        return graphql.schema.SchemaTransformer.transformSchema(oldSchema, object : GraphQLTypeVisitorStub() {
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
        })
    }

    private fun updateObjectNodeType(
        type: GraphQLObjectType, nodeDefinition: NodeDefinition, context: SchemaTransformationContext
    ): GraphQLObjectType {
        return type.transform {
            for (relationshipDefinition in nodeDefinition.relationshipDefinitions.values) {
                val field = relationshipDefinition.generateFieldDefinition(context)
                it.field(field)
                registerRelationshipDataFetcher(relationshipDefinition, context)
            }
        }
    }

    private fun updateInterfaceNodeType(
        type: GraphQLInterfaceType, nodeDefinition: NodeDefinition, context: SchemaTransformationContext
    ): GraphQLInterfaceType {
        return type.transform {
            for (relationshipDefinition in nodeDefinition.relationshipDefinitions.values) {
                val field = relationshipDefinition.generateFieldDefinition(context)
                it.field(field)
                registerRelationshipDataFetcher(relationshipDefinition, context)
            }
        }
    }

    /**
     * Registers a DataFetcher for a [RelationshipDefinition]
     * Creates a new [DataFetcherFactory] and registers it to the [GraphQLCodeRegistry]
     * Uses the [Node.propertyLookup] to access the delegate for the property
     *
     * @param relationshipDefinition the definition of the relationship to create the data fetcher for
     * @param context provides the [GraphQLCodeRegistry]
     */
    private fun registerRelationshipDataFetcher(
        relationshipDefinition: RelationshipDefinition, context: SchemaTransformationContext
    ) {
        val kProperty = relationshipDefinition.property
        val functionDataFetcherFactory =
            dataFetcherFactoryProvider.functionDataFetcherFactory(null, BaseProperty<*>::getFromGraphQL)
        val dataFetcherFactory = DataFetcherFactory { dataFetcherFactoryEnvironment ->
            val functionDataFetcher = functionDataFetcherFactory.get(dataFetcherFactoryEnvironment)
            DataFetcher {
                val node = it.getSource<Node>()
                val environment = DelegateDataFetchingEnvironment(it, node.propertyLookup[kProperty]!!)
                functionDataFetcher.get(environment)
            }
        }
        val coordinates = FieldCoordinates.coordinates(
            relationshipDefinition.parentKClass.getSimpleName(), relationshipDefinition.graphQLName
        )
        context.codeRegistry.dataFetcher(coordinates, dataFetcherFactory)
    }

    /**
     * Adds the missing connection like queries for [Node] types declared using the [DomainNode] annotation
     */
    private fun updateQueryType(queryType: GraphQLObjectType, context: SchemaTransformationContext): GraphQLObjectType {
        val newQueryType = queryType.transform {
            val function = TopLevelQueryProvider::class.memberFunctions.first { it.name == "getFromGraphQL" }
            for ((nodeDefinition, queryName) in topLevelQueries) {
                val nodeClass = nodeDefinition.nodeType
                val field = generateConnectionFieldDefinition(
                    nodeClass, queryName, "Query for nodes of type ${nodeClass.getSimpleName()}", context
                )
                it.field(field)
                val coordinates = FieldCoordinates.coordinates(queryType.name, queryName)
                val dataFetcherFactory = dataFetcherFactoryProvider.functionDataFetcherFactory(
                    TopLevelQueryProvider<Node>(nodeDefinition), function
                )
                context.codeRegistry.dataFetcher(coordinates, dataFetcherFactory)
            }
        }
        return newQueryType
    }

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
}

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