package com.mobisys.cordova.plugins.mlkit.barcode.scanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import com.google.android.gms.common.api.CommonStatusCodes;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Main Java class for the Cordova plugin. Handles all the JavaScript calls
 * and launches the CaptureActivity for users to scan and detect barcodes
 * with the Google ML Kit Vision library.
 */
public class MLKitBarcodeScanner extends CordovaPlugin {

  private static final int RC_BARCODE_CAPTURE = 9001;
  private CallbackContext callbackContext;
  private Boolean beepOnSuccess;
  private Boolean vibrateOnSuccess;
  private MediaPlayer mediaPlayer;
  private Vibrator vibrator;

  /**
   * Initialize the Cordova plugin.
   * @param cordova interface for the Cordova app
   * @param webView the Cordova app web view
   */
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    Context context = cordova.getContext();

    vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    mediaPlayer = new MediaPlayer();

    try {
      AssetFileDescriptor descriptor = context.getAssets().openFd("beep.ogg");
      mediaPlayer.setDataSource(descriptor.getFileDescriptor(),
          descriptor.getStartOffset(), descriptor.getLength());
      descriptor.close();
      mediaPlayer.prepare();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * This method gets called when our plugin's exec() JavaScript method of is called.
   *
   * @param action The action to execute.
   * @param args The exec() arguments.
   * @param cordovaCallbackContext The callback context used when calling back into JavaScript.
   * @return Returns true when the action has been executed successfully. Returns false otherwise,
   * resulting in a "MethodNotFound" error.
   * @throws JSONException
   */
  @Override
  public boolean execute(String action, JSONArray args, CallbackContext cordovaCallbackContext)
      throws JSONException {

    Activity activity = cordova.getActivity();
    Boolean hasCamera = activity.getPackageManager()
        .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    CameraManager cameraManager = (CameraManager) activity
        .getSystemService(Context.CAMERA_SERVICE);

    callbackContext = cordovaCallbackContext;

    int numberOfCameras = 0;

    try {
      numberOfCameras = cameraManager.getCameraIdList().length;
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (!hasCamera || numberOfCameras == 0) {
      AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
      alertDialog.setMessage(activity.getString(activity.getResources()
          .getIdentifier("no_cameras_found", "string", activity.getPackageName())));
      alertDialog.setButton(
          AlertDialog.BUTTON_POSITIVE, activity.getString(activity.getResources()
              .getIdentifier("ok", "string", activity.getPackageName())),
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
              dialog.dismiss();
            }
          });
      alertDialog.show();
      return false;
    }

    if (action.equals("startScan")) {
      final class OneShotTask implements Runnable {
        private final Context context;
        private final JSONArray args;

        private OneShotTask(Context ctx, JSONArray as) {
          context = ctx;
          args = as;
        }

        public void run() {
          try {
            openNewActivity(context, args);
          } catch (JSONException e) {
            callbackContext.sendPluginResult(
                new PluginResult(PluginResult.Status.ERROR, e.toString()));
          }
        }
      }
      Thread t = new Thread(new OneShotTask(cordova.getContext(), args));
      t.start();
      return true;
    }
    return false;
  }

  private void openNewActivity(Context context, JSONArray args) throws JSONException {
    JSONObject config = args.getJSONObject(0);
    Intent intent = new Intent(context, CaptureActivity.class);
    intent.putExtra("BarcodeFormats", config.optInt("barcodeFormats", 0));
    intent.putExtra("DetectorSize", config.optDouble("detectorSize", 0.5));
    intent.putExtra("RotateCamera", config.optBoolean("rotateCamera", false));

    beepOnSuccess = config.optBoolean("beepOnSuccess", false);
    vibrateOnSuccess = config.optBoolean("vibrateOnSuccess", false);

    this.cordova.setActivityResultCallback(this);
    this.cordova.startActivityForResult(this, intent, RC_BARCODE_CAPTURE);
  }

  /**
   * This method is called once the CaptureActivity has finished.
   * @param requestCode The request code originally supplied to startActivityForResult(),
   * allowing you to identify who this result came from.
   * @param resultCode the integer result code returned by the child activity
   * through its setResult().
   * @param data extra data returned by the CaptureActivity.
   */
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == RC_BARCODE_CAPTURE) {
      if (resultCode == CommonStatusCodes.SUCCESS) {
        if (data != null) {
          Integer barcodeFormat = data.getIntExtra(CaptureActivity.BARCODE_FORMAT, 0);
          Integer barcodeType = data.getIntExtra(CaptureActivity.BARCODE_TYPE, 0);
          String barcodeValue = data.getStringExtra(CaptureActivity.BARCODE_VALUE);
          JSONArray result = new JSONArray();
          result.put(barcodeValue);
          result.put(barcodeFormat);
          result.put(barcodeType);
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));

          if (beepOnSuccess) {
            mediaPlayer.start();
          }

          if (vibrateOnSuccess) {
            final Integer durationMilliseconds = 200;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              vibrator.vibrate(VibrationEffect.createOneShot(
                  durationMilliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
              // deprecated in API 26 aka Oreo
              vibrator.vibrate(durationMilliseconds);
            }
          }

          Log.d("MLKitBarcodeScanner", "Barcode read: " + barcodeValue);
        }
      } else {
        String err = data.getStringExtra("err");
        JSONArray result = new JSONArray();
        result.put(err);
        result.put("");
        result.put("");
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, result));
      }
    }
  }

  /**
   * Called when the plugin is the recipient of an Activity result after the
   * CordovaActivity has been destroyed. The Bundle will be the same as the one
   * the plugin returned in onSaveInstanceState()
   * @param state Bundle containing the state of the plugin.
   * @param cordovaCallbackContext Replacement Context to return the plugin result to.
   */
  @Override
  public void onRestoreStateForActivityResult(
      Bundle state, CallbackContext cordovaCallbackContext) {

    callbackContext = cordovaCallbackContext;
  }
}
