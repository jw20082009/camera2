package com.eyedog.camera2.dev;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;
import com.eyedog.camera2.Constant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * created by jw200 at 2018/11/6 17:42
 **/
public class CameraView extends AutoFitTextureView {
    private Activity mActivity;
    private String CAMERA_ID_BACK = "0";
    private String CAMERA_ID_FRONT = "1";
    private String mCurrentCameraId;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CameraCharacteristics mCharacteristics;
    private Size mPreviewSize, mVideoSize;
    private CameraDevice mCameraDevice;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String[] VIDEO_PERMISSIONS = {
        Manifest.permission.CAMERA
    };

    private static final int STATE_CLOSED = 0;
    private static final int STATE_OPENED = 1;
    private static final int STATE_PREVIEW = 2;
    private static final int STATE_WAITING_FOR_3A_CONVERGENCE = 3;
    private int mState = STATE_CLOSED;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest.Builder mPreviewBuilder;
    private CaptureRequest mPreviewRequest;
    private final Object mCameraStateLock = new Object();

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        if (getContext() == null) return;
        mActivity = (Activity) getContext();
        if (null == mActivity || mActivity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            if (cameraIds != null && cameraIds.length > 0) {
                for (String id : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                    Integer level =
                        characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    if (level == null
                        || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                        continue;
                    }
                    int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        CAMERA_ID_BACK = id;
                    } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        CAMERA_ID_FRONT = id;
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mCurrentCameraId = CAMERA_ID_BACK;//default preSet back
    }

    public void preSetBackCamera() {
        mCurrentCameraId = CAMERA_ID_BACK;
    }

    public void preSetFrontCamera() {
        mCurrentCameraId = CAMERA_ID_FRONT;
    }

    public void onResume() {
        startBackgroundThread();
        if (isAvailable()) {
            openCamera();
        } else {
            setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    public void onPause() {
        closePreviewSession();
        closeCamera();
        stopBackgroundThread();
    }

    protected void openCamera() {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        try {
            synchronized (mCameraStateLock) {
                if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Time out waiting to lock camera opening.");
                }
                CameraManager manager =
                    (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(mCurrentCameraId);
                mCharacteristics = characteristics;
                StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    throw new RuntimeException("Cannot get available preview/video sizes");
                }
                mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    Constant.PREVIEW_WIDTH, Constant.PREVIEW_HEIGHT, mVideoSize);
                if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                manager.openCamera(mCurrentCameraId, mStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
        }
    }

    protected void startPreview() {
        if (null == mCameraDevice || !isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            Surface previewSurface = new Surface(texture);
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(surfaces,
                new CameraCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        mPreviewSession = session;
                        updatePreview();
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Activity activity = (Activity) getContext();
                        if (null != activity) {
                            Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, mBackgroundHandler);
            mState = STATE_PREVIEW;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (null == mCameraDevice || mPreviewBuilder == null || mPreviewSession == null) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            mPreviewRequest = mPreviewBuilder.build();
            mPreviewSession.setRepeatingRequest(mPreviewRequest, mPreCaptureCallback,
                mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    public static Size chooseVideoSize(Size[] choices) {
        for (Size option : choices) {
            int optionWidth = option.getWidth();
            int optionHeight = option.getHeight();
            if (option.getWidth() < option.getHeight()) {
                optionWidth = option.getHeight();
                optionHeight = option.getWidth();
            }
            if (optionWidth == optionHeight * 16 / 9 && optionWidth <= 1280) {
                return option;
            }
        }
        return choices[choices.length - 1];
    }

    public static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        List<Size> sameRatioS = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            int optionWidth = option.getWidth();
            int optionHeight = option.getHeight();
            if ((width > height && option.getWidth() < option.getHeight()) || (width < height
                && option.getWidth() > option.getHeight())) {
                optionWidth = option.getHeight();
                optionHeight = option.getWidth();
            }
            if (option.getHeight() == option.getWidth() * h / w) {
                if (optionWidth >= width && optionHeight >= height) {
                    bigEnough.add(option);
                } else {
                    sameRatioS.add(option);
                }
            }
        }
        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            if (sameRatioS.size() > 0) {
                return Collections.max(sameRatioS, new CompareSizesByArea());
            } else {
                return choices[0];
            }
        }
    }

    protected void closePreviewSession() {
        synchronized (mCameraStateLock) {
            if (mPreviewSession != null && mCameraDevice != null) {
                try {
                    mPreviewSession.stopRepeating();
                    mPreviewSession.abortCaptures();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mPreviewSession.close();
                    mPreviewSession = null;
                }
            }
        }
    }

    protected void closeCamera() {
        synchronized (mCameraStateLock) {
            if (mCameraDevice != null) {
                try {
                    mCameraOpenCloseLock.acquire();
                    if (mPreviewRequest != null) {
                        mPreviewRequest = null;
                    }
                    if (mPreviewBuilder != null) {
                        mPreviewBuilder = null;
                    }
                    // Reset state and clean up resources used by the camera.
                    // Note: After calling this, the ImageReaders will be closed after any background
                    // tasks saving Images from these readers have been completed.
                    mState = STATE_CLOSED;
                    if (null != mCameraDevice) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    mCameraOpenCloseLock.release();
                }
            }
        }
    }

    protected void requestVideoPermissions() {
        ActivityCompat.requestPermissions(mActivity, VIDEO_PERMISSIONS,
            REQUEST_VIDEO_PERMISSIONS);
    }

    protected boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(mActivity, permission)
                != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    protected void stopBackgroundThread() {
        if (mBackgroundHandler != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    protected TextureView.SurfaceTextureListener mSurfaceTextureListener
        = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
            int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
            int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    protected static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    protected CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mState = STATE_OPENED;
            mCameraDevice = cameraDevice;
            mCameraOpenCloseLock.release();
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mState = STATE_CLOSED;
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mState = STATE_CLOSED;
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            if (null != mActivity && !mActivity.isFinishing()) {
                mActivity.finish();
            }
        }
    };

    private void configureTransform(int viewWidth, int viewHeight) {
        synchronized (mCameraStateLock) {
            if (null == mPreviewSize) {
                return;
            }
            int rotation =
                ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            }
            setTransform(matrix);
        }
    }

    protected CameraCaptureSession.CaptureCallback mPreCaptureCallback
        = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is running normally.
                    break;
                }
                case STATE_WAITING_FOR_3A_CONVERGENCE: {
                    //if (mPendingUserCaptures > 0) {
                    //    // Capture once for each user tap of the "Picture" button.
                    //    while (mPendingUserCaptures > 0) {
                    //        captureStillPictureLocked();
                    //        mPendingUserCaptures--;
                    //    }
                    //    // After this, the camera will go back to the normal state of preview.
                    //    mState = STATE_PREVIEW;
                    //}
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
            CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
            TotalCaptureResult result) {
            process(result);
        }
    };
}
