package it.matteocrippa.flutternfcreader

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class AbstractNfcHandler(protected val result: MethodChannel.Result, protected val call: MethodCall) : NfcAdapter.ReaderCallback, IParametrizedIO by ParametrizedIO() {
    protected var argument: NFCArguments? = null

    init {
        argument = call.argument<String>("jsonArgs")?.let { parseArgs(it) }
    }

    protected fun unregister() = FlutterNfcReaderPlugin.listeners.remove(this)
}

class NfcWriter(result: MethodChannel.Result, call: MethodCall) : AbstractNfcHandler(result, call) {
    override fun onTagDiscovered(tag: Tag) {
        val type = call.argument<String>("path")
                ?: return result.error("404", "Missing parameter", null)
        val payload = call.argument<String>("label")
                ?: return result.error("404", "Missing parameter", null)

        call.argument<String>("technology").also {
            writeMUL(tag, type, payload, result)
        } ?: run {
            val nfcRecord = NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE, type.toByteArray(), byteArrayOf(), payload.toByteArray())
            val nfcMessage = NdefMessage(arrayOf(nfcRecord))
            writeMessageToTag(nfcMessage, tag)
        }

        val data = mapOf(kId to "", kContent to payload, kError to "", kStatus to "write")
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            result.success(data)
        }
        unregister()
    }

    private fun writeMessageToTag(nfcMessage: NdefMessage, tag: Tag?): Boolean {
        val nDefTag = Ndef.get(tag)

        nDefTag?.let {
            it.connect()
            if (it.maxSize < nfcMessage.toByteArray().size) {
                //Message to large to write to NFC tag
                return false
            }
            return if (it.isWritable) {
                it.use { ndef ->
                    ndef.writeNdefMessage(nfcMessage)
                }
                //Message is written to tag
                true
            } else {
                //NFC tag is read-only
                false
            }
        }

        val nDefFormatableTag = NdefFormatable.get(tag)

        nDefFormatableTag?.let {
            it.use { ndef ->
                ndef.connect()
                ndef.format(nfcMessage)
            }
            //The data is written to the tag
            true
        }
        //NDEF is not supported
        return false
    }
}

class NfcReader(result: MethodChannel.Result, call: MethodCall) : AbstractNfcHandler(result, call) {
    override fun onTagDiscovered(tag: Tag) {
        val callback = { data: Map<*, *> -> result.success(data) }
        argument?.also {
            readWithArgs(it, tag, callback)
        } ?: tag.read(callback)

        unregister()
    }
}

class NfcScanner(private val plugin: FlutterNfcReaderPlugin) : NfcAdapter.ReaderCallback, IParametrizedIO by ParametrizedIO() {
    override fun onTagDiscovered(tag: Tag) {
        val sink = plugin.eventSink ?: return

        val callback = { data: Map<*, *> -> sink.success(data) }
        plugin.arguments?.also {
            readWithArgs(it, tag, callback)
        } ?: tag.read(callback)
    }
}

class TransactionHandler(private val readResult: MethodChannel.Result, readCall: MethodCall) : AbstractNfcHandler(readResult, readCall) {

    private val readArgs : NFCArguments? = readCall.argument<String>("jsonArgs")?.let { parseArgs(it) }
    private val readCallback = { data: Map<*, *> -> readResult.success(data) }

    private var transactionTech : MifareUltralight? = null

    @RequiresApi(Build.VERSION_CODES.O)
    val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")

    @RequiresApi(Build.VERSION_CODES.O)
    fun write(result: MethodChannel.Result, call: MethodCall) {
        Log.i("FlutterNfcReaderPlugin", "write request handled in native: " + fmt.format(LocalDateTime.now()))
        val log = {s1 : String, s2 : String -> Log.i(s1, fmt.format(LocalDateTime.now())) }

        val type = call.argument<String>("path")
                ?: return result.error("404", "Missing parameter", null)
        val payload = call.argument<String>("label")
                ?: return result.error("404", "Missing parameter", null)
        val callback = { data: Map<*, *> -> result.success(data) }

        transactionWrite(transactionTech, type, payload, callback, result, log)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun checkRead(result: MethodChannel.Result, call: MethodCall) {
        Log.i("FlutterNfcReaderPlugin", "check read handled in native: " + fmt.format(LocalDateTime.now()))
        val log = {s1 : String, s2 : String -> Log.i(s1, s2 + fmt.format(LocalDateTime.now())) }

        val args : NFCArguments? = call.argument<String>("jsonArgs")?.let { parseArgs(it) }
        val callback = { data: Map<*, *> -> result.success(data) }

        args?.pages?.let { secondRead(transactionTech, it, callback, log, result) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onTagDiscovered(tag: Tag) {
        unregister()

        Log.i("FlutterNfcReaderPlugin", "onTagDiscovered - starting transaction: " + fmt.format((LocalDateTime.now())))
        val log = {s1 : String, s2 : String -> Log.i(s1, s2 + fmt.format(LocalDateTime.now())) }

        readArgs?.pages?.let {
            val tech = MifareUltralight.get(tag)
            if (tech == null) {
                result.error("-1", "No tech created.", null)
                return
            }
            transactionTech = tech
            transactionRead(tag, tech, it, readCallback, log)
        }
    }
}

fun ByteArray.bytesToHexString(): String? {
    val stringBuilder = StringBuilder("0x")

    for (i in indices) {
        stringBuilder.append(Character.forDigit(get(i).toInt() ushr 4 and 0x0F, 16))
        stringBuilder.append(Character.forDigit(get(i).toInt() and 0x0F, 16))
    }

    return stringBuilder.toString()
}

private fun Tag.read(callback: (Map<*, *>) -> Unit) {
    // convert tag to NDEF tag
    val ndef = Ndef.get(this)
    ndef.connect()
    val ndefMessage = ndef.ndefMessage ?: ndef.cachedNdefMessage
    val message = ndefMessage.toByteArray()
            .toString(Charsets.UTF_8)
    val id = id.bytesToHexString()
    ndef.close()
    val data = mapOf(kId to id, kContent to message, kError to "", kStatus to "reading")
    val mainHandler = Handler(Looper.getMainLooper())
    mainHandler.post {
        callback(data)
    }
}
