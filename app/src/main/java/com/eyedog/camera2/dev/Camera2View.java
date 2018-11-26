package com.eyedog.camera2.dev;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;
import com.eyedog.camera2.Constant;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * created by jw200 at 2018/11/6 17:42
 **/
public class Camera2View extends AutoFitTextureView {
    private final String TAG = "Camera2View";
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
    Camera camera;
    android.graphics.Camera camera2;
    private static final String[] VIDEO_PERMISSIONS = {
        Manifest.permission.CAMERA
    };
    private int mPendingUserCaptures = 0;
    private static final int STATE_CLOSED = 0;
    private static final int STATE_OPENED = 1;
    private static final int STATE_PREVIEW = 2;
    private static final int STATE_WAITING_FOR_3A_CONVERGENCE = 3;
    private int mState = STATE_CLOSED;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest.Builder mPreviewBuilder;
    private CaptureRequest mPreviewRequest;
    private final Object mCameraStateLock = new Object();
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mJpegResultQueue = new TreeMap<>();
    private RefCountedAutoCloseable<ImageReader> mJpegImageReader;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private final AtomicInteger mRequestCounter = new AtomicInteger();
    private long mCaptureTimer;
    private static final long PRECAPTURE_TIMEOUT_MS = 1000;
    private boolean mNoAFRun = false;
    int mPresetPictureWidth = 0, mPresetPictureHeight = 0;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    public Camera2View(Context context) {
        this(context, null);
    }

