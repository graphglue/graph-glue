# GraphGlue

A library to develop annotation-based code-first GraphQL servers using GraphQL Kotlin, Spring Boot and Neo4j.  
For the full documentation, have a look at our [website](https://graphglue.github.io)

## Installation

### Full Installation

#### Gradle
```kts
implementation("io.github.graphglue", "graphglue", "7.2.4")
```

#### Maven
```xml
<dependency>
    <groupId>io.github.graphglue</groupId>
    <artifactId>graphglue</artifactId>
    <version>7.2.4</version>
</dependency>
```

### Core Installation

This is meant to be used when the project consits of multiple modules, and the module containing the domain model should not provide the beans for the GraphQL server.
this has no dependency on `com.expediagroup:graphql-kotlin-spring-server`, however it still depends on `com.expediagroup:graphql-kotlin-server`.

#### Gradle
```kts
implementation("io.github.graphglue", "graphglue-core", "7.2.4")
```

#### Maven
```xml
<dependency>
    <groupId>io.github.graphglue</groupId>
    <artifactId>graphglue-core</artifactId>
    <version>7.2.4</version>
</dependency>
```

If you want to use Spring Data Repositories, please make sure to annotate your `Application` class with `@EnableGraphGlueRepositories`


## Features

- Declare relations in Kotlin
  - Many side of relations are automatically transformed to connections in GraphQL
  - Lazy loading support in Kotlin for easy algorithm implementation
- GraphQL queries directly translated into Neo4j Cypher
  - One GraphQL query maps to one Cypher query (in case lazy loading and manual authorization checking are not used)
- Declarative & extensible authorization aproach
  - Specify Node-based authorization using annotations
  - Implement custom logic using Spring beans
  - "Inherit" permissions from other nodes via relations
  - Automatic permission checking when using relations
- Automatic `node` GraphQL query and connection-like queries if specified
- Ordering & Filtering
  - Filter by properties, and even across relations
  - Add custom, property-independent filter using annotations and Spring beans
  - Order by properties
  - Always performed directly in the database
- Use your favorite [GraphQL Kotlin](https://opensource.expediagroup.com/graphql-kotlin) and [Spring Data Neo4j](https://spring.io/projects/spring-data-neo4j) features
- Integrated [GraphiQL](https://github.com/graphql/graphiql/blob/main/packages/graphiql/README.md) GraphQL IDE with explorer plugin

## Building locally

Build the project:
```sh
./gradlew clean build
```

Deploy to the local maven repository
```sh
./gradlew publishToMavenLocal
```

## LICENSE

GraphGlue is provided under the Apache License Version 2.0 