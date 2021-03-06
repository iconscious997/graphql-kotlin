/*
 * Copyright 2019 Expedia, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.expediagroup.graphql.generator.types

import com.expediagroup.graphql.generator.SchemaGenerator
import com.expediagroup.graphql.generator.extensions.getGraphQLDescription
import com.expediagroup.graphql.generator.extensions.getSimpleName
import com.expediagroup.graphql.generator.extensions.safeCast
import graphql.TypeResolutionEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

internal class UnionBuilder(private val generator: SchemaGenerator) {
    internal fun unionType(kClass: KClass<*>): GraphQLUnionType {
        val builder = GraphQLUnionType.newUnionType()
        builder.name(kClass.getSimpleName())
        builder.description(kClass.getGraphQLDescription())

        generator.directives(kClass).forEach {
            builder.withDirective(it)
        }

        generator.subTypeMapper.getSubTypesOf(kClass)
            .map { generator.graphQLTypeOf(it.createType()) }
            .forEach {
                when (val unwrappedType = GraphQLTypeUtil.unwrapType(it).last()) {
                    is GraphQLTypeReference -> builder.possibleType(unwrappedType)
                    is GraphQLObjectType -> builder.possibleType(unwrappedType)
                }
            }

        val unionType = builder.build()
        generator.codeRegistry.typeResolver(unionType) { env: TypeResolutionEnvironment -> env.schema.getObjectType(env.getObject<Any>().javaClass.kotlin.getSimpleName()) }
        return generator.config.hooks.onRewireGraphQLType(unionType).safeCast()
    }
}
