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

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;

import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.opengl.GLES20;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.rajawali3d.Object3D;
import org.rajawali3d.animation.Animation;
import org.rajawali3d.animation.Animation3D;
import org.rajawali3d.animation.EllipticalOrbitAnimation3D;
import org.rajawali3d.animation.RotateAroundAnimation3D;
import org.rajawali3d.animation.RotateOnAxisAnimation;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.Loader3DSMax;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector2;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.RajawaliRenderer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11Ext;
import javax.microedition.khronos.opengles.GL11ExtensionPack;

import com.google.atap.tangoservice.TangoXyzIjData;
import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.renderables.PointCloud;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * Renderer that implements a basic augmented reality scene using Rajawali.
 * It creates a scene with a background quad taking the whole screen, where the color camera is
 * rendered, and a sphere with the texture of the earth floating ahead of the start position of
 * the Tango device.
 */
public class AugmentedRealityRenderer extends RajawaliRenderer {
    private static final String TAG = AugmentedRealityRenderer.class.getSimpleName();
    private int savecnt = 0;
    public static Object lock = new Object();

    static class WorldPointCloudSet
    {
        Matrix4 viewTworld;
    }

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;
    private static final int MAX_NUMBER_OF_POINTS = 60000;

    // Rajawali texture used to render the Tango color camera.
    private ATexture mTangoCameraTexture;

    // Keeps track of whether the scene camera has been configured.
    private boolean mSceneCameraConfigured;

    private boolean screenshot;
    private static int framebufferWidth = 1920;
    private static int framebufferHeight = 942;

    private UIUtil.Rect selectRect;
    private PointCloud mPointCloud;
    private TangoXyzIjData mXYZij;
    private TangoPoseData mPose;
    private TangoCameraIntrinsics mIntrinsics;
    private Vector3[][] mPositionBuffer = new Vector3[ framebufferHeight ][ framebufferWidth ];

    public AugmentedRealityRenderer(Context context) {
        super(context);
    }

    public void setScreenShot()
    {
        screenshot = true;
    }

    private Vector2 viewPointToScreen(TangoCameraIntrinsics intrinsics, float x, float y, float z)
    {
        float fx = (float) intrinsics.fx;
        float fy = (float) intrinsics.fy;
        float cx = (float) intrinsics.cx;
        float cy = (float) intrinsics.cy;

        int pixelX = (int)(fx * (x / z) + cx);
        int pixelY = (int)(fy * (x / z) + cy);

        return new Vector2(pixelX, pixelY);
    }

    private Vector3d pointWorldToView(Vector3d worldPos, Matrix4 worldToView)
    {
        Matrix4d mat4 = new Matrix4d();
        mat4.set(worldToView.getDoubleValues());
        Matrix3d worldToView3d = new Matrix3d(mat4);
        Vector3d viewPos = worldToView3d.transform(worldPos);

        return viewPos;
    }

    private boolean isInRegion(float px, float py, int x1, int y1, int x2, int y2)
    {
        return (px >= x1 && px <= x2 && py >= y1 && py <= y2);
    }

    public void togglePointcloud()
    {
        mPointCloud.setVisible(!mPointCloud.isVisible());
    }

    private void savePointPositionToBuffer(int px, int py, Vector3 pos)
    {
        if(px < 0 || px >= framebufferWidth || py < 0 || py >= framebufferHeight)
            return;
        mPositionBuffer[py][px] = pos;
    }

    private void clearPositionBuffer()
    {
        final Vector3 defaultPos = new Vector3(0, 0, 0);
        for(Vector3[] row : mPositionBuffer)
        {
            Arrays.fill(row, defaultPos);
        }
    }

    private void savePointCloud(PointCloud pointCloud, TangoXyzIjData xyzIj, TangoPoseData pose)
    {
        Log.d("PointCloud", xyzIj.xyzCount + "");
        Log.d("Intrinsic", mIntrinsics.width + ", " + mIntrinsics.height);

        FloatBuffer newXyz = FloatBuffer.allocate(xyzIj.xyzCount * 3);
        PointCloud newPointCloud = new PointCloud(MAX_NUMBER_OF_POINTS);

        clearPositionBuffer();
        for (int k = 0; k < xyzIj.xyzCount * 3; k += 3) {
            float x = (float) xyzIj.xyz.get(k);
            float y = (float) xyzIj.xyz.get(k + 1);
            float z = (float) xyzIj.xyz.get(k + 2);

            Vector3 pos = new Vector3(x, y, z);
            Vector2 screenPos = viewPointToScreen(mIntrinsics, x, y, z);
            int pixelX = (int) screenPos.getX();
            int pixelY = (int) screenPos.getY();
            savePointPositionToBuffer(pixelX, pixelY, pos);

            newXyz.put(x);
            newXyz.put(y);
            newXyz.put(z);
        }

        newPointCloud.updateCloud(xyzIj.xyzCount, newXyz);
        newPointCloud.setPosition(pointCloud.getPosition());
        newPointCloud.setOrientation(pointCloud.getOrientation());

        getCurrentScene().addChild(newPointCloud);

        Log.d("PointCloud", "OK");
    }

