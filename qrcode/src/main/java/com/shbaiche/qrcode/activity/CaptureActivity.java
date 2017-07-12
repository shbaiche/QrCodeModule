package com.shbaiche.qrcode.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.Result;
import com.shbaiche.qrcode.R;
import com.shbaiche.qrcode.camera.CameraManager;
import com.shbaiche.qrcode.decode.DecodeThread;
import com.shbaiche.qrcode.utils.BeepManager;
import com.shbaiche.qrcode.utils.CaptureActivityHandler;
import com.shbaiche.qrcode.utils.InactivityTimer;

import java.io.IOException;
import java.lang.reflect.Field;


public class CaptureActivity extends Activity implements SurfaceHolder.Callback {

    private Context mContext;
    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;

    private SurfaceView scanPreview = null;
    private RelativeLayout scanContainer;
    private RelativeLayout scanCropView;
    private ImageView scanLine;

    private Rect mCropRect = null;
    private boolean isHasSurface = false;
    /*闪光灯默认关闭*/
    private boolean isTurn = false;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mContext = this;
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            getWindow().setStatusBarColor(Color.BLACK);
        }
        setContentView(R.layout.activity_capture);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        initView();
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        initScanAnim();
    }

    @Override
    protected void onResume() {
        try {
            super.onResume();
            cameraManager = new CameraManager(getApplication());
            handler = null;
            if (scanPreview != null) {
                if (isHasSurface) {
                    initCamera(scanPreview.getHolder());
                } else {
                    scanPreview.getHolder().addCallback(this);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        try {
            if (handler != null) {
                handler.quitSynchronously();
                handler = null;
            }
            inactivityTimer.onPause();
            beepManager.close();
            cameraManager.closeDriver();
            if (!isHasSurface) {
                if (scanPreview != null) {
                    scanPreview.getHolder().removeCallback(this);
                }
            }
            super.onPause();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        if (isTurn) {
            turnOffLight();
        }
        closeCamera();
        super.onDestroy();
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    private void initView() {
        scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
        scanContainer = (RelativeLayout) findViewById(R.id.capture_container);
        scanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
        scanLine = (ImageView) findViewById(R.id.capture_scan_line);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (!isHasSurface) {
                isHasSurface = true;
                cameraManager.closeDriver();
                initCamera(holder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isHasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    /**
     * A valid barcode has been found, so give an indication of isSuccess and show
     * the results.
     *
     * @param rawResult The contents of the barcode.
     * @param bundle    The extras
     */
    public void handleDecode(Result rawResult, Bundle bundle) {
        inactivityTimer.onActivity();
        beepManager.playBeepSoundAndVibrate();
        closeCamera();
        Intent intent = new Intent();
        intent.putExtra("result", rawResult.getText());
        setResult(RESULT_OK, intent);
        finish();
    }

    private void closeCamera() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        cameraManager.closeDriver();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            if (surfaceHolder == null) {
                throw new IllegalStateException("No SurfaceHolder provided");
            }
            if (cameraManager.isOpen()) {
                return;
            }
            cameraManager.openDriver(surfaceHolder);
            if (handler == null) {
                handler = new CaptureActivityHandler(this, cameraManager,
                        DecodeThread.ALL_MODE);
            }
            cameraManager.turnAuto();
            initCrop();
        } catch (IOException ioe) {
            Toast.makeText(mContext, ioe.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void restartCamera() {
        scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
        SurfaceHolder surfaceHolder = scanPreview.getHolder();
        initCamera(surfaceHolder);
        inactivityTimer.onResume();
    }

    /**
     * if you dont finish this activity an scan again ,
     * you can call this method before something
     */
    private void startScanAgain() {
        closeCamera();
        restartCamera();
    }

    public Rect getCropRect() {
        return mCropRect;
    }

    private void initCrop() {
        @SuppressWarnings("SuspiciousNameCombination") int cameraWidth = cameraManager.getCameraResolution().y;
        @SuppressWarnings("SuspiciousNameCombination") int cameraHeight = cameraManager.getCameraResolution().x;

        int[] location = new int[2];
        scanCropView.getLocationInWindow(location);

        int cropLeft = location[0];
        int cropTop = location[1] - getStatusBarHeight();

        int cropWidth = scanCropView.getWidth();
        int cropHeight = scanCropView.getHeight();

        int containerWidth = scanContainer.getWidth();
        int containerHeight = scanContainer.getHeight();

        int x = cropLeft * cameraWidth / containerWidth;
        int y = cropTop * cameraHeight / containerHeight;

        int width = cropWidth * cameraWidth / containerWidth;
        int height = cropHeight * cameraHeight / containerHeight;

        mCropRect = new Rect(x, y, width + x, height + y);
    }

    private int getStatusBarHeight() {
        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object obj = c.newInstance();
            Field field = c.getField("status_bar_height");
            int x = Integer.parseInt(field.get(obj).toString());
            return getResources().getDimensionPixelSize(x);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }


    /**
     * init the scanLine animation
     * default the translateY
     */
    private void initScanAnim() {
        TranslateAnimation animation = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.9f);
        animation.setDuration(4000);
        animation.setRepeatCount(-1);
        animation.setRepeatMode(Animation.RESTART);
        scanLine.startAnimation(animation);
    }

    /**
     * Turn or Off thr Light
     */
    private void turnOffLight() {
        if (isFlashSupported()) {
            Toast.makeText(mContext, "该手机暂不支持闪光灯", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isTurn) {
            cameraManager.offLight();
            isTurn = false;
        } else {
            cameraManager.turnLight();
            isTurn = true;
        }
    }

    private boolean isFlashSupported() {
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }
}