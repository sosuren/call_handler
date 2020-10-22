
import 'dart:async';
import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:meta/meta.dart';

typedef Future<dynamic> MessageHandler(Map<String, dynamic> message);

void _callFcmSetupBackgroundChannel(
    {MethodChannel backgroundChannel = const MethodChannel(
        'plugins.wolfpack.app/firebase_messaging_background')}) async {
  // Setup Flutter state needed for MethodChannels.
  WidgetsFlutterBinding.ensureInitialized();

  // This is where the magic happens and we handle background events from the
  // native portion of the plugin.
  backgroundChannel.setMethodCallHandler((MethodCall call) async {
    if (call.method == 'handleBackgroundMessage') {
      final CallbackHandle handle =
      CallbackHandle.fromRawHandle(call.arguments['handle']);
      final Function handlerFunction =
      PluginUtilities.getCallbackFromHandle(handle);
      try {
        await handlerFunction(
            Map<String, dynamic>.from(call.arguments['message']));
      } catch (e) {
        print('Unable to handle incoming background message.');
        print(e);
      }
      return Future<void>.value();
    }
  });

  // Once we've finished initializing, let the native portion of the plugin
  // know that it can start scheduling handling messages.
  backgroundChannel.invokeMethod<void>('CallFcmDartService#initialized');
}

class CallFirebaseMessaging {

  factory CallFirebaseMessaging() => _instance;

  @visibleForTesting
  CallFirebaseMessaging.private(MethodChannel channel)
      : _channel = channel;

  static final CallFirebaseMessaging _instance = CallFirebaseMessaging.private(
    const MethodChannel('plugins.wolfpack.app/firebase_messaging'),
  );

  final MethodChannel _channel;

  MessageHandler _onMessage;
  MessageHandler _onBackgroundMessage;
  MessageHandler _onLaunch;
  MessageHandler _onResume;

  void configure({
    MessageHandler onMessage,
    MessageHandler onBackgroundMessage,
    MessageHandler onLaunch,
    MessageHandler onResume,
  }) {

    _onMessage = onMessage;
    _onLaunch = onLaunch;
    _onResume = onResume;
    _channel.setMethodCallHandler(_handleMethod);
    _channel.invokeMethod<void>('configure');
    if (onBackgroundMessage != null) {
      _onBackgroundMessage = onBackgroundMessage;
      final CallbackHandle backgroundSetupHandle =
      PluginUtilities.getCallbackHandle(_callFcmSetupBackgroundChannel);
      final CallbackHandle backgroundMessageHandle =
      PluginUtilities.getCallbackHandle(_onBackgroundMessage);

      if (backgroundMessageHandle == null) {
        throw ArgumentError(
          '''Failed to setup background message handler! `onBackgroundMessage`
          should be a TOP-LEVEL OR STATIC FUNCTION and should NOT be tied to a
          class or an anonymous function.''',
        );
      }

      _channel.invokeMethod<bool>(
        'CallFcmDartService#start',
        <String, dynamic>{
          'setupHandle': backgroundSetupHandle.toRawHandle(),
          'backgroundHandle': backgroundMessageHandle.toRawHandle()
        },
      );
    }
  }

  Future<dynamic> _handleMethod(MethodCall call) async {
    switch (call.method) {
      case "onMessage":
        return _onMessage(call.arguments.cast<String, dynamic>());
      case "onLaunch":
        return _onLaunch(call.arguments.cast<String, dynamic>());
      case "onResume":
        return _onResume(call.arguments.cast<String, dynamic>());
      default:
        throw UnsupportedError("Unrecognized JSON message");
    }
  }
}