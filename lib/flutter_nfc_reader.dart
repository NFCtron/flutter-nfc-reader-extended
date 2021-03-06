import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/services.dart';

enum NFCStatus {
  none,
  reading,
  read,
  stopped,
  error,
}

class NfcData {
  final String id;
  final String content;
  final String error;
  final String statusMapper;

  NFCStatus status;

  NfcData({
    this.id,
    this.content,
    this.error,
    this.statusMapper,
  });

  factory NfcData.fromMap(Map data) {
    NfcData result = NfcData(
      id: data['nfcId'],
      content: data['nfcContent'],
      error: data['nfcError'],
      statusMapper: data['nfcStatus'],
    );
    switch (result.statusMapper) {
      case 'none':
        result.status = NFCStatus.none;
        break;
      case 'reading':
        result.status = NFCStatus.reading;
        break;
      case 'stopped':
        result.status = NFCStatus.stopped;
        break;
      case 'error':
        result.status = NFCStatus.error;
        break;
      default:
        result.status = NFCStatus.none;
    }
    return result;
  }
}

class FlutterNfcReader {
  static const MethodChannel _channel =
      const MethodChannel('flutter_nfc_reader');
  static const stream =
      const EventChannel('it.matteocrippa.flutternfcreader.flutter_nfc_reader');

  static Future<NfcData> enableReaderMode() async {
    final Map data = await _channel.invokeMethod('NfcEnableReaderMode');
    final NfcData result = NfcData.fromMap(data);

    return result;
  }

  static Future<NfcData> disableReaderMode() async {
    final Map data = await _channel.invokeMethod('NfcDisableReaderMode');
    final NfcData result = NfcData.fromMap(data);

    return result;
  }

  static Future<NfcData> stop() async {
    final Map data = await _channel.invokeMethod('NfcStop');
    final NfcData result = NfcData.fromMap(data);

    return result;
  }

  static Future<NfcData> read({String instruction, String jsonArgs}) async {
    final Map data = await _callRead(instruction: instruction, jsonArgs: jsonArgs);
    final NfcData result = NfcData.fromMap(data);
    return result;
  }

  static Stream<NfcData> onTagDiscovered({String instruction, String jsonArgs}) {
    if (Platform.isIOS) {
      _callRead(instruction: instruction);
    }
    return stream.receiveBroadcastStream(jsonArgs).map((rawNfcData) {
      return NfcData.fromMap(rawNfcData);
    });
  }

  static Future<Map> _callRead({instruction: String, String jsonArgs}) async {
      return await _channel.invokeMethod('NfcRead', <String, dynamic> {
        "instruction": instruction,
        "jsonArgs": jsonArgs
      });
  }

  static Future<NfcData> write(String path, String label, String technology) async {
    final Map data = await _channel.invokeMethod(
        'NfcWrite', <String, dynamic> {
          'label': label,
          'path': path,
          "technology": technology
        });

    final NfcData result = NfcData.fromMap(data);

    return result;
  }

  static Future<NfcData> writeMultiple(List<int> pages, String hex, String tech) {
    if (pages.isEmpty) return Future.error(
        "Pages indexes list is empty",
        StackTrace.fromString("FlutterNfcReader.writeMultiple")
    );

    String pagesStr = pages.map((val) => val.toString()).join(',');

    return write(pagesStr, hex, tech);
  }

  // todo generalize...
  static Future<NfcData> transactionRead({String instruction, String jsonArgs}) async {
    final Map data = await _transactionRead(instruction: instruction, jsonArgs: jsonArgs);
    final NfcData result = NfcData.fromMap(data);
    return result;
  }

  static Future<Map> _transactionRead({instruction: String, String jsonArgs}) async {
    try {
      return await _channel.invokeMethod('TransactionStart', <String, dynamic> {
        "instruction": instruction,
        "jsonArgs": jsonArgs
      });
    } catch (e) {
      final Map errMap =  <String, dynamic> {
        'nfcStatus': 'error',
        'nfcError': e.toString()
      };
      return Future.value( errMap );
    }
  }

  static Future<NfcData> transactionWrite(List<int> pages, String hex, String tech) {
    if (pages.isEmpty) return Future.error(
        "Pages indexes list is empty",
        StackTrace.fromString("FlutterNfcReader.transactionWrite")
    );

    String pagesStr = pages.map((val) => val.toString()).join(',');

    return _transactionWrite(pagesStr, hex, tech);
  }

  static Future<NfcData> _transactionWrite(String path, String label, String technology) async {
    try {
      final Map data = await _channel.invokeMethod(
          'TransactionWrite', <String, dynamic> {
        'label': label,
        'path': path,
        "technology": technology
      });

      final NfcData result = NfcData.fromMap(data);

      return result;
    } catch (e) {
      var nfcData = NfcData(error: e.toString());
      nfcData.status = NFCStatus.error;
      return Future.value( nfcData );
    }
  }

  static Future<NfcData> transactionCheck({String instruction, String jsonArgs}) async {
    final Map data = await _transactionCheck(instruction: instruction, jsonArgs: jsonArgs);
    final NfcData result = NfcData.fromMap(data);
    return result;
  }

  static Future<Map> _transactionCheck({instruction: String, String jsonArgs}) async {
    try {
      return await _channel.invokeMethod('TransactionCheck', <String, dynamic>{
        "instruction": instruction,
        "jsonArgs": jsonArgs
      });
    } catch (e) {
      final Map errMap =  <String, dynamic> {
        'nfcStatus': 'error',
        'nfcError': e.toString()
      };
      return Future.value( errMap );
    }
  }

  static Future<NFCAvailability> checkNFCAvailability() async {
    var availability = "NFCAvailability.${await _channel.invokeMethod<String>("NfcAvailable")}";
    return NFCAvailability.values.firstWhere((item) => item.toString() == availability);
  }
}

enum NFCAvailability {
  available, disabled, not_supported
}