    public Camera2View(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Camera2View(Context context, AttributeSet attrs, int defStyle) {
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

    public void capture() {
        //synchronized (mCameraStateLock) {
        //    mPendingUserCaptures++;
        //    mState = STATE_WAITING_FOR_3A_CONVERGENCE;
        //    // Start a timer for the pre-capture sequence.
        //    startTimerLocked();
        //    // Replace the existing repeating request with one with updated 3A triggers.
        //    try {
        //        if (!mNoAFRun) {
        //            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
        //                CameraMetadata.CONTROL_AF_TRIGGER_START);
        //        }
        //
        //        // If this is not a legacy device, we can also trigger an auto-exposure metering
        //        // run.
        //        if (!isLegacyLocked()) {
        //            // Tell the camera to lock focus.
        //            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
        //                CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        //        }
        //        mPreviewSession.capture(mPreviewBuilder.build(), mPreCaptureCallback,
        //            mBackgroundHandler);
        //    } catch (CameraAccessException e) {
        //        e.printStackTrace();
        //    }
        //}
        captureStillPictureLocked();
    }

    private boolean isLegacyLocked() {
        return mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
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

    private void startTimerLocked() {
        mCaptureTimer = SystemClock.elapsedRealtime();
    }

    private boolean hitTimeoutLocked() {
        return (SystemClock.elapsedRealtime() - mCaptureTimer) > PRECAPTURE_TIMEOUT_MS;
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
                if (mPresetPictureWidth <= 0 || mPresetPictureHeight <= 0) {
                    initJpegImageReader(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    initJpegImageReader(mPresetPictureWidth, mPresetPictureHeight);
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

    private void initJpegImageReader(int width, int height) {
        //if (mJpegImageReader == null || mJpegImageReader.getAndRetain() == null) {
        mJpegImageReader = new RefCountedAutoCloseable<>(
            ImageReader.newInstance(width, height, ImageFormat.JPEG, /*maxImages*/5));
        mJpegImageReader.get()
            .setOnImageAvailableListener(mOnJpegImageAvailableListener, mBackgroundHandler);
        //}
    }

    private void captureStillPictureLocked() {
        try {
            final Activity activity = (Activity) getContext();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mJpegImageReader.get().getSurface());
            setUpCaptureRequestBuilder(captureBuilder);
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                sensorToDeviceRotation(mCharacteristics, rotation));
            // Set request tag to easily track results in callbacks.
            captureBuilder.setTag(mRequestCounter.getAndIncrement());
            CaptureRequest request = captureBuilder.build();
            // Create an ImageSaverBuilder in which to collect results, and add it to the queue
            // of active requests.
            ImageSaver.ImageSaverBuilder jpegBuilder =
                new ImageSaver.ImageSaverBuilder(activity)
                    .setCharacteristics(mCharacteristics);
            mJpegResultQueue.put((Integer) request.getTag(), jpegBuilder);
            mPreviewSession.capture(request, mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics c, int deviceOrientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        // Reverse device orientation for front-facing cameras
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceOrientation = -deviceOrientation;
        }
        return (sensorOrientation - deviceOrientation + 360) % 360;
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
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface previewSurface = new Surface(texture);
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);
            Surface jpegSurface = mJpegImageReader.get().getSurface();
            surfaces.add(jpegSurface);
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
        Float minFocusDist =
            mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

        // If MINIMUM_FOCUS_DISTANCE is 0, lens is fixed-focus and we need to skip the AF run.
        mNoAFRun = (minFocusDist == null || minFocusDist == 0);
        if (!mNoAFRun) {
            // If there is a "continuous picture" mode available, use it, otherwise default to AUTO.
            if (contains(mCharacteristics.get(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_AUTO);
            }
        }

        // If there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        if (contains(mCharacteristics.get(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON);
        }

        // If there is an auto-magical white balance control mode available, use it.
        if (contains(mCharacteristics.get(
            CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
            CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
            // Allow AWB to run auto-magically if this device supports this
            builder.set(CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }
    }

    private static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
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

    private void dequeueAndSaveImage(TreeMap<Integer, ImageSaver.ImageSaverBuilder> pendingQueue,
        RefCountedAutoCloseable<ImageReader> reader) {
        synchronized (mCameraStateLock) {
            Map.Entry<Integer, ImageSaver.ImageSaverBuilder> entry =
                pendingQueue.firstEntry();
            ImageSaver.ImageSaverBuilder builder = entry.getValue();

            // Increment reference count to prevent ImageReader from being closed while we
            // are saving its Images in a background thread (otherwise their resources may
            // be freed while we are writing to a file).
            if (reader == null || reader.getAndRetain() == null) {
                pendingQueue.remove(entry.getKey());
                return;
            }

            Image image;
            try {
                image = reader.get().acquireNextImage();
            } catch (IllegalStateException e) {
                pendingQueue.remove(entry.getKey());
                return;
            }
            builder.setRefCountedReader(reader).setImage(image);
            handleCompletionLocked(entry.getKey(), builder, pendingQueue);
        }
    }

    private void handleCompletionLocked(int requestId, ImageSaver.ImageSaverBuilder builder,
        TreeMap<Integer, ImageSaver.ImageSaverBuilder> queue) {
        if (builder == null) return;
        ImageSaver saver = builder.buildIfComplete();
        if (saver != null) {
            queue.remove(requestId);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(saver);
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
            post(new Runnable() {
                @Override
                public void run() {
                    int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }
                }
            });
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

    protected CameraCaptureSession.CaptureCallback mPreCaptureCallback
        = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is running normally.
                    break;
                }
                case STATE_WAITING_FOR_3A_CONVERGENCE: {
                    if (mPendingUserCaptures > 0) {
                        // Capture once for each user tap of the "Picture" button.
                        while (mPendingUserCaptures > 0) {
                            captureStillPictureLocked();
                            mPendingUserCaptures--;
                        }
                        // After this, the camera will go back to the normal state of preview.
                        mState = STATE_PREVIEW;
                    }
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

    public static class RefCountedAutoCloseable<T extends AutoCloseable> implements AutoCloseable {
        private T mObject;
        private long mRefCount = 0;

        public RefCountedAutoCloseable(T object) {
            if (object == null) throw new NullPointerException();
            mObject = object;
        }

        public synchronized T getAndRetain() {
            if (mRefCount < 0) {
                return null;
            }
            mRefCount++;
            return mObject;
        }

        public synchronized T get() {
            return mObject;
        }

        @Override
        public synchronized void close() {
            if (mRefCount >= 0) {
                mRefCount--;
                if (mRefCount < 0) {
                    try {
                        mObject.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        mObject = null;
                    }
                }
            }
        }
    }

    private static class ImageSaver implements Runnable {

        /**
         * The image to save.
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        /**
         * The CaptureResult for this image capture.
         */
        private final CaptureResult mCaptureResult;

        /**
         * The CameraCharacteristics for this camera device.
         */
        private final CameraCharacteristics mCharacteristics;

        /**
         * The Context to use when updating MediaStore with the saved images.
         */
        private final Context mContext;

        /**
         * A reference counted wrapper for the ImageReader that owns the given image.
         */
        private final RefCountedAutoCloseable<ImageReader> mReader;

        //private ImageSaver(Image image, File file, CaptureResult result,
        //    CameraCharacteristics characteristics, Context context,
        //    RefCountedAutoCloseable<ImageReader> reader) {
        //    this(image, file, result, characteristics, context, reader, null);
        //}

        private ImageSaver(Image image, File file, CaptureResult result,
            CameraCharacteristics characteristics, Context context,
            RefCountedAutoCloseable<ImageReader> reader) {
            mImage = image;
            mFile = file;
            mCaptureResult = result;
            mCharacteristics = characteristics;
            mContext = context;
            mReader = reader;
        }

        @Override
        public void run() {
            boolean success = false;
            int format = mImage.getFormat();
            switch (format) {
                case ImageFormat.JPEG: {
                    ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile);
                        output.write(bytes);
                        success = true;
                        ((Activity) mContext).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mContext,
                                    "Picture Saved success:" + mFile.getAbsolutePath(),
                                    Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                    break;
                }
                case ImageFormat.RAW_SENSOR: {
                    DngCreator dngCreator = new DngCreator(mCharacteristics, mCaptureResult);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile);
                        dngCreator.writeImage(output, mImage);
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                    break;
                }
                default: {
                    break;
                }
            }
            // Decrement reference count to allow ImageReader to be closed to free up resources.
            mReader.close();
        }

        /**
         * Cleanup the given {@link OutputStream}.
         *
         * @param outputStream the stream to close.
         */
        private static void closeOutput(OutputStream outputStream) {
            if (null != outputStream) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Builder class for constructing {@link ImageSaver}s.
         * <p/>
         * This class is thread safe.
         */
        public static class ImageSaverBuilder {
            private Image mImage;
            private File mFile;
            private CaptureResult mCaptureResult;
            private CameraCharacteristics mCharacteristics;
            private Context mContext;
            private RefCountedAutoCloseable<ImageReader> mReader;

            /**
             * Construct a new ImageSaverBuilder using the given {@link Context}.
             *
             * @param context a {@link Context} to for accessing the
             * {@link android.provider.MediaStore}.
             */
            public ImageSaverBuilder(final Context context) {
                mContext = context;
            }

            public synchronized ImageSaverBuilder setRefCountedReader(
                RefCountedAutoCloseable<ImageReader> reader) {
                if (reader == null) throw new NullPointerException();

                mReader = reader;
                return this;
            }

            public synchronized ImageSaverBuilder setImage(final Image image) {
                if (image == null) throw new NullPointerException();
                mImage = image;
                return this;
            }

            public synchronized ImageSaverBuilder setFile(final File file) {
                if (file == null) throw new NullPointerException();
                mFile = file;
                return this;
            }

            public synchronized ImageSaverBuilder setResult(final CaptureResult result) {
                if (result == null) throw new NullPointerException();
                mCaptureResult = result;
                return this;
            }

            public synchronized ImageSaverBuilder setCharacteristics(
                final CameraCharacteristics characteristics) {
                if (characteristics == null) throw new NullPointerException();
                mCharacteristics = characteristics;
                return this;
            }

            public synchronized ImageSaver buildIfComplete() {
                if (!isComplete()) {
                    return null;
                }
                return new ImageSaver(mImage, mFile, mCaptureResult, mCharacteristics, mContext,
                    mReader);
            }

            public synchronized String getSaveLocation() {
                return (mFile == null) ? "Unknown" : mFile.toString();
            }

            private boolean isComplete() {
                return mImage != null && mFile != null && mCaptureResult != null
                    && mCharacteristics != null;
            }
        }
    }

    private final ImageReader.OnImageAvailableListener mOnJpegImageAvailableListener
        = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.i(TAG, "onImageAvailable");
            if (!mJpegResultQueue.isEmpty()) {
                dequeueAndSaveImage(mJpegResultQueue, mJpegImageReader);
            }
        }
    };

    private final CameraCaptureSession.CaptureCallback mCaptureCallback
        = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
            long timestamp, long frameNumber) {
            synchronized (mCameraStateLock) {
                Log.i(TAG, "onCaptureStarted");
                final String filePath =
                    Environment.getExternalStorageDirectory()
                        + File.separator
                        + "camera2JW/"
                        + "take_photo_"
                        + SystemClock.elapsedRealtime()
                        + ".jpeg";
                File jpegFile = new File(filePath);
                if (!jpegFile.getParentFile().exists()) {
                    jpegFile.getParentFile().mkdir();
                }
                // Look up the ImageSaverBuilder for this request and update it with the file name
                // based on the capture start time.
                ImageSaver.ImageSaverBuilder jpegBuilder;
                int requestId = (int) request.getTag();
                jpegBuilder = mJpegResultQueue.get(requestId);
                if (jpegBuilder != null) jpegBuilder.setFile(jpegFile);
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            Log.i(TAG, "onCaptureProgressed");
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
            Log.i(TAG, "onCaptureBufferLost");
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session,
            int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
            Log.i(TAG, "onCaptureSequenceAborted");
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session,
            int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
            TotalCaptureResult result) {
            synchronized (mCameraStateLock) {
                int requestId = (int) request.getTag();
                ImageSaver.ImageSaverBuilder jpegBuilder;
                StringBuilder sb = new StringBuilder();
                // Look up the ImageSaverBuilder for this request and update it with the CaptureResult
                jpegBuilder = mJpegResultQueue.get(requestId);
                if (jpegBuilder != null) {
                    jpegBuilder.setResult(result);
                    sb.append("Saving JPEG as: ");
                    sb.append(jpegBuilder.getSaveLocation());
                }
                // If we have all the results necessary, save the image to a file in the background.
                Log.i(TAG, "onCaptureCompleted");
                handleCompletionLocked(requestId, jpegBuilder, mJpegResultQueue);
                finishedCaptureLocked();
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
            CaptureFailure failure) {
            int requestId = (int) request.getTag();
            Log.i(TAG, "onCaptureFailed");
            synchronized (mCameraStateLock) {
                mJpegResultQueue.remove(requestId);
                finishedCaptureLocked();
            }
        }
    };

    private void finishedCaptureLocked() {
        //try {
        // Reset the auto-focus trigger in case AF didn't run quickly enough.
        if (!mNoAFRun) {
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //mPreviewSession.capture(mPreviewBuilder.build(), mPreCaptureCallback,
            //    mBackgroundHandler);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        }
    }

    public Size[] getPictureSizes() throws CameraAccessException {
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
        return map.getOutputSizes(SurfaceTexture.class);
    }

    public void setPictureSize(final int width, final int height) {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                closePreviewSession();
                closeCamera();
                mPresetPictureWidth = width;
                mPresetPictureHeight = height;
                openCamera();
            }
        });
    }
}
