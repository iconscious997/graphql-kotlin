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

import com.expediagroup.graphql.exceptions.InvalidInterfaceException
import com.expediagroup.graphql.generator.SchemaGenerator
import com.expediagroup.graphql.generator.extensions.getGraphQLDescription
import com.expediagroup.graphql.generator.extensions.getSimpleName
import com.expediagroup.graphql.generator.extensions.getValidFunctions
import com.expediagroup.graphql.generator.extensions.getValidProperties
import com.expediagroup.graphql.generator.extensions.getValidSuperclasses
import com.expediagroup.graphql.generator.extensions.safeCast
import graphql.TypeResolutionEnvironment
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLTypeUtil
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

internal class InterfaceBuilder(private val generator: SchemaGenerator) {
    internal fun interfaceType(kClass: KClass<*>): GraphQLInterfaceType {
        // Interfaces can not implement another interface in GraphQL
        if (kClass.getValidSuperclasses(generator.config.hooks).isNotEmpty()) {
            throw InvalidInterfaceException()
        }

        val builder = GraphQLInterfaceType.newInterface()

        builder.name(kClass.getSimpleName())
        builder.description(kClass.getGraphQLDescription())

        generator.directives(kClass).forEach {
            builder.withDirective(it)
        }

        kClass.getValidProperties(generator.config.hooks)
            .forEach { builder.field(generator.property(it, kClass)) }

        kClass.getValidFunctions(generator.config.hooks)
            .forEach { builder.field(generator.function(it, kClass.getSimpleName(), abstract = true)) }

        generator.subTypeMapper.getSubTypesOf(kClass)
            .map { generator.graphQLTypeOf(it.createType()) }
            .forEach {
                // Do not add objects currently under construction to the additional types
                val unwrappedType = GraphQLTypeUtil.unwrapType(it).last()
                if (unwrappedType !is GraphQLTypeReference) {
                    generator.state.additionalTypes.add(it)
                }
            }

        val interfaceType = builder.build()
        generator.codeRegistry.typeResolver(interfaceType) { env: TypeResolutionEnvironment -> env.schema.getObjectType(env.getObject<Any>().javaClass.kotlin.getSimpleName()) }
        return generator.config.hooks.onRewireGraphQLType(interfaceType).safeCast()
    }
}
