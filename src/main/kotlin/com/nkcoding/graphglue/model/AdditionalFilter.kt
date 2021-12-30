package com.nkcoding.graphglue.model

import java.lang.annotation.Inherited

@Repeatable
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Inherited
annotation class AdditionalFilter(val beanName: String)
