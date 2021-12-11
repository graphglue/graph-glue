package com.nkcoding.graphglue.graphql.filter

import graphql.Scalars

class StringFilters(name: String) : SimpleObjectFilterDefinitionEntry(name, "StringFilterInput", listOf(
    StringFilterEntry("equals"),
    StringFilterEntry("startsWith"),
    StringFilterEntry("endsWith"),
    StringFilterEntry("contains"),
    StringFilterEntry("matches")
))

internal class StringFilterEntry(name: String) : SimpleFilterDefinitionEntry(name, Scalars.GraphQLString)