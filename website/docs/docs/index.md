---
title: Getting started
---

# Getting started

## About

GraphGlue is a library to develop annotation-based code-first GraphQL servers using GraphQL Kotlin, Spring Boot and Neo4j.

## Installation

### Full installation

::: code-group

```kotlin [Gradle Kotlin]
implementation("io.github.graphglue", "graphglue", "8.0.0")
```

```xml [Maven]
<dependency>
    <groupId>io.github.graphglue</groupId>
    <artifactId>graphglue</artifactId>
    <version>8.0.0</version>
</dependency>
```

:::

### Core installation

This is meant to be used when the project consists of multiple modules, and the module containing the domain model should not provide the beans for the GraphQL server.
This has no dependency on `com.expediagroup:graphql-kotlin-spring-server`, however it still depends on `com.expediagroup:graphql-kotlin-server`.

::: code-group

```kotlin [Gradle Kotlin]
implementation("io.github.graphglue", "graphglue-core", "8.0.0")
```

```xml [Maven]
<dependency>
    <groupId>io.github.graphglue</groupId>
    <artifactId>graphglue-core</artifactId>
    <version>8.0.0</version>
</dependency>
```

:::

::: info
If you want to use Spring Data Repositories, please make sure to annotate your `Application` class with `@EnableGraphGlueRepositories`.
:::

## Database setup

GraphGlue needs a Neo4j database to work.
Only the latest 5.x version is officially supported, however older 5.x versions might work as well.
Due to how we check permissions, we recommend using the [GraphGlue Neo4j authorization plugin](https://github.com/graphglue/authorization-procedure).
Additionally, due to a memory reporting bug in Neo4j when using `UNWIND` in combination with `ORDER BY` and `LIMIT`, we recommend setting `dbms.memory.transaction.total.max` to `0`, disabling the limit.
A docker image based on the community edition of the official Neo4j image is available at `ghcr.io/graphglue/neo4j:main`.

## Features

- Declare relations in Kotlin
  - Many sides of relations are automatically transformed to connections in GraphQL
  - Lazy loading support in Kotlin for easy algorithm implementation, including single-query fetching of sub-properties
- GraphQL queries directly translated into Neo4j Cypher
  - One GraphQL query maps to one Cypher query (in case lazy loading and manual authorization checking are not used)
- Declarative & extensible authorization approach
  - Specify Node-based authorization using annotations
  - Implement custom logic using Spring beans
  - "Inherit" permissions from other nodes via relations
  - Automatic permission checking when using relations
- Automatic `node` GraphQL query and connection-like queries if specified
- Ordering & filtering
  - Filter by properties, and even across relations
  - Add custom, property-independent filters using annotations and Spring beans
  - Order by properties
  - Always performed directly in the database
- Use your favorite [GraphQL Kotlin](https://opensource.expediagroup.com/graphql-kotlin) and [Spring Data Neo4j](https://spring.io/projects/spring-data-neo4j) features
- Integrated [GraphiQL](https://github.com/graphql/graphiql/blob/main/packages/graphiql/README.md) GraphQL IDE with explorer plugin

## Building locally

Build the project:

```sh
./gradlew clean build
```

Deploy to the local Maven repository:

```sh
./gradlew publishToMavenLocal
```
