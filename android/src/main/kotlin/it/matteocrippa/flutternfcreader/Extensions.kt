package it.matteocrippa.flutternfcreader

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
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

// todo rewrite or remove
interface IParametrizedIO {
    fun readWithArgs(it: NFCArguments, tag: Tag, callback: (Map<*, *>) -> Unit)
    fun parseArgs(it: String): NFCArguments
    fun writeMUL(tag: Tag?, pages: String, hexData: String, result: MethodChannel.Result)
}

class ParametrizedIO : IParametrizedIO {

    override fun readWithArgs(it: NFCArguments, tag: Tag, callback: (Map<*, *>) -> Unit) {
        val technology = TechnologyType.getType(it.technologyName)
        if (technology == TechnologyType.MIFILRE_ULTRALIGHT) {
            tag.readMUL(it.pages, callback)
        }
    }

    override fun parseArgs(it: String): NFCArguments = Json.decodeFromString(it)

    override fun writeMUL(tag: Tag?, pages: String, hexData: String, result: MethodChannel.Result) {
        val tech = MifareUltralight.get(tag)
        val pageBytes = parseBytesForPage(pages, hexData)

        try {

            tech.connect()
            pageBytes.forEach { tech.writePage(it.first, it.second) }

        } catch (e: IOException) {
            result.error("-1", "IOException", e.message)
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

@RequiresApi(Build.VERSION_CODES.O)
fun transactionWrite(tech: MifareUltralight, pages: String, hexData: String, callback: (Map<*, *>) -> Unit, result: MethodChannel.Result, log: (String, String) -> Int) {
    val pageBytes = parseBytesForPage(pages, hexData)

    try {
        pageBytes.forEach {
            log("writing 1 page start: ", "")
            tech.writePage(it.first, it.second)
            log("writing 1 page start: ", "")
        }
    } catch (e: IOException) {
        result.error("-1", "IOException", e.message)
        return
    } catch (e: NumberFormatException) {
        result.error("-1", "Wrong format", null)
        return
    }

    val data = mapOf(kId to "", kContent to hexData, kError to "", kStatus to "write")
    val mainHandler = Handler(Looper.getMainLooper())

    log("write done: ", "")
    mainHandler.post { callback(data) }
}

@RequiresApi(Build.VERSION_CODES.O)
fun transactionRead(tag: Tag?, pages: List<Int>, callback: (Map<*, *>) -> Unit, log: (String, String) -> Int): MifareUltralight {
    val tech = MifareUltralight.get(tag)
    tech.connect()

    var result = ByteArray(0)
    pages.forEach {
        try {
            log("reading 1 page start: ", "")
            val page = tech.readPages(it)
            log("reading 1 page done: ", "")
            result += page
        } catch (e: IOException) {
            // try reread?
        }
    }

    val id = tag?.id?.bytesToHexString()
    val data = mapOf(kId to id, kContent to result.bytesToHexString(), kError to "", kStatus to "reading")
    val mainHandler = Handler(Looper.getMainLooper())
    log("read 1 done: ", "")
    mainHandler.post { callback(data) }
    return tech
}

// todo refactor
@RequiresApi(Build.VERSION_CODES.O)
fun secondRead(tech: MifareUltralight, pages: List<Int>, callback: (Map<*, *>) -> Unit, log: (String, String) -> Int) {

    var result = ByteArray(0)
    pages.forEach {
        try {
            log("reading 1 page start: ", "")
            val page = tech.readPages(it)
            log("reading 1 page start: ", "")
            result += page
        } catch (e: IOException) { }
    }
    tech.close()

    val data = mapOf(kId to "", kContent to result.bytesToHexString(), kError to "", kStatus to "reading")
    val mainHandler = Handler(Looper.getMainLooper())
    log("read 2 done: ", "")
    mainHandler.post { callback(data) }
}

// maybe move elsewhere
private fun parseBytesForPage(pages: String, hexData: String) : List<Pair<Int, ByteArray>> {
    val bytes = hexData
            .chunked(2)
            .map { Integer.parseInt(it, 16).toByte() }
            .chunked(4)
            .map { it.toByteArray() }
    return pages
            .split(",")
            .map { Integer.parseInt(it) }
            .zip(bytes)
}