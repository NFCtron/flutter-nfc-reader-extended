package it.matteocrippa.flutternfcreader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.os.Build
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList

const val PERMISSION_NFC = 1007

@RequiresApi(Build.VERSION_CODES.M)
class FlutterNfcReaderPlugin : FlutterPlugin, ActivityAware, MethodCallHandler, EventChannel.StreamHandler, NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private var nfcManager: NfcManager? = null
    private var activity: Activity? = null

    internal var eventSink: EventChannel.EventSink? = null
    internal var arguments: NFCArguments? = null
    private var methodChannel: MethodChannel? = null
    private var eventChannel: EventChannel? = null
    private var nfcFlags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V


    override fun onMethodCall(call: MethodCall, result: Result) {
        require(activity != null) { "Plugin not ready yet" }
        require(nfcAdapter != null) { "Plugin not ready yet" }
        if (nfcAdapter?.isEnabled != true && call.method != "NfcAvailable") {
            result.error("404", "NFC Hardware not found", null)
            return
        }

        when (call.method) {
            "NfcEnableReaderMode" ->
                nfcAdapter!!.startNFCReader()
            "NfcDisableReaderMode" ->
                nfcAdapter!!.stopNFCReader()
            "NfcStop" -> {
                listeners.removeAll { it !is NfcScanner }
                result.success(null)
            }

            "NfcRead" -> {
                listeners.add(NfcReader(result, call))
            }

            "NfcWrite" -> {
                listeners.add(NfcWriter(result, call))
            }
            "NfcAvailable" -> {
                when {
                    nfcAdapter == null -> result.success("not_supported")
                    nfcAdapter!!.isEnabled -> result.success("available")
                    else -> result.success("disabled")
                }
            }
            else -> result.notImplemented()
        }
    }

    // EventChannel.StreamHandler methods
    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        eventSink = events
        this.arguments = arguments?.toString()?.let {
            Json.decodeFromString<NFCArguments>(it)
        }
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    private fun NfcAdapter.startNFCReader() {
        listeners.add(NfcScanner(this@FlutterNfcReaderPlugin))
        enableReaderMode(activity, this@FlutterNfcReaderPlugin, nfcFlags, null)
    }


    private fun NfcAdapter.stopNFCReader() {
        disableReaderMode(activity)
        listeners.clear()
    }

    // handle discovered NDEF Tags
    override fun onTagDiscovered(tag: Tag) = listeners.forEach { it.onTagDiscovered(tag) }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val messenger = binding.binaryMessenger
        methodChannel = MethodChannel(messenger, "flutter_nfc_reader")
        methodChannel!!.setMethodCallHandler(this)
        eventChannel = EventChannel(messenger, "it.matteocrippa.flutternfcreader.flutter_nfc_reader")
        eventChannel!!.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel!!.setMethodCallHandler(null)
        eventChannel!!.setStreamHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        val activity = binding.activity
        this.activity = activity

        nfcManager = activity.getSystemService(Context.NFC_SERVICE) as? NfcManager
        nfcAdapter = nfcManager?.defaultAdapter

        activity.requestPermissions(
                arrayOf(Manifest.permission.NFC),
                PERMISSION_NFC
        )

        nfcAdapter?.startNFCReader()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) = onAttachedToActivity(binding)

    override fun onDetachedFromActivityForConfigChanges() = onDetachedFromActivity()

    override fun onDetachedFromActivity() {
        activity = null
    }

    companion object {
        internal val listeners = CopyOnWriteArrayList<NfcAdapter.ReaderCallback>()
    }
}
