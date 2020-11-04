package it.matteocrippa.flutternfcreader

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import android.os.Handler
import android.os.Looper
import kotlinx.serialization.Serializable
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

fun Tag.readMUL(pages: List<Int>, callback: (Map<*, *>) -> Unit) {
    val tech = MifareUltralight.get(this)
    tech.connect() // consider if propagate attempt of other technology usage

    var result: ByteArray = ByteArray(0)
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