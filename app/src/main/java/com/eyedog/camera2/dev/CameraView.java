package com.eyedog.camera2.dev;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.view.TextureView;
import com.eyedog.camera2.Constant;
import java.io.IOException;
import java.util.List;

/**
 * created by jw200 at 2018/11/16 15:43
 **/
public class CameraView extends AutoFitTextureView {
    private static final String[] VIDEO_PERMISSIONS = {
        Manifest.permission.CAMERA
    };
    private Activity mActivity;
    private int mFacing = 0;
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private Camera mCameraDevice;
    private Camera.Parameters mParams;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mPictureWidth = 1000;
    private int mPictureHeight = 1000;
    private int mPreferPreviewWidth = Constant.OUT_WIDTH;
    private int mPreferPreviewHeight = Constant.OUT_HEIGHT;
    private boolean mIsPreviewing = false;
    private int mCameraId = 0;
    private final String TAG = "CameraView";

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
    }

    public void onResume() {
        if (isAvailable()) {
            openCamera(mCameraId);
        } else {
            setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    public void toggle() {
        closeCamera();
        openCamera(
            mFacing == Camera.CameraInfo.CAMERA_FACING_BACK ? Camera.CameraInfo.CAMERA_FACING_FRONT
                : Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    public void onPause() {
        closeCamera();
    }

    protected void closeCamera() {
        if (mIsPreviewing) {
            mIsPreviewing = false;
            if (mCameraDevice != null) {
                stopPreview();
                mCameraDevice.setPreviewCallback(null);
                mCameraDevice.release();
                mCameraDevice = null;
            }
        }
    }

    protected TextureView.SurfaceTextureListener mSurfaceTextureListener
        = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
            int width, int height) {
            openCamera(mCameraId);
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

    protected void openCamera(int facing) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
                int numberOfCameras = Camera.getNumberOfCameras();

                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == facing) {
                        mCameraId = i;
                        mFacing = facing;
                    }
                }
            }
            stopPreview();
            if (mCameraDevice != null) {
                mCameraDevice.release();
            }
            if (mCameraId >= 0) {
                mCameraDevice = Camera.open(mCameraId);
            } else {
                mCameraDevice = Camera.open();
                mFacing = Camera.CameraInfo.CAMERA_FACING_BACK; //default: back facing
            }
            android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(mCameraId, info);
            int rotation = info.orientation;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (360 - info.orientation) % 360;
            } else {
                rotation = info.orientation % 360;
            }
            mCameraDevice.setDisplayOrientation(rotation);
        } catch (Exception e) {
            e.printStackTrace();
            mCameraDevice = null;
            return;
        }
        if (mCameraDevice != null) {
            try {
                initCamera();
            } catch (Exception e) {
                mCameraDevice.release();
                mCameraDevice = null;
                return;
            }
            return;
        }
        return;
    }

    public Camera.Size getPictureSize(List<Camera.Size> list, int picwidth, int picheight) {
        if (list == null || list.size() == 0) {
            return null;
        }
        if (picwidth > picheight) {
            int temp = picwidth;
            picwidth = picheight;
            picheight = temp;
        }
        float ratio = 1.0f * picheight / picwidth;
        Camera.Size result = null;
        for (Camera.Size size : list) {
            int width = size.width;
            int height = size.height;
            if (width > height) {
                int temp = width;
                width = height;
                height = temp;
            }
            if ((1.0f * height / width) == ratio) {
                if (result != null) {
                    int result_w = result.width;
                    int result_h = result.height;
                    if (result_w > result_h) {
                        int temp = result_w;
                        result_w = result_h;
                        result_h = temp;
                    }
                    if (result_w < width) {
                        result = size;
                    }
                } else {
                    result = size;
                }
            }
        }
        if (result == null) {
            result = list.get(0);
        }
        return result;
    }

    public Camera.Size getLargeSize(List<Camera.Size> list, int width, int height,
        boolean isPreview) {
        if (list == null || list.size() == 0) {
            return null;
        }
        if (width > height) {
            int tempwidth = width;
            width = height;
            height = tempwidth;
        }
        // 存放宽高与屏幕宽高相同的size
        Camera.Size size = null;
        // 存放比率相同的最大size
        Camera.Size largeSameRatioSize = null;
        // 存放比率差距0.1的最大size
        Camera.Size largeRatioSize = null;
        float scrwhRatio = 1.0f * width / height;
        for (Camera.Size preSize : list) {
            float tempRatio = 1.0f * preSize.width / preSize.height;
            if (preSize.width < preSize.height) {
                if (preSize.width == width && preSize.height == height) {
                    size = preSize;
                    break;
                }
            } else if (preSize.width > preSize.height) {
                tempRatio = 1.0f * preSize.height / preSize.width;
                if (preSize.height == width && preSize.width == height) {
                    size = preSize;
                    break;
                }
            }

            if (tempRatio == scrwhRatio) {
                if (largeSameRatioSize == null) {
                    largeSameRatioSize = preSize;
                } else {
                    int largeSameRatioWidth = largeSameRatioSize.width;
                    if (largeSameRatioSize.width > largeSameRatioSize.height) {
                        largeSameRatioWidth = largeSameRatioSize.height;
                    }
                    int preSizeWidth = preSize.width;
                    if (preSize.width > preSize.height) {
                        preSizeWidth = preSize.height;
                    }
                    if (Math.abs(largeSameRatioWidth - width) > Math
                        .abs(preSizeWidth - width)) {
                        if (preSizeWidth < width && preSizeWidth > 540) {
                            largeSameRatioSize = preSize;
                        } else if (preSizeWidth >= width) {
                            if (isPreview) {
                                if (preSizeWidth < width * 2) {
                                    largeSameRatioSize = preSize;
                                }
                            } else {
                                largeSameRatioSize = preSize;
                            }
                        }
                    } else if (Math.abs(largeSameRatioWidth - width) == Math
                        .abs(preSizeWidth - width)) {
                        largeSameRatioSize =
                            largeSameRatioWidth > preSizeWidth ? largeSameRatioSize : preSize;
                    }
                }
            }

            float ratioDistance = Math.abs(tempRatio - scrwhRatio);
            if (ratioDistance < 0.1) {
                if (largeRatioSize == null) {
                    largeRatioSize = preSize;
                } else {
                    int largeRatioWidth = largeRatioSize.width;
                    if (largeRatioSize.width > largeRatioSize.height) {
                        largeRatioWidth = largeRatioSize.height;
                    }
                    int preSizeWidth = preSize.width;
                    if (preSize.width > preSize.height) {
                        preSizeWidth = preSize.height;
                    }
                    if (Math.abs(largeRatioWidth - width) > Math.abs(preSizeWidth - width)) {
                        largeRatioSize = preSize;
                    }
                }
            }
        }

        if (size != null) {
            return size;
        } else if (largeSameRatioSize != null) {
            int largeSameRatioWidth = largeSameRatioSize.width;
            if (largeSameRatioSize.width > largeSameRatioSize.height) {
                largeSameRatioWidth = largeSameRatioSize.height;
            }
            if (Math.abs(largeSameRatioWidth - width) <= (width * 1.0f / 3.0f)) {
                return largeSameRatioSize;
            } else if (largeRatioSize != null) {
                if (!isPreview) {
                    return largeRatioSize;
                }
                int largeRatioWidth = largeRatioSize.width;
                if (largeRatioSize.width > largeRatioSize.height) {
                    largeRatioWidth = largeRatioSize.height;
                }
                if (Math.abs(largeRatioWidth - width) < (width * 1.0f / 3.0f)) {
                    return largeRatioSize;
                } else {
                    return list.get(0);
                }
            } else {
                return list.get(0);
            }
        } else if (largeRatioSize != null) {
            int largeRatioWidth = largeRatioSize.width;
            if (largeRatioSize.width > largeRatioSize.height) {
                largeRatioWidth = largeRatioSize.height;
            }
            if (Math.abs(largeRatioWidth - width) <= (width * 1.0f / 3.0f)) {
                return largeRatioSize;
            } else {
                return list.get(0);
            }
        } else {
            return getPictureSize(list, width, height);
        }
    }

    public void initCamera() {
        if (mCameraDevice == null) {
            return;
        }
        mParams = mCameraDevice.getParameters();
        mParams.setPictureFormat(PixelFormat.JPEG);
        List<Camera.Size> picSizes = mParams.getSupportedPictureSizes();
        Camera.Size picSz = getLargeSize(picSizes, mPictureWidth, mPictureHeight, false);
        List<Camera.Size> prevSizes = mParams.getSupportedPreviewSizes();
        Camera.Size prevSz =
            getLargeSize(prevSizes, mPreferPreviewWidth, mPreferPreviewHeight, true);
        List<Integer> frameRates = mParams.getSupportedPreviewFrameRates();
        int fpsMax = 0;
        for (Integer n : frameRates) {
            if (fpsMax < n) {
                fpsMax = n;
            }
        }
        mParams.setPreviewSize(prevSz.width, prevSz.height);
        mParams.setPictureSize(picSz.width, picSz.height);
        List<String> focusModes = mParams.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        mParams.setPreviewFrameRate(fpsMax); //设置相机预览帧率
        try {
            mCameraDevice.setParameters(mParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mParams = mCameraDevice.getParameters();
        Camera.Size szPic = mParams.getPictureSize();
        Camera.Size szPrev = mParams.getPreviewSize();
        mPreviewWidth = szPrev.width;
        mPreviewHeight = szPrev.height;
        mPictureWidth = szPic.width;
        mPictureHeight = szPic.height;
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setAspectRatio(mPreviewWidth, mPreviewHeight);
        } else {
            setAspectRatio(mPreviewHeight, mPreviewWidth);
        }
        startPreview(getSurfaceTexture());
    }

    public synchronized void startPreview(SurfaceTexture texture) {
        if (mIsPreviewing) {
            return;
        }
        texture.setDefaultBufferSize(mPreviewWidth, mPreviewHeight);
        if (mCameraDevice != null) {
            try {
                mCameraDevice.setPreviewTexture(texture);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mCameraDevice.startPreview();
            mIsPreviewing = true;
        }
    }

    public synchronized void stopPreview() {
        if (mIsPreviewing && mCameraDevice != null) {
            mIsPreviewing = false;
            mCameraDevice.stopPreview();
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
}
