package com.eyedog.camera2.recorder;

import android.content.Context;
import android.util.AttributeSet;
import com.eyedog.camera2.dev.CameraSurfaceView;

/**
 * created by jw200 at 2018/11/27 22:10
 **/
public class RecorderSurfaceView extends CameraSurfaceView {

    public RecorderSurfaceView(Context context) {
        this(context, null);
    }

    public RecorderSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecorderSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RecorderSurfaceView(Context context, AttributeSet attrs, int defStyleAttr,
        int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