    @Override
    protected void initScene() {
        // Create a quad covering the whole background and assign a texture to it where the
        // Tango color camera contents will be rendered.
        ScreenQuad backgroundQuad = new ScreenQuad();
        Material tangoCameraMaterial = new Material();
        tangoCameraMaterial.setColorInfluence(0);
        // We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
        // for GL_TEXTURE_EXTERNAL_OES rendering
        mTangoCameraTexture =
                new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
        try {
            tangoCameraMaterial.addTexture(mTangoCameraTexture);
            backgroundQuad.setMaterial(tangoCameraMaterial);

        } catch (ATexture.TextureException e) {
            Log.e(TAG, "Exception creating texture for RGB camera contents", e);
        }
        getCurrentScene().addChildAt(backgroundQuad, 0);

        mPointCloud = new PointCloud(MAX_NUMBER_OF_POINTS);
        getCurrentScene().addChild(mPointCloud);
        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);
        getCurrentCamera().setFieldOfView(37.5);
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The camera pose should match the pose of the camera color at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     * <p/>
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        // Conjugating the Quaternion is need because Rajawali uses left handed convention for
        // quaternions.
        getCurrentCamera().setRotation(quaternion.conjugate());
        getCurrentCamera().setPosition(translation[0], translation[1], translation[2]);
    }

    /**
     * Updates the rendered point cloud. For this, we need the point cloud data and the device pose
     * at the time the cloud data was acquired.
     * NOTE: This needs to be called from the OpenGL rendering thread.
     */
    public void updatePointCloud(TangoXyzIjData xyzIjData, TangoPoseData devicePose,
                                 DeviceExtrinsics extrinsics, TangoCameraIntrinsics intrinsics) {
        Pose pointCloudPose =
                ScenePoseCalculator.toDepthCameraOpenGlPose(devicePose, extrinsics);
        mPointCloud.updateCloud(xyzIjData.xyzCount, xyzIjData.xyz);
        mPointCloud.setPosition(pointCloudPose.getPosition());
        mPointCloud.setOrientation(pointCloudPose.getOrientation());
        mIntrinsics = intrinsics;

        mXYZij = xyzIjData;
        mPose = devicePose;

    }

    @Override
    public void onRenderFrame(GL10 gl) {
        synchronized (lock)
        {
            if(screenshot){
                savePointCloud(mPointCloud, mXYZij, mPose);
                screenshot = false;
                Log.d("Keyframe", "Done");
            }
        }
        super.onRenderFrame(gl);
    }

    /**
     * It returns the ID currently assigned to the texture where the Tango color camera contents
     * should be rendered.
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public int getTextureId() {
        return mTangoCameraTexture == null ? -1 : mTangoCameraTexture.getTextureId();
    }

    /**
     * We need to override this method to mark the camera for re-configuration (set proper
     * projection matrix) since it will be reset by Rajawali on surface changes.
     */
    @Override
    public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
        super.onRenderSurfaceSizeChanged(gl, width, height);
    }

    public boolean isSceneCameraConfigured() {
        return mSceneCameraConfigured;
    }

    /**
     * Sets the projection matrix for the scen camera to match the parameters of the color camera,
     * provided by the {@code TangoCameraIntrinsics}.
     */
    public void setProjectionMatrix(TangoCameraIntrinsics intrinsics) {
        Matrix4 projectionMatrix = ScenePoseCalculator.calculateProjectionMatrix(
                intrinsics.width, intrinsics.height,
                intrinsics.fx, intrinsics.fy, intrinsics.cx, intrinsics.cy);
        getCurrentCamera().setProjectionMatrix(projectionMatrix);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    @Override
    public void onRenderSurfaceCreated(EGLConfig config, GL10 gl, int width, int height) {
        super.onRenderSurfaceCreated(config, gl, width, height);
    }
}
