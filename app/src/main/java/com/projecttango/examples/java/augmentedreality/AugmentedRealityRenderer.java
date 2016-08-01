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
import android.opengl.GLES20;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

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
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.RajawaliRenderer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11Ext;
import javax.microedition.khronos.opengles.GL11ExtensionPack;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Renderer that implements a basic augmented reality scene using Rajawali.
 * It creates a scene with a background quad taking the whole screen, where the color camera is
 * rendered, and a sphere with the texture of the earth floating ahead of the start position of
 * the Tango device.
 */
public class AugmentedRealityRenderer extends RajawaliRenderer {
    private static final String TAG = AugmentedRealityRenderer.class.getSimpleName();

    // Rajawali texture used to render the Tango color camera.
    private ATexture mTangoCameraTexture;

    // Keeps track of whether the scene camera has been configured.
    private boolean mSceneCameraConfigured;

    private boolean screenshot;
    private Bitmap lastScreenshot;

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
            FileOutputStream out = new FileOutputStream(path + "/" + "screenshot" + ".png");
            screenshot.compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

//        // Add a directional light in an arbitrary direction.
//        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
//        light.setColor(1, 1, 1);
//        light.setPower(0.8f);
//        light.setPosition(3, 2, 4);
//        getCurrentScene().addLight(light);
//
//        // Create sphere with earth texture and place it in space 3m forward from the origin.
//        Material earthMaterial = new Material();
//        try {
//            Texture t = new Texture("earth", R.drawable.earth);
//            earthMaterial.addTexture(t);
//        } catch (ATexture.TextureException e) {
//            Log.e(TAG, "Exception generating earth texture", e);
//        }
//        earthMaterial.setColorInfluence(0);
//        earthMaterial.enableLighting(true);
//        earthMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
//        Object3D earth = new Sphere(0.4f, 20, 20);
//        earth.setMaterial(earthMaterial);
//        earth.setPosition(0, 0, -3);
//        getCurrentScene().addChild(earth);
//
//        // Rotate around its Y axis
//        Animation3D animEarth = new RotateOnAxisAnimation(Vector3.Axis.Y, 0, -360);
//        animEarth.setInterpolator(new LinearInterpolator());
//        animEarth.setDurationMilliseconds(60000);
//        animEarth.setRepeatMode(Animation.RepeatMode.INFINITE);
//        animEarth.setTransformable3D(earth);
//        getCurrentScene().registerAnimation(animEarth);
//        animEarth.play();
//
//        // Create sphere with moon texture.
//        Material moonMaterial = new Material();
//        try {
//            Texture t = new Texture("moon", R.drawable.moon);
//            moonMaterial.addTexture(t);
//        } catch (ATexture.TextureException e) {
//            Log.e(TAG, "Exception generating moon texture", e);
//        }
//        moonMaterial.setColorInfluence(0);
//        moonMaterial.enableLighting(true);
//        moonMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
//        Object3D moon = new Sphere(0.1f, 20, 20);
//        moon.setMaterial(moonMaterial);
//        moon.setPosition(0, 0, -1);
//        getCurrentScene().addChild(moon);
//
//        // Rotate the moon around its Y axis
//        Animation3D animMoon = new RotateOnAxisAnimation(Vector3.Axis.Y, 0, -360);
//        animMoon.setInterpolator(new LinearInterpolator());
//        animMoon.setDurationMilliseconds(60000);
//        animMoon.setRepeatMode(Animation.RepeatMode.INFINITE);
//        animMoon.setTransformable3D(moon);
//        getCurrentScene().registerAnimation(animMoon);
//        animMoon.play();
//
//        // Make the moon orbit around the earth, the first two parameters are the focal point and
//        // periapsis of the orbit.
//        Animation3D translationMoon =  new EllipticalOrbitAnimation3D(new Vector3(0, 0, -5),
//                new Vector3(0, 0, -1), Vector3.getAxisVector(Vector3.Axis.Y), 0,
//                360, EllipticalOrbitAnimation3D.OrbitDirection.COUNTERCLOCKWISE);
//        translationMoon.setDurationMilliseconds(60000);
//        translationMoon.setRepeatMode(Animation.RepeatMode.INFINITE);
//        translationMoon.setTransformable3D(moon);
//        getCurrentScene().registerAnimation(translationMoon);
//        translationMoon.play();
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

    @Override
    public void onRenderFrame(GL10 gl) {
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

            int framebufferWidth = 1280;
            int framebufferHeight = 720;
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

            screenshot = false;
            GLES20.glViewport(0, 0, this.getViewportWidth(), this.getViewportHeight());
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
        mFramebufferObject.setup(1280, 720);
        mShader.setFrameSize(width, height);
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
