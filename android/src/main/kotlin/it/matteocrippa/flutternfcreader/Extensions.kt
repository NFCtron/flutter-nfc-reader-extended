package it.matteocrippa.flutternfcreader

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.reflect.KClass

enum class TechnologyType {
    MIFILRE_ULTRALIGHT {
        override fun getTechnology() = MifareUltralight::class
    },
    NDEF {
        override fun getTechnology() = Ndef::class
    };

    abstract fun getTechnology(): KClass<out TagTechnology>

    companion object {
        fun getType(key: String): TechnologyType {
            return try {
                valueOf(key)
            } catch (e: IllegalArgumentException) {
                NDEF
            }
        }
    }
}

@Serializable
data class NFCArguments(val technologyName: String, val pages: List<Int> = emptyList())

interface IParametrizedRead {
    fun readWithArgs(it: NFCArguments, tag: Tag, callback: (Map<*, *>) -> Unit)
    fun parseArgs(it: String): NFCArguments
    fun writeMUL(tag: Tag?, page: String, data: String, result: MethodChannel.Result)
}

class ParametrizedRead : IParametrizedRead {
    override fun readWithArgs(it: NFCArguments, tag: Tag, callback: (Map<*, *>) -> Unit) {
        val technology = TechnologyType.getType(it.technologyName)
        if (technology == TechnologyType.MIFILRE_ULTRALIGHT) {
            tag.readMUL(it.pages, callback)
        }
    }

    override fun parseArgs(it: String): NFCArguments = Json.decodeFromString(it)

    override fun writeMUL(tag: Tag?, page: String, data: String, result: MethodChannel.Result) {
        val tech = MifareUltralight.get(tag)
        try {
            val pageIndex = Integer.parseInt(page)
            tech.connect()
            tech.writePage(pageIndex, data.toByteArray())
        } catch (e: IOException) {
            // TODO consider right reaction...
        } catch (e: NumberFormatException) {
            result.error("-1", "Wrong format", null)
        } finally {
            tech.close()
        }
    }
}

fun Tag.readMUL(pages: List<Int>, callback: (Map<*, *>) -> Unit) {
    val tech = MifareUltralight.get(this)
    tech.connect() // consider if propagate attempt of other technology usage

    var result = ByteArray(0)
    pages.forEach {
        try {
            val page = tech.readPages(it)
            result += page
        } catch (e: IOException) {
            // TODO consider right reaction...
        }
    }

    val id = id.bytesToHexString()
    tech.close()

    val data = mapOf(kId to id, kContent to result.bytesToHexString(), kError to "", kStatus to "reading")
    val mainHandler = Handler(Looper.getMainLooper())
    mainHandler.post {
        callback(data)
    }
}