/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.examples.java.augmentedreality;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoTextureCameraPreview;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.surface.RajawaliSurfaceView;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.atap.tangoservice.experimental.TangoMesh;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;
import com.projecttango.tangoutils.TangoPoseUtilities;

/**
 * This is a simple example that shows how to use the Tango APIs to create an augmented reality (AR)
 * application. It displays the Planet Earth floating in space one meter in front of the device, and
 * the Moon rotating around it.
 * <p/>
 * This example uses Rajawali for the OpenGL rendering. This includes the color camera image in the
 * background and a 3D sphere with a texture of the Earth floating in space three meter forward.
 * This part is implemented in the {@code AugmentedRealityRenderer} class, like a regular Rajawali
 * application.
 * <p/>
 * This example focuses on how to use the Tango APIs to get the color camera data into an OpenGL
 * texture efficiently and have the OpenGL camera track the movement of the device in order to
 * achieve an augmented reality effect.
 * <p/>
 * Note that it is important to include the KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION configuration
 * parameter in order to achieve best results synchronizing the Rajawali virtual world with the
 * RGB camera.
 * <p/>
 * If you're looking for a more stripped down example that doesn't use a rendering library like
 * Rajawali, see java_hello_video_example.
 */
public class AugmentedRealityActivity extends Activity {
    private static final String TAG = AugmentedRealityActivity.class.getSimpleName();
    private static final int INVALID_TEXTURE_ID = 0;


    // Configure the Tango coordinate frame pair
    private static final ArrayList<TangoCoordinateFramePair> FRAME_PAIRS =
            new ArrayList<TangoCoordinateFramePair>();

    {
        FRAME_PAIRS.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
    }

    private RajawaliSurfaceView mSurfaceView;
    private AugmentedRealityRenderer mRenderer;
    private TangoCameraIntrinsics mIntrinsics;
    private DeviceExtrinsics mExtrinsics;
    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsConnected = false;
    private double mCameraPoseTimestamp = 0;

    // Texture rendering related fields.
    // NOTE: Naming indicates which thread is in charge of updating this variable.
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mRgbTimestampGlThread;

    private TangoPointCloudManager mPointCloudManager;

    private ArrayList<Vector2> mDragBuffer = new ArrayList<Vector2>();

    public void putDragPos(int x, int y)
    {
        if(!mDragBuffer.isEmpty())
        {
            Vector2 pre = mDragBuffer.get(mDragBuffer.size() - 1);
            if((int)pre.getX() == x && (int)pre.getY() == y)
                return;
        }
        mDragBuffer.add(new Vector2(x, y));
    }

    public void saveDragBuffer()
    {
        int minX = 0x7fffffff, minY = 0x7fffffff;
        int maxX = 0, maxY = 0;
        for(Vector2 p : mDragBuffer)
        {
            minX = (int)Math.min(minX, p.getX());
            minY = (int)Math.min(minY, p.getY());
            maxX = (int)Math.max(maxX, p.getX());
            maxY = (int)Math.max(maxY, p.getY());
        }
        Log.d("Bound", minX + ", " + minY + " : " + maxX + ", " + maxY);
        mRenderer.setBound(new Vector2(minX, minY), new Vector2(maxX, maxY));
        mDragBuffer.clear();
    }

    private static class MyDragShadowBuilder extends View.DragShadowBuilder{
        // The drag shadow image, defined as a drawable thing
        private static Drawable shadow;

        // Defines the constructor for MyDragShadowBuilder
        public MyDragShadowBuilder(View v){
            super(v);

            // Get the background color of dragged object
            int color = Color.CYAN;

            // Creates a draggable image that will fill the Canvas provided by the system
            shadow = new ColorDrawable((color));
        }

        // Defines a callback that sends the drag shadow dimensions and touch point back to the system
        @Override
        public void onProvideShadowMetrics(Point size, Point touch){
            // Define local variables
            int width, height;

            // Sets the width of the shadow to half the width of the original view
            width = getView().getWidth()/2;

            // Sets the height of the shadow to half the height of the original view
            height = getView().getHeight()/2;

            // The drag shadow will fill the Canvas
            shadow.setBounds(0,0,50,50);

            // Sets the size parameter's width and height values
            size.set(width, height);

            // Sets the touch point position to be in the middle of the drag shadow
            touch.set(25,25);
        }

