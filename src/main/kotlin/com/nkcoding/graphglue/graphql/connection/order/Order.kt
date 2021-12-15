package com.nkcoding.graphglue.graphql.connection.order

import com.nkcoding.graphglue.model.Node

class Order<in T: Node>(val direction: OrderDirection, val field: OrderField<T>)