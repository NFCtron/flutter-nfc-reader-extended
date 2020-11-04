import 'package:enum_to_string/enum_to_string.dart';

class NFCArguments {
  final TechnologyType technologyName;
  final List<int> pages;

  NFCArguments(this.technologyName, this.pages);

  String toJson() =>
      "{\"technologyName\":\"" + EnumToString.convertToString(technologyName) +
          "\",\"pages\":" + pages.toString() + "}";

}

enum TechnologyType { MIFILRE_ULTRALIGHT }