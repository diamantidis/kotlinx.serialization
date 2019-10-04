/*
 * Copyright 2017-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.internal

import kotlinx.serialization.*
import kotlin.reflect.KClass

abstract class AbstractPolymorphicSerializer<T : Any> internal constructor() : KSerializer<T> {

    public abstract val baseClass: KClass<T>

    @Suppress("UNCHECKED_CAST")
    public final override fun serialize(encoder: Encoder, obj: T) {
        val actualSerializer = findPolymorphicSerializer(encoder, obj)
        val compositeEncoder = encoder.beginStructure(descriptor)
        compositeEncoder.encodeStringElement(descriptor, 0, actualSerializer.descriptor.name)
        compositeEncoder.encodeSerializableElement(descriptor, 1, actualSerializer as KSerializer<Any?>, obj)
        compositeEncoder.endStructure(descriptor)
    }

    @Suppress("UNCHECKED_CAST")
    public final override fun deserialize(decoder: Decoder): T {
        val compositeDecoder = decoder.beginStructure(descriptor)
        var klassName: String? = null
        var value: Any? = null
        mainLoop@ while (true) {
            when (val index = compositeDecoder.decodeElementIndex(descriptor)) {
                CompositeDecoder.READ_ALL -> {
                    klassName = compositeDecoder.decodeStringElement(descriptor, 0)
                    val serializer = findPolymorphicSerializer(compositeDecoder, klassName)
                    value = compositeDecoder.decodeSerializableElement(descriptor, 1, serializer)
                    break@mainLoop
                }
                CompositeDecoder.READ_DONE -> {
                    break@mainLoop
                }
                0 -> {
                    klassName = compositeDecoder.decodeStringElement(descriptor, index)
                }
                1 -> {
                    klassName = requireNotNull(klassName) { "Cannot read polymorphic value before its type token" }
                    val serializer = findPolymorphicSerializer(compositeDecoder, klassName)
                    value = compositeDecoder.decodeSerializableElement(descriptor, index, serializer)
                }
                else -> throw SerializationException(
                    "Invalid index in polymorphic deserialization of " +
                            (klassName ?: "unknown class") +
                            "\n Expected 0, 1, READ_ALL(-2) or READ_DONE(-1), but found $index"
                )
            }
        }

        compositeDecoder.endStructure(descriptor)
        return requireNotNull(value) { "Polymorphic value have not been read for class $klassName" } as T
    }

    /**
     * Lookups a polymorphic serializer by given [klassName] in the context of [decoder] by the current [base class][baseClass].
     * Throws [SerializationException] if serializer is not found.
     */
    public open fun findPolymorphicSerializer(
        decoder: CompositeDecoder,
        klassName: String
    ): KSerializer<out T> = decoder.context.getPolymorphic(baseClass, klassName)
            ?: throwSubtypeNotRegistered(klassName, baseClass)


    /**
     * Lookups a polymorphic serializer by given [value] in the context of [encoder] by the current [base class][baseClass].
     * Throws [SerializationException] if serializer is not found.
     */
    @Suppress("UNCHECKED_CAST")
    public open fun findPolymorphicSerializer(
        encoder: Encoder,
        value: T
    ): KSerializer<out T> =
        encoder.context.getPolymorphic(baseClass, value) ?: throwSubtypeNotRegistered(value::class, baseClass)
}


internal fun throwSubtypeNotRegistered(subClassName: String, baseClass: KClass<*>): Nothing =
    throw SerializationException("$subClassName is not registered for polymorphic serialization in the scope of $baseClass")

internal fun throwSubtypeNotRegistered(subClass: KClass<*>, baseClass: KClass<*>): Nothing =
    throwSubtypeNotRegistered(subClass.toString(), baseClass)
