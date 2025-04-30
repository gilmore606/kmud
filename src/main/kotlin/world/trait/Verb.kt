package com.dlfsystems.world.trait

import com.dlfsystems.app.Log
import com.dlfsystems.compiler.Compiler
import com.dlfsystems.server.Yegg
import com.dlfsystems.value.VObj
import com.dlfsystems.value.VTrait
import com.dlfsystems.value.Value
import com.dlfsystems.vm.Context
import com.dlfsystems.vm.VM
import com.dlfsystems.vm.VMWord
import com.dlfsystems.vm.dumpText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable(with = Verb.VerbSerializer::class)
class Verb(
    val name: String,
) {
    private var source = ""
    private var code: List<VMWord> = listOf()
    private var symbols: Map<String, Int> = mapOf()

    fun program(cOut: Compiler.Result) {
        source = cOut.source
        code = cOut.code
        symbols = cOut.symbols
        Log.i("programmed $name with code ${code.dumpText()}")
    }

    fun call(c: Context, vThis: VObj, vTrait: VTrait, args: List<Value>): Value {
        if (source.isNotEmpty() && code.isEmpty()) recompile()
        val vm = VM(code, symbols)
        c.push(vThis, vTrait, name, args, vm)
        val r = vm.execute(c, args)
        c.pop()
        return r
    }

    fun getListing() = source

    private fun recompile() {
        Compiler.compile(source).also {
            code = it.code
            symbols = it.symbols
        }
    }

    object VerbSerializer : KSerializer<Verb> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Verb") {
            element<String>("name")
            element<String>("source")
            element<List<VMWord>>("code")
            element<Map<String, Int>>("symbols")
        }

        override fun serialize(encoder: Encoder, value: Verb) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.name)
                encodeStringElement(descriptor, 1, value.source)
                if (Yegg.serializeWithCode) {
                    encodeSerializableElement(descriptor, 2, ListSerializer(VMWord.serializer()), value.code)
                    encodeSerializableElement(descriptor, 3, MapSerializer(String.serializer(), Int.serializer()), value.symbols)
                }
            }
        }

        override fun deserialize(decoder: Decoder): Verb {
            return decoder.decodeStructure(descriptor) {
                var name: String? = null
                var source: String? = null
                var code: List<VMWord>? = null
                var symbols: Map<String, Int>? = null

                while (true) {
                    when (val i = decodeElementIndex(descriptor)) {
                        CompositeDecoder.DECODE_DONE -> break
                        0 -> name = decodeStringElement(descriptor, 0)
                        1 -> source = decodeStringElement(descriptor, 1)
                        2 -> code = decodeSerializableElement(descriptor, 2, ListSerializer(VMWord.serializer()))
                        3 -> symbols = decodeSerializableElement(descriptor, 3, MapSerializer(String.serializer(), Int.serializer()))
                    }
                }

                Verb(
                    requireNotNull(name),
                ).also {
                    it.source = requireNotNull(source)
                    it.code = code ?: listOf()
                    it.symbols = symbols ?: mapOf()
                }
            }
        }
    }

}
