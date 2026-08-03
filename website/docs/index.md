---
layout: home

hero:
  name: GraphGlue
  text: GraphQL servers, glued to your domain model
  tagline: Annotation-based, code-first GraphQL on Kotlin, Spring Boot and Neo4j - with connections, filtering, ordering and authorization built in.
  image:
    src: /img/logo.svg
    alt: GraphGlue
  actions:
    - theme: brand
      text: Get started
      link: /docs/
    - theme: alt
      text: API reference
      link: /api/
    - theme: alt
      text: View on GitHub
      link: https://github.com/graphglue/graph-glue

features:
  - title: Relations, declared in Kotlin
    details: Declare relationships as delegated properties. Many-sides automatically become GraphQL connections, and lazy loading fetches whole subtrees in a single query.
  - title: One GraphQL query, one Cypher query
    details: Queries are translated straight into Neo4j Cypher instead of being resolved field by field, so the n+1 problem never appears in the first place.
  - title: Declarative authorization
    details: Annotate nodes with the permissions they require, inherit permissions across relations, and extend the rules with Spring beans. Checks run in the database.
  - title: Filtering and ordering for free
    details: Filter by properties and across relations, order by any comparable property, and add property-independent filters and orders of your own.
  - title: Built on tools you already use
    details: Every GraphQL Kotlin and Spring Data Neo4j feature keeps working. GraphGlue adds to them rather than replacing them.
  - title: GraphiQL included
    details: A GraphiQL instance with the explorer plugin is served out of the box, so the schema is explorable from the first run.
---
