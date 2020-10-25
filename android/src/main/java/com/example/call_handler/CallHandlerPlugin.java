package com.example.call_handler;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.NewIntentListener;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** CallHandlerPlugin */
public class CallHandlerPlugin extends BroadcastReceiver
    implements MethodCallHandler, NewIntentListener, FlutterPlugin, ActivityAware {
  private static final String ACCEPT_CALL = "ACCEPT_CALL";
  public static final String ACTION_REMOTE_MESSAGE = "com.wolfpack.plugins.firebasemessaging.CALL_NOTIFICATION";
  public static final String EXTRA_REMOTE_MESSAGE = "notification";
  private static final String TAG = "WP";

  private MethodChannel channel;
  private Context applicationContext;
  private Activity mainActivity;

  public static void registerWith(Registrar registrar) {
    CallHandlerPlugin instance = new CallHandlerPlugin();
    instance.setActivity(registrar.activity());
    registrar.addNewIntentListener(instance);
    instance.onAttachedToEngine(registrar.context(), registrar.messenger());
  }

  private void onAttachedToEngine(Context context, BinaryMessenger binaryMessenger) {
    Log.d(TAG, "attached [CallFirebaseMessagingPlugin] to engine");
    this.applicationContext = context;
    FirebaseApp.initializeApp(applicationContext);
    channel = new MethodChannel(binaryMessenger, "plugins.wolfpack.app/firebase_messaging");
    channel.setMethodCallHandler(this);

    // Register broadcast receiver
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ACCEPT_CALL);
    LocalBroadcastManager manager = LocalBroadcastManager.getInstance(applicationContext);
    manager.registerReceiver(this, intentFilter);
  }

  private void setActivity(Activity flutterActivity) {
    this.mainActivity = flutterActivity;
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    onAttachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger());
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    LocalBroadcastManager.getInstance(binding.getApplicationContext()).unregisterReceiver(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
    Log.d(TAG, "received method call: " + call.method);

    if ("configure".equals(call.method)) {

      if (mainActivity != null) {
        String action = mainActivity.getIntent().getAction();
        if (action.equals(ACCEPT_CALL)) {
          RemoteMessage message = mainActivity.getIntent().getParcelableExtra(EXTRA_REMOTE_MESSAGE);
          Map<String, Object> content = parseRemoteMessage(message);
          channel.invokeMethod("onLaunch", content);
        }
      }
      result.success(null);
    } else {
      result.notImplemented();
    }
  }

  // BroadcastReceiver implementation.
  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    Log.d(TAG, "[onReceive] new action: " + action);

    if (action == null) {
      return;
    }

    if (action.equals(ACCEPT_CALL)) {
      RemoteMessage message =
              intent.getParcelableExtra(EXTRA_REMOTE_MESSAGE);
      Map<String, Object> content = parseRemoteMessage(message);
      Log.d(TAG, "[onReceive] invoking onMessage");
      channel.invokeMethod("onMessage", content);
    }
  }

  @NonNull
  private Map<String, Object> parseRemoteMessage(RemoteMessage message) {
    Map<String, Object> content = new HashMap<>();
    content.put("data", message.getData());

    RemoteMessage.Notification notification = message.getNotification();

    Map<String, Object> notificationMap = new HashMap<>();

    String title = notification != null ? notification.getTitle() : null;
    notificationMap.put("title", title);

    String body = notification != null ? notification.getBody() : null;
    notificationMap.put("body", body);

    content.put("notification", notificationMap);
    return content;
  }

  @Override
  public boolean onNewIntent(Intent intent) {
    Log.d(TAG, "new intent with action: " + intent.getAction());
    boolean res = sendMessageFromIntent("onResume", intent);
    if (res && mainActivity != null) {
      mainActivity.setIntent(intent);
    }
    return res;
  }

  /** @return true if intent contained a message to send. */
  private boolean sendMessageFromIntent(String method, Intent intent) {
    Log.d(TAG, "intent action: " + intent.getAction());
    if (ACCEPT_CALL.equals(intent.getAction())
            || ACCEPT_CALL.equals(intent.getStringExtra("click_action"))) {
      Map<String, Object> message = new HashMap<>();
      Bundle extras = intent.getExtras();

      if (extras == null) {
        Log.d(TAG, "no extra value available so ignoring");
        return false;
      }

      Map<String, Object> notificationMap = new HashMap<>();
      Map<String, Object> dataMap = new HashMap<>();

      for (String key : extras.keySet()) {
        Object extra = extras.get(key);
        if (extra != null) {
          dataMap.put(key, extra);
        }
      }

      message.put("notification", notificationMap);
      message.put("data", dataMap);

      Log.d(TAG, "invoking method: " + method);
      channel.invokeMethod(method, message);
      return true;
    }
    return false;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    binding.addOnNewIntentListener(this);
    this.mainActivity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    this.mainActivity = null;
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    binding.addOnNewIntentListener(this);
    this.mainActivity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivity() {
    this.mainActivity = null;
  }
}
