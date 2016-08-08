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
        ``
    }

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;
    private static final int MAX_NUMBER_OF_POINTS = 60000;

    // Rajawali texture used to render the Tango color camera.
    private ATexture mTangoCameraTexture;

    // Keeps track of whether the scene camera has been configured.
    private boolean mSceneCameraConfigured;

    private boolean screenshot;
    private Bitmap lastScreenshot;
    private static int framebufferWidth = 1920;
    private static int framebufferHeight = 942;
    private static int depthbufferWidth = 1280;
    private static int depthbufferHeight = 720;

    private PointCloud mPointCloud;
    private TangoXyzIjData mPointCloudXYZij;
    private TangoCameraIntrinsics mIntrinsics;
    private float[][] mPointCloudBuffer = new float[depthbufferHeight][depthbufferWidth * 3];

    /**
     * オフスクリーン描画用のフレームバッファオブジェクトを保持します。
     */
    private GLES20FramebufferObject mFramebufferObject;

    /**
     * オンスクリーン描画用の GLSL シェーダーオブジェクトを保持します。
     */
    private GLES20Shader mShader;

    public AugmentedRealityRenderer(Context context) {
        super(context);
    }

    public void setScreenShot()
    {
        screenshot = true;
    }

    private void saveScreenshot(Bitmap screenshot){
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "openglscreenshots");
            file.mkdirs();
            String path = file.toString();
            FileOutputStream out = new FileOutputStream(path + "/" + "screenshot" + savecnt + ".png");
            screenshot.compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void savePointCloudBuffer(){
        try {
            File file = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "openglscreenshots");
            file.mkdirs();
            String path = file.toString();
            FileOutputStream out = new FileOutputStream(path + "/" + "pointcloud" + savecnt + ".dat");
            DataOutputStream dout = new DataOutputStream(out);

            ByteBuffer buffer = ByteBuffer.allocate(4 * mIntrinsics.width * mIntrinsics.height * 3);
            FloatBuffer floatBuffer = buffer.asFloatBuffer();
            for(float[] row : mPointCloudBuffer)
            {
                floatBuffer.put(row);
            }
            dout.write(buffer.array());
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void samplePointCloudAround(int pixel_x, int pixel_y, float px, float py, float pz)
    {
        int image_width = mIntrinsics.width;
        int image_height = mIntrinsics.height;
        int kWindowSize = 7;

        // Set the neighbour pixels to same color.
        for (int a = -kWindowSize; a <= kWindowSize; ++a) {
            for (int b = -kWindowSize; b <= kWindowSize; ++b) {
                if (pixel_x >= image_width || pixel_y >= image_height || pixel_x < 0 ||
                        pixel_y < 0) {
                    continue;
                }

                int bx = pixel_x * 3;
                int by = pixel_y;
                if (bx >= 0 && bx < image_width * 3 && by >= 0 && by < image_height) {
                    mPointCloudBuffer[by][bx] = px;
                    mPointCloudBuffer[by][bx + 1] = py;
                    mPointCloudBuffer[by][bx + 2] = pz;
                }
            }
        }
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

    private void savePointCloud(TangoXyzIjData xyzIj)
    {
        Log.d("PointCloud", xyzIj.xyzCount + "");
        Log.d("Intrinsic", mIntrinsics.width + ", " + mIntrinsics.height);

        for(float[] row : mPointCloudBuffer)
            Arrays.fill(row, 0);

        Matrix4 modelView = getPointCloudModelViewMatrix();
        Matrix4d mat4 = new Matrix4d();
        mat4.set(modelView.getDoubleValues());
        Matrix3d viewTworld = new Matrix3d(mat4);

        for (int k = 0; k < xyzIj.xyzCount * 3; k += 3) {
            float X = (float) xyzIj.xyz.get(k);
            float Y = (float) xyzIj.xyz.get(k + 1);
            float Z = (float) xyzIj.xyz.get(k + 2);

            Vector3d pos = new Vector3d(X, Y, Z);
            pos = viewTworld.transform(pos);

            Vector2 screenPos = viewPointToScreen(mIntrinsics, X, Y, Z);
            int pixelX = (int) screenPos.getX();
            int pixelY = (int) screenPos.getY();

            mPointCloudBuffer[pixelY][pixelX * 3] = (float) pos.x;
            mPointCloudBuffer[pixelY][pixelX * 3 + 1] = (float) pos.y;
            mPointCloudBuffer[pixelY][pixelX * 3 + 2] = (float) pos.z;
            //samplePointCloudAround(pixelX, pixelY, (float) pos.x, (float) pos.y, (float) pos.z);
        }
        Log.d("PointCloud", "OK");
    }

    public Matrix4 getPointCloudModelViewMatrix()
    {
        return mPointCloud.getModelViewMatrix();
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
        mPointCloudXYZij = xyzIjData;
        mIntrinsics = intrinsics;
    }

    public void togglePointcloud()
    {
        mPointCloud.setVisible(!mPointCloud.isVisible());
    }

    @Override
    public void onRenderFrame(GL10 gl) {
        synchronized (lock)
        {
            if(screenshot){
                ////////////////////////////////////////////////////////////
                // オフスクリーンレンダリング

                // FBO へ切り替えます。
                mFramebufferObject.enable();
                GLES20.glViewport(0, 0, mFramebufferObject.getWidth(), mFramebufferObject.getHeight());

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                mShader.draw(getTextureId());

                ////////////////////////////////////////////////////////////
                // オンスクリーンレンダリング

                // ウィンドウシステムが提供するフレームバッファへ切り替えます。
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

                int mViewportWidth = framebufferWidth;
                int mViewportHeight = framebufferHeight;

                int screenshotSize = mViewportWidth * mViewportHeight;
                ByteBuffer bb = ByteBuffer.allocateDirect(screenshotSize * 4);
                bb.order(ByteOrder.nativeOrder());

                GLES20.glReadPixels(0, 0, mViewportWidth, mViewportHeight, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, bb);
                int pixelsBuffer[] = new int[screenshotSize];
                bb.asIntBuffer().get(pixelsBuffer);
                bb = null;
                Bitmap bitmap = Bitmap.createBitmap(mViewportWidth, mViewportHeight, Bitmap.Config.RGB_565);
                bitmap.setPixels(pixelsBuffer, screenshotSize-mViewportWidth, -mViewportWidth, 0, 0, mViewportWidth, mViewportHeight);
                pixelsBuffer = null;

                short sBuffer[] = new short[screenshotSize];
                ShortBuffer sb = ShortBuffer.wrap(sBuffer);
                bitmap.copyPixelsToBuffer(sb);

                //Making created bitmap (from OpenGL points) compatible with Android bitmap
                for (int i = 0; i < screenshotSize; ++i) {
                    short v = sBuffer[i];
                    sBuffer[i] = (short) (((v&0x1f) << 11) | (v&0x7e0) | ((v&0xf800) >> 11));
                }
                sb.rewind();
                bitmap.copyPixelsFromBuffer(sb);
                lastScreenshot = bitmap;
                saveScreenshot(lastScreenshot);
                savePointCloud(mPointCloudXYZij);
                savePointCloudBuffer();

                screenshot = false;
                GLES20.glViewport(0, 0, this.getViewportWidth(), this.getViewportHeight());
                Log.d("Screenshot", "Done");
                savecnt++;
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
        mSceneCameraConfigured = false;
        mFramebufferObject.setup(width, height);
        framebufferWidth = width;
        framebufferHeight = height;
        Log.d("Size", width + ", " + height);
        mShader.setFrameSize(width, height);
        // ウィンドウシステムが提供するフレームバッファへ切り替えます。
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
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
        mFramebufferObject = new GLES20FramebufferObject();
        mShader = new GLES20Shader();
        mShader.setup();
    }
}
