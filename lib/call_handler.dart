
import 'dart:async';

import 'package:flutter/services.dart';

class CallHandler {
  static const MethodChannel _channel =
      const MethodChannel('call_handler');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