        @Override
        public void onDrawShadow(Canvas canvas){
            // Draws the ColorDrawable in the Canvas passed in from the system
            shadow.draw(canvas);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (RajawaliSurfaceView) findViewById(R.id.surfaceview);
        mRenderer = new AugmentedRealityRenderer(this);
        setupRenderer();
        mPointCloudManager = new TangoPointCloudManager();

        Button doButton = (Button) findViewById(R.id.button2);
        doButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRenderer.setScreenShot();
            }
        });

        Button toggleButton = (Button) findViewById(R.id.savePointCloudButton);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRenderer.togglePointcloud();
            }
        });

        Button mergeButton = (Button) findViewById(R.id.button3);
        mergeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRenderer.setMerge();

            }
        });

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int x = (int) motionEvent.getX();
                int y = (int) motionEvent.getY();
                Log.d("Click", x + ", " + y);
                ClipData.Item item = new ClipData.Item("Dragger");
                ClipData dragData = new ClipData(
                        "Dragger",
                        new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN},item
                );
                View.DragShadowBuilder myShadow = new MyDragShadowBuilder(view);
                mSurfaceView.startDrag(dragData, myShadow, null, 0);
                return false;
            }
        });

        mSurfaceView.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View view, DragEvent event) {
                int action = event.getAction();
                int x = (int) event.getX();
                int y = (int) event.getY();
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        Log.d("DRAG_STARTED", x + ", " + y);
                        break;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        Log.d("DRAG_ENTERED", x + ", " + y);
                        putDragPos(x, y);
                        break;
                    case DragEvent.ACTION_DRAG_EXITED:
                        Log.d("DRAG_EXIT", x + ", " + y);
                        break;
                    case DragEvent.ACTION_DROP:
                        Log.d("DRAG_DROP", x + ", " + y);
                        putDragPos(x, y);

                        break;
                    case DragEvent.ACTION_DRAG_ENDED:
                        Log.d("DRAG_END", x + ", " + y);
                        saveDragBuffer();
                        break;
                    case DragEvent.ACTION_DRAG_LOCATION:
                        //Log.d("DRAG_LOC", x + ", " + y);

                        break;
                    default:
                        Log.d("UNKON", "");
                        break;
                }
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSurfaceView.onResume();
        // Set render mode to RENDERMODE_CONTINUOUSLY to force getting onDraw callbacks until the
        // Tango service is properly set-up and we start getting onFrameAvailable callbacks.
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Initialize Tango Service as a normal Android Service, since we call
        // mTango.disconnect() in onPause, this will unbind Tango Service, so
        // everytime when onResume get called, we should create a new Tango object.
        mTango = new Tango(AugmentedRealityActivity.this, new Runnable() {
            // Pass in a Runnable to be called from UI thread when Tango is ready,
            // this Runnable will be running on a new thread.
            // When Tango is ready, we can call Tango functions safely here only
            // when there is no UI thread changes involved.
            @Override
            public void run() {
                // Synchronize against disconnecting while the service is being used in the OpenGL
                // thread or in the UI thread.
                synchronized (AugmentedRealityActivity.this) {
                    TangoSupport.initialize();
                    mConfig = setupTangoConfig(mTango);

                    try {
                        setTangoListeners();
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (SecurityException e) {
                        Log.e(TAG, getString(R.string.permission_camera), e);
                    }
                    try {
                        mTango.connect(mConfig);
                        mIsConnected = true;
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_out_of_date), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (AugmentedRealityActivity.this) {
                            mIntrinsics = mTango.getCameraIntrinsics(
                                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                            Log.d("Intrinsics", mIntrinsics.width + ", " + mIntrinsics.height);

                        }
                    }
                });
            }
        });
        mTango.getCameraIntrinsics(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSurfaceView.onPause();
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
        // will block here until all Tango callback calls are finished. If you lock against this
        // object in a Tango callback thread it will cause a deadlock.
        synchronized (this) {
            try {
                mIsConnected = false;
                mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                // We need to invalidate the connected texture ID so that we cause a re-connection
                // in the OpenGL thread after resume.
                mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, getString(R.string.exception_tango_error), e);
            }
        }
    }

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use default configuration for Tango Service, plus color camera and
        // low latency IMU integration.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        // NOTE: Low latency integration is necessary to achieve a precise alignment of
        // virtual objects with the RBG image and produce a good AR effect.
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);
        return config;
    }

    /**
     * Set up the callback listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void setTangoListeners() {
        // No need to add any coordinate frame pairs since we aren't using pose data from callbacks.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        mTango.connectListener(framePairs, new OnTangoUpdateListener() {
            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // We are not using onPoseAvailable for this app.
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
                synchronized (AugmentedRealityRenderer.lock) {
                    mPointCloudManager.updateXyzIj(xyzIj);
                }
                //Log.d("XYZIJ", "Update");
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                // We are not using onTangoEvent for this app.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // Check if the frame available is for the camera we want and update its frame
                // on the view.
                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Now that we are receiving onFrameAvailable callbacks, we can switch
                    // to RENDERMODE_WHEN_DIRTY to drive the render loop from this callback.
                    // This will result on a frame rate of  approximately 30FPS, in synchrony with
                    // the RGB camera driver.
                    // If you need to render at a higher rate (i.e.: if you want to render complex
                    // animations smoothly) you  can use RENDERMODE_CONTINUOUSLY throughout the
                    // application lifecycle.
                    if (mSurfaceView.getRenderMode() != GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
                        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
                    }


                    // Mark a camera frame is available for rendering in the OpenGL thread.
                    mIsFrameAvailableTangoThread.set(true);
                    // Trigger an Rajawali render to update the scene with the new RGB data.
                    mSurfaceView.requestRender();
                }
            }
        });
        mExtrinsics = setupExtrinsics();
    }

    private DeviceExtrinsics setupExtrinsics() {
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        TangoPoseData imuTColorCameraPose = mTango.getPoseAtTime(0.0, framePair);

        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH;
        TangoPoseData imuTDepthCameraPose = mTango.getPoseAtTime(0.0, framePair);

        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        TangoPoseData imuTDevicePose = mTango.getPoseAtTime(0.0, framePair);

        return new DeviceExtrinsics(imuTDevicePose, imuTColorCameraPose, imuTDepthCameraPose);
    }


    /**
     * Connects the view and renderer to the color camara and callbacks.
     */
    private void setupRenderer() {
        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {
            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks had a chance to run and before scene objects are rendered
                // into the scene.

                // Prevent concurrent access to {@code mIsFrameAvailableTangoThread} from the Tango
                // callback thread and service disconnection from an onPause event.
                synchronized (AugmentedRealityActivity.this) {
                    // Don't execute any tango API actions if we're not connected to the service.
                    if (!mIsConnected) {
                        return;
                    }

                    TangoXyzIjData pointCloud = mPointCloudManager.getLatestXyzIj();
                    if (pointCloud != null) {
                        TangoPoseData pointCloudPose =
                                mTango.getPoseAtTime(pointCloud.timestamp, FRAME_PAIRS.get(0));
                        mRenderer.updatePointCloud(pointCloud, pointCloudPose, mExtrinsics, mIntrinsics);
                    }

                    // Set-up scene camera projection to match RGB camera intrinsics.
                    if (!mRenderer.isSceneCameraConfigured()) {
                        mRenderer.setProjectionMatrix(mIntrinsics);
                    }

                    // Connect the camera texture to the OpenGL Texture if necessary
                    // NOTE: When the OpenGL context is recycled, Rajawali may re-generate the
                    // texture with a different ID.
                    if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                        mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                mRenderer.getTextureId());
                        mConnectedTextureIdGlThread = mRenderer.getTextureId();
                        Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                    }

                    // If there is a new RGB camera frame available, update the texture with it
                    if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                        mRgbTimestampGlThread =
                                mTango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                    }

                    // If a new RGB frame has been rendered, update the camera pose to match.
                    if (mRgbTimestampGlThread > mCameraPoseTimestamp) {
                        // Calculate the camera color pose at the camera frame update time in
                        // OpenGL engine.
                        TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(
                                mRgbTimestampGlThread,
                                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                Surface.ROTATION_0);
                        if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                            // Update the camera pose from the renderer
                            mRenderer.updateRenderCameraPose(lastFramePose);
                            mCameraPoseTimestamp = lastFramePose.timestamp;
                        } else {
                            Log.w(TAG, "Can't get device pose at time: " +
                                    mRgbTimestampGlThread);
                        }
                    }
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }

            @Override
            public boolean callPreFrame() {
                return true;
            }
        });

        mSurfaceView.setSurfaceRenderer(mRenderer);
    }
}
