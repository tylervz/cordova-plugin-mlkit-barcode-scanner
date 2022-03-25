package com.mobisys.cordova.plugins.mlkit.barcode.scanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.mobisys.cordova.plugins.mlkit.barcode.scanner.utils.BitmapUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for capturing a barcode with the scanner.
 */
public class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback {

  /**
   * All of the barcode formats we are interested in detecting, specified in a single integer.
   * The value is the sum of all the barcode formats we want to detect,
   * defined in {@link com.google.mlkit.vision.barcode.common.Barcode.BarcodeFormat}
   * and our TypeScript file Detector.ts.
   */
  private Integer barcodeFormats;
  /**
   * Proportion of the screen that should be filled with a rectangular box
   * to indicate to the user where the app is looking for barcodes.
   * Value must be between 0 and 1.
   */
  private double detectorSize;

  /**
   * Constant used for setting and retrieving data in an Intent.
   */
  public static final String BARCODE_FORMAT = "MLKitBarcodeFormat";
  /**
   * Constant used for setting and retrieving data in an Intent.
   */
  public static final String BARCODE_TYPE = "MLKitBarcodeType";
  /**
   * Constant used for setting and retrieving data in an Intent.
   */
  public static final String BARCODE_VALUE = "MLKitBarcodeValue";

  private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
  private ExecutorService executor = Executors.newSingleThreadExecutor();
  private PreviewView mCameraView;
  private SurfaceHolder holder;
  private SurfaceView surfaceView;

  private static final int RC_HANDLE_CAMERA_PERM = 2;
  private ImageButton torchButton;
  private Camera camera;

  private ScaleGestureDetector scaleGestureDetector;
  private GestureDetector gestureDetector;

  /**
   * Called when the activity is starting. This is where most initialization should go.
   * @param savedInstanceState  If the activity is being re-initialized after previously being
   * shut down then this Bundle contains the data it most recently supplied
   * in onSaveInstanceState(Bundle). Note: Otherwise it is null.
   */
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(getResources().getIdentifier("capture_activity", "layout", getPackageName()));

    // Create the bounding box
    surfaceView = findViewById(getResources().getIdentifier("overlay", "id", getPackageName()));
    surfaceView.setZOrderOnTop(true);

    holder = surfaceView.getHolder();
    holder.setFormat(PixelFormat.TRANSPARENT);
    holder.addCallback(this);

    // read parameters from the intent used to launch the activity.
    barcodeFormats = getIntent().getIntExtra("BarcodeFormats", 0);
    detectorSize = getIntent().getDoubleExtra("DetectorSize", .5);

    // setting boundary detectorSize must be between 0 to 1.
    if (detectorSize <= 0 || detectorSize >= 1) {
      detectorSize = 0.5;
    }

    int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

    if (rc == PackageManager.PERMISSION_GRANTED) {
      // Start Camera
      startCamera();
    } else {
      requestCameraPermission();
    }

    gestureDetector = new GestureDetector(this, new CaptureGestureListener());
    scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

    torchButton = findViewById(getResources().getIdentifier(
        "torch_button", "id", this.getPackageName()));

    torchButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {

        LiveData<Integer> flashState = camera.getCameraInfo().getTorchState();
        if (flashState.getValue() != null) {
          boolean state = flashState.getValue() == 1;
          torchButton.setBackgroundResource(getResources().getIdentifier(
              !state ? "torch_active" : "torch_inactive",
              "drawable", CaptureActivity.this.getPackageName()));
          camera.getCameraControl().enableTorch(!state);
        }

      }
    });

  }

  // ----------------------------------------------------------------------------
  // | Helper classes
  // ----------------------------------------------------------------------------
  private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      return super.onSingleTapConfirmed(e);
    }
  }

  private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
      return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

      if (camera != null) {
        float scale = camera.getCameraInfo().getZoomState().getValue().getZoomRatio()
            * detector.getScaleFactor();
        camera.getCameraControl().setZoomRatio(scale);
      }
    }
  }

  private void requestCameraPermission() {

    final String[] permissions = new String[] {
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE };

    boolean shouldShowPermission = !ActivityCompat.shouldShowRequestPermissionRationale(this,
        Manifest.permission.CAMERA);
    shouldShowPermission = shouldShowPermission
        && !ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

    if (shouldShowPermission) {
      ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
      return;
    }

    View.OnClickListener listener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        ActivityCompat.requestPermissions(CaptureActivity.this, permissions,
            RC_HANDLE_CAMERA_PERM);
      }
    };

    findViewById(getResources().getIdentifier("topLayout", "id", getPackageName()))
        .setOnClickListener(listener);
    Snackbar
        .make(surfaceView, getResources().getIdentifier("permission_camera_rationale",
            "string", getPackageName()), Snackbar.LENGTH_INDEFINITE)
        .setAction(getResources().getIdentifier("ok", "string", getPackageName()), listener)
        .show();

  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (requestCode != RC_HANDLE_CAMERA_PERM) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      return;
    }

    if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      startCamera();
      drawFocusRect(Color.parseColor("#FFFFFF"));
      return;
    }

    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        finish();
      }
    };

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Camera permission required")
        .setMessage(getResources().getIdentifier(
            "no_camera_permission", "string", getPackageName()))
        .setPositiveButton(getResources().getIdentifier("ok", "string", getPackageName()),
            listener).show();
  }

  /**
   * This is called immediately after the surface is first created.
   * @param surfaceHolder Abstract interface to someone holding the display surface.
   * Allows you to control the surface size and format, edit the pixels
   * in the surface, and monitor changes to the surface.
   */
  @Override
  public void surfaceCreated(SurfaceHolder surfaceHolder) {

  }

  /**
   * This is called immediately after any structural changes (format or size)
   * have been made to the surface.
   * @param surfaceHolder the SurfaceHolder whose surface has changed.
   * @param i The new {@link PixelFormat} of the surface.
   * @param i1 The new width of the surface.
   * @param i2 The new height of the surface.
   */
  @Override
  public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
    drawFocusRect(Color.parseColor("#FFFFFF"));
  }

  /**
   * This is called immediately before a surface is being destroyed.
   * @param surfaceHolder the SurfaceHolder whose surface is being destroyed.
   */
  @Override
  public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

  }

  /**
   * Called when a touch screen event was not handled by any of the views under it.
   * @param e the touch screen event being processed.
   * @return Returns true if you have consumed the event, false if you haven't.
   */
  @Override
  public boolean onTouchEvent(MotionEvent e) {
    boolean b = scaleGestureDetector.onTouchEvent(e);
    boolean c = gestureDetector.onTouchEvent(e);

    return b || c || super.onTouchEvent(e);
  }

  /**
   * Called as part of the activity lifecycle when the user no longer actively interacts
   * with the activity, but it is still visible on screen. The counterpart to onResume().
   */
  @Override
  protected void onPause() {
    super.onPause();
  }

  /**
   * Called after onRestoreInstanceState(Bundle), onRestart(), or onPause().
   * This is usually a hint for your activity to start interacting with the user,
   * which is a good indicator that the activity became active and ready to receive input.
   */
  @Override
  protected void onResume() {
    super.onResume();
  }

  private void startCamera() {
    mCameraView = findViewById(getResources().getIdentifier("previewView",
        "id", getPackageName()));
    mCameraView.setPreferredImplementationMode(PreviewView.ImplementationMode.TEXTURE_VIEW);

    Boolean rotateCamera = getIntent().getBooleanExtra("RotateCamera", false);
    if (rotateCamera) {
      mCameraView.setScaleX(-1F);
      mCameraView.setScaleY(-1F);
    } else {
      mCameraView.setScaleX(1F);
      mCameraView.setScaleY(1F);
    }

    // mCameraView.setScaleType(PreviewView.ScaleType.FIT_CENTER);

    cameraProviderFuture = ProcessCameraProvider.getInstance(this);
    cameraProviderFuture.addListener(new Runnable() {
      @Override
      public void run() {
        try {
          ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
          CaptureActivity.this.bindPreview(cameraProvider);

        } catch (ExecutionException | InterruptedException e) {
          // No errors need to be handled for this Future.
          // This should never be reached.
        }
      }
    }, ContextCompat.getMainExecutor(this));
  }

  /**
   * Binding to camera.
   * @param cameraProvider the singleton camera provider for the Activity
   */
  private void bindPreview(ProcessCameraProvider cameraProvider) {

    int barcodeFormat;
    if (barcodeFormats == 0) {
      barcodeFormat = (Barcode.FORMAT_CODE_39 | Barcode.FORMAT_DATA_MATRIX);
    } else {
      barcodeFormat = barcodeFormats;
    }

    Preview preview = new Preview.Builder().build();

    CameraSelector cameraSelector = new CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();

    preview.setSurfaceProvider(mCameraView.createSurfaceProvider());

    ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setTargetAspectRatio(AspectRatio.RATIO_16_9).build();

    BarcodeScanner scanner = BarcodeScanning
        .getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(barcodeFormat).build());

    imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
      @SuppressLint("UnsafeExperimentalUsageError")
      @Override
      public void analyze(@NonNull ImageProxy image) {

        if (image == null || image.getImage() == null) {
          return;
        }

        Bitmap bmp = BitmapUtils.getBitmap(image);

        int height = bmp.getHeight();
        int width = bmp.getWidth();

        int diameter = Math.min(height, width);

        int offset = (int) ((1 - detectorSize) * diameter);
        diameter -= offset;

        int left = width / 2 - diameter / 2;
        int top = height / 2 - diameter / 2;
        int right = width / 2 + diameter / 2;
        int bottom = height / 2 + diameter / 2;

        int boxHeight = bottom - top;
        int boxWidth = right - left;

        Bitmap bitmap = Bitmap.createBitmap(bmp, left, top, boxWidth, boxHeight);
        scanner.process(InputImage.fromBitmap(bitmap, image.getImageInfo().getRotationDegrees()))
            .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
              @Override
              public void onSuccess(List<Barcode> barCodes) {

                // # Code to test image viewfinder
                /*
                 * ImageView imageView = (ImageView)
                 * findViewById(getResources().getIdentifier("imageView", "id",
                 * getPackageName())); imageView.setImageBitmap(bitmap);
                 */

                if (barCodes.size() > 0) {
                  for (Barcode barcode : barCodes) {
                    // Toast.makeText(CaptureActivity.this, "FOUND: " + barcode.getDisplayValue(),
                    // Toast.LENGTH_SHORT).show();
                    Intent data = new Intent();
                    String value = barcode.getRawValue();

                    // rawValue returns null if string is not UTF-8 encoded.
                    // If that's the case, we will decode it as ASCII,
                    // because it's the most common encoding for barcodes.
                    // e.g. https://www.barcodefaq.com/1d/code-128/
                    if (barcode.getRawValue() == null) {
                      value = new String(barcode.getRawBytes(), StandardCharsets.US_ASCII);
                    }

                    data.putExtra(BARCODE_FORMAT, barcode.getFormat());
                    data.putExtra(BARCODE_TYPE, barcode.getValueType());
                    data.putExtra(BARCODE_VALUE, value);
                    setResult(CommonStatusCodes.SUCCESS, data);
                    finish();

                  }
                }
              }
            }).addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {

              }
            }).addOnCompleteListener(new OnCompleteListener<List<Barcode>>() {
              @Override
              public void onComplete(@NonNull Task<List<Barcode>> task) {
                image.close();
              }
            });
      }

    });

    camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector,
        imageAnalysis, preview);
  }

  /**
   * For drawing the rectangular box.
   * @param color color of the rectangle, specified as an integer in r,g,b,a format.
   */
  private void drawFocusRect(int color) {

    if (mCameraView != null) {
      int height = mCameraView.getHeight();
      int width = mCameraView.getWidth();

      int diameter = Math.min(height, width);

      int offset = (int) ((1 - detectorSize) * diameter);
      diameter -= offset;

      Canvas canvas = holder.lockCanvas();
      canvas.drawColor(0, PorterDuff.Mode.CLEAR);
      // border's properties
      Paint paint = new Paint();
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(color);
      paint.setStrokeWidth(5);

      int left = width / 2 - diameter / 2;
      int top = height / 2 - diameter / 2;
      int right = width / 2 + diameter / 2;
      int bottom = height / 2 + diameter / 2;

      // Changing the value of x in diameter/x will change the size of the box ;
      // inversely proportionate to x
      // If the detectorSize is 0.3 or less, then it is too small to make the rectangle rounded.
      final double cutoff = 0.3;
      if (detectorSize <= cutoff) {
        canvas.drawRect(new RectF(left, top, right, bottom), paint);
      } else {
        final float radius = 100;
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
      }

      holder.unlockCanvasAndPost(canvas);
    }

  }
}
