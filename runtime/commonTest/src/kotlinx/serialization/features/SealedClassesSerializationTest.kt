/*
 * Copyright 2017-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.features

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.test.assertStringFormAndRestored
import kotlin.test.*

@Serializable
sealed class SimpleSealed {
    @Serializable
    public data class SubSealedA(val s: String) : SimpleSealed()

    @Serializable
    public data class SubSealedB(val i: Int) : SimpleSealed()
}

@Serializable
sealed class SealedProtocol {
    @Serializable
    data class StringMessage(val description: String, val message: String) : SealedProtocol()

    @Serializable
    data class IntMessage(val description: String, val message: Int) : SealedProtocol()

    @Serializable
    data class ErrorMessage(val error: String) : SealedProtocol()

    @Suppress("PLUGIN_ERROR")
    @SerialName("EOF")
    @Serializable
    object EOF : SealedProtocol()
}

//@Serializable
//sealed class SealedProtocol2 {
//
//    @Serializable
//    sealed class Message2 : SealedProtocol2() {
//        //        abstract val description
//        @Serializable
//        data class StringMessage(val description: String, val message: String) : Message2()
//
//        @Serializable
//        data class IntMessage(val description: String, val message: Int) : Message2()
//    }
//
//
//    @Serializable
//    data class ErrorMessage(val error: String) : SealedProtocol2()
//
//    @Suppress("PLUGIN_ERROR")
//    @SerialName("EOF")
//    @Serializable
//    object EOF : SealedProtocol2()
//}

val ManualSerializer = SealedClassSerializer(
    "SimpleSealed",
    arrayOf(SimpleSealed.SubSealedA::class, SimpleSealed.SubSealedB::class),
    arrayOf(SimpleSealed.SubSealedA.serializer(), SimpleSealed.SubSealedB.serializer())
)

@Serializable
data class SealedHolder(val s: SimpleSealed)

@Serializable
data class SealedBoxHolder(val b: Box<SimpleSealed>)

class SealedClassesSerializationTest {
    private val arrayJson = Json(JsonConfiguration.Default.copy(useArrayPolymorphism = true))
    private val json = Json(JsonConfiguration.Default.copy(useArrayPolymorphism = false))

    @Test
    fun manualSerializer() {
        val message = json.stringify(
            ManualSerializer,
            SimpleSealed.SubSealedB(42)
        )
        assertEquals("{\"type\":\"kotlinx.serialization.features.SimpleSealed.SubSealedB\",\"i\":42}", message)
    }

    @Test
    fun onTopLevel() {
        val arrayMessage = arrayJson.stringify(
            SimpleSealed.serializer(),
            SimpleSealed.SubSealedB(42)
        )
        val message = json.stringify(
            SimpleSealed.serializer(),
            SimpleSealed.SubSealedB(42)
        )
        // todo: manual serializer check
        assertEquals("{\"type\":\"kotlinx.serialization.features.SimpleSealed.SubSealedB\",\"i\":42}", message)
        assertEquals("[\"kotlinx.serialization.features.SimpleSealed.SubSealedB\",{\"i\":42}]", arrayMessage)
    }

    @Test
    fun insideClass() {
        assertStringFormAndRestored(
            """{"s":{"type":"kotlinx.serialization.features.SimpleSealed.SubSealedA","s":"foo"}}""",
            SealedHolder(SimpleSealed.SubSealedA("foo")),
            SealedHolder.serializer(),
            json
        )
    }

    @Test
    fun insideGeneric() {
        assertStringFormAndRestored(
            """{"boxed":{"type":"kotlinx.serialization.features.SimpleSealed.SubSealedA","s":"foo"}}""",
            Box<SimpleSealed>(SimpleSealed.SubSealedA("foo")),
            Box.serializer(SimpleSealed.serializer()),
            json
        )
        assertStringFormAndRestored(
            """{"b":{"boxed":{"type":"kotlinx.serialization.features.SimpleSealed.SubSealedA","s":"foo"}}}""",
            SealedBoxHolder(Box(SimpleSealed.SubSealedA("foo"))),
            SealedBoxHolder.serializer(),
            json
        )
    }

    @Test
    fun complexProtocol() {
        val messages = listOf<SealedProtocol>(
            SealedProtocol.StringMessage("string message", "foo"),
            SealedProtocol.IntMessage("int message", 42),
//            SealedProtocol.Message<SimpleSealed>("simple sealed message", SimpleSealed.SubSealedA("bar")),
            SealedProtocol.ErrorMessage("requesting termination"),
            SealedProtocol.EOF
        )
        val json = Json(JsonConfiguration.Default.copy(useArrayPolymorphism = false, prettyPrint = true))
        val message = json.stringify(SealedProtocol.serializer().list, messages)
        println(message)
    }
//
//    @Test
//    @Ignore
//    fun complexProtocol2() {
//        val messages = listOf<SealedProtocol2>(
//            SealedProtocol2.Message2.StringMessage("string message", "foo"),
//            SealedProtocol2.Message2.IntMessage("int message", 42),
//            SealedProtocol2.ErrorMessage("requesting termination"),
//            SealedProtocol2.EOF
//        )
//        val json = Json(JsonConfiguration.Default.copy(useArrayPolymorphism = true, prettyPrint = true))
//        val message = json.stringify(SealedProtocol2.serializer().list, messages)
//        println(message)
//    }
}
