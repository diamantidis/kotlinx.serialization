/*
 * Copyright 2017-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.internal

import kotlinx.serialization.*

/**
 * Serializer for Kotlin's singletons (denoted by `object` keyword).
 *
 * To preserve singleton instance after serialization and deserialization process, this serializer
 * accepts instance itself as `theInstance` parameter. This action is automatically performed by a compiler plugin
 * when you mark `object` as `@Serializable`.
 *
 * By default, singleton is serialized as empty structure, which would look in JSON like "{}" (empty object).
 */
public class ObjectSerializer<T : Any>(private val serialName: String, private val theInstance: T) : KSerializer<T> {
    override fun serialize(encoder: Encoder, obj: T) {
        encoder.beginStructure(descriptor).endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): T {
        decoder.beginStructure(descriptor).endStructure(descriptor)
        return theInstance
    }

    override val descriptor: SerialDescriptor = ObjectDescriptor(serialName)
}

/**
 * Object descriptor has UnionKind.OBJECT and consists of itself:
 * It has one element with name equals to object name
 * and [getElementDescriptor] returns the descriptor itself.
 */
public class ObjectDescriptor(name: String) : SerialClassDescImpl(name) {
    override val kind: SerialKind = UnionKind.OBJECT

    init {
        addElement(name)
    }

    override fun getElementDescriptor(index: Int): SerialDescriptor {
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObjectDescriptor) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "$name()"
    }
}
