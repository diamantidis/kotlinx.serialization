/*
 * Copyright 2017-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization

import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass

/**
 * This class provides support for multiplatform polymorphic serialization for sealed classes.
 *
 * In contrary to [PolymorphicSerializer], all known subclasses with serializers must be passed
 * in `subclasses` and `subSerializers` constructor parameters. This action is automatically performed by a compiler plugin
 * when you mark sealed class as `@Serializable`. If a subclass is sealed class itself, all its subclasses are registered, too.
 * In most of the cases, you won't need to perform any additional manual setup:
 *
 * ```
 * @Serializable
 * sealed class SimpleSealed {
 *     @Serializable
 *     public data class SubSealedA(val s: String) : SimpleSealed()
 *
 *     @Serializable
 *     public data class SubSealedB(val i: Int) : SimpleSealed()
 * }
 *
 * // will perform correct polymorphic serialization and deserialization:
 * Json.stringify(SimpleSealed.serializer(), SubSealedA("foo"))
 * ```
 *
 * However, it is still possible to register additional subclasses later using regular [SerializersModule].
 * It is required when one of inheritors of sealed class is an abstract class:
 *
 * ```
 * @Serializable
 * sealed class ProtocolWithAbstractClass {
 *     @Serializable
 *     abstract class Message : ProtocolWithAbstractClass() {
 *         @Serializable
 *         data class StringMessage(val description: String, val message: String) : Message()
 *
 *         @Serializable
 *         data class IntMessage(val description: String, val message: Int) : Message()
 *     }
 *
 *     @Serializable
 *     data class ErrorMessage(val error: String) : ProtocolWithAbstractClass()
 * }
 * ```
 *
 * In this case, `ErrorMessage` would be registered automatically by plugin,
 * but `StringMessage` and `IntMessage` require usual registration, as described in [PolymorphicSerializer] documentation:
 *
 * ```
 * val abstractContext = SerializersModule {
 *     polymorphic(ProtocolWithAbstractClass::class, ProtocolWithAbstractClass.Message::class) {
 *         ProtocolWithAbstractClass.Message.IntMessage::class with ProtocolWithAbstractClass.Message.IntMessage.serializer()
 *         ProtocolWithAbstractClass.Message.StringMessage::class with ProtocolWithAbstractClass.Message.StringMessage.serializer()
 *         // no need to register ProtocolWithAbstractClass.ErrorMessage
 *     }
 * }
 * ```
 *
 */
public class SealedClassSerializer<T : Any>(
    serialName: String,
    override val baseClass: KClass<T>,
    subclasses: Array<KClass<out T>>,
    subSerializers: Array<KSerializer<out T>>
) : AbstractPolymorphicSerializer<T>() {

    private val backingMap: Map<KClass<out T>, KSerializer<out T>>

    private val inverseMap: Map<String, KSerializer<out T>>


    init {
        require(subclasses.size == subSerializers.size) { "Arrays of classes and serializers must have the same length" }
        backingMap = subclasses.zip(subSerializers).toMap()
        inverseMap = backingMap.values.associateBy { serializer -> serializer.descriptor.name }
    }

    @Suppress("UNCHECKED_CAST")
    override fun findPolymorphicSerializer(decoder: CompositeDecoder, klassName: String): KSerializer<out T> {
        return inverseMap[klassName]
                ?: super.findPolymorphicSerializer(decoder, klassName)
    }

    @Suppress("UNCHECKED_CAST")
    override fun findPolymorphicSerializer(encoder: Encoder, value: T): KSerializer<out T> {
        return backingMap[value::class]
                ?: super.findPolymorphicSerializer(encoder, value)
    }

    override val descriptor: SerialDescriptor = SealedClassDescriptor(serialName, subSerializers.map { it.descriptor })
}

/**
 * Descriptor for sealed class contains descriptors for all its serializable inheritors
 * which can be obtained via [getElementDescriptor].
 */
public class SealedClassDescriptor(
    override val name: String,
    elementDescriptors: List<SerialDescriptor>
) : SerialClassDescImpl(name) {
    override val kind: SerialKind = PolymorphicKind.SEALED

    init {
        elementDescriptors.forEach {
            addElement(it.name)
            pushDescriptor(it)
        }
    }
}
