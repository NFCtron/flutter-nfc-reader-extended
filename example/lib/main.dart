import 'package:flutter/material.dart';
import 'package:flutter_nfc_reader/extensions.dart';
import 'package:flutter_nfc_reader/flutter_nfc_reader.dart';

void main() => runApp(MyApp());

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Material App',
      home: Scaffold(
        appBar: AppBar(
          title: Text('Material App Bar'),
        ),
        body: Center(child: NfcScan()),
      ),
    );
  }
}

class NfcScan extends StatefulWidget {
  NfcScan({Key key}) : super(key: key);

  @override
  _NfcScanState createState() => _NfcScanState();
}

class _NfcScanState extends State<NfcScan> {
  TextEditingController writerController = TextEditingController();

  @override
  initState() {
    super.initState();
    writerController.text = 'Flutter NFC Scan';

    // MIFARE Ultralight protocol always reads 4 pages at a time
    // var args = NFCArguments(TechnologyType.MIFILRE_ULTRALIGHT, [6, 10, 14]);
    //
    // FlutterNfcReader.onTagDiscovered(jsonArgs: args.toJson()).listen((onData) {
    //   print("data from scanner:");
    //   print("id:");
    //   print(onData.id);
    //   print("content:");
    //   print(onData.content);
    // });
  }

  @override
  void dispose() {
    // Clean up the controller when the widget is removed from the
    // widget tree.
    writerController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: <Widget>[
        TextField(
          controller: writerController,
        ),
        RaisedButton(
          onPressed: () {

            // MIFARE Ultralight protocol always reads 4 pages at a time
            var args = NFCArguments(TechnologyType.MIFILRE_ULTRALIGHT, [6, 10, 14]);

            FlutterNfcReader.transactionRead(jsonArgs: args.toJson())
                .then((value) {
                    print("id:");
                    print(value.id);
                    print("content:");
                    print(value.content);
                    return FlutterNfcReader.transactionWrite([6], "1111cc00", "MIFILRE_ULTRALIGHT");
                })
                .then((value) {
                    print("id:");
                    print(value.id);
                    print("content:");
                    print(value.content);
                    var args = NFCArguments(TechnologyType.MIFILRE_ULTRALIGHT, [6]);
                    return FlutterNfcReader.transactionCheck(jsonArgs: args.toJson());
                })
                .then((value) {
                    print("id:");
                    print(value.id);
                    print("content:");
                    print(value.content);
                });
          },
          child: Text("Read"),
        ),
        RaisedButton(
          onPressed: () {
            FlutterNfcReader.write("12", "ËÑ", "MIFILRE_ULTRALIGHT").then((value) {
              print(value.content);
            });
          },
          child: Text("Write"),
        )
      ],
    );
  }
}
