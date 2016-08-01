package com.projecttango.examples.java.augmentedreality;
/*
 * Copyright (c) 2012-2013 OrangeSignal.com All Rights Reserved.
 */


import android.annotation.TargetApi;
import android.opengl.GLES20;
import android.os.Build;

/**
 * オフスクリーン描画用の OpenGL ES 2.0 のフレームバッファオブジェクト管理クラスを提供します。
 *
 * @author 杉澤 浩二
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class GLES20FramebufferObject {

    /**
     * 幅を保持します。
     */
    private int mWidth;

    /**
     * 高さを保持します。
     */
    private int mHeight;

    /**
     * フレームバッファ識別子を保持します。
     */
    private int mFramebufferName;

    /**
     * レンダーバッファ識別子を保持します。
     */
    private int mRenderbufferName;

    /**
     * テクスチャ識別子を保持します。
     */
    private int mTexName;

    //////////////////////////////////////////////////////////////////////////
    // セッター / ゲッター

    /**
     * 幅を返します。
     *
     * @return 幅
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * 高さを返します。
     *
     * @return 高さ
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * テクスチャ識別子を返します。
     *
     * @return テクスチャ識別子
     */
    public int getTexName() {
        return mTexName;
    }

    //////////////////////////////////////////////////////////////////////////
    // パブリック メソッド

    /**
     * 指定された幅と高さでフレームバッファオブジェクト (FBO) を構成します。<p>
     * 既にフレームバッファオブジェクト (FBO) が構成されている場合は、
     * 現在のフレームバッファオブジェクト (FBO) を削除して新しいフレームバッファオブジェクト (FBO) を構成します。
     *
     * @param width 幅
     * @param height 高さ
     * @throws RuntimeException フレームバッファの構成に失敗した場合。
     */
    public void setup(final int width, final int height) {
        // 現在のフレームバッファオブジェクトを削除します。
        release();

        try {
            mWidth = width;
            mHeight = height;

            final int[] args = new int[1];

            // フレームバッファ識別子を生成します。
            GLES20.glGenFramebuffers(args.length, args, 0);
            mFramebufferName = args[0];
            // フレームバッファ識別子に対応したフレームバッファオブジェクトを生成します。
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebufferName);

            // レンダーバッファ識別子を生成します。
            GLES20.glGenRenderbuffers(args.length, args, 0);
            mRenderbufferName = args[0];
            // レンダーバッファ識別子に対応したレンダーバッファオブジェクトを生成します。
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mRenderbufferName);
            // レンダーバッファの幅と高さを指定します。
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height);
            // フレームバッファのアタッチメントとしてレンダーバッファをアタッチします。
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, mRenderbufferName);

            // Offscreen position framebuffer texture target
            GLES20.glGenTextures(args.length, args, 0);
            mTexName = args[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexName);
            GLES20Utils.setupSampler(GLES20.GL_TEXTURE_2D, GLES20.GL_LINEAR, GLES20.GL_NEAREST);

            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            // フレームバッファのアタッチメントとして 2D テクスチャをアタッチします。
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTexName, 0);

            // フレームバッファが完全かどうかチェックします。
            final int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Failed to initialize framebuffer object " + status);
            }
        } catch (final RuntimeException e) {
            release();
            throw e;
        }
    }

    /**
     * クリーンアップを行います。
     */
    public void release() {
        GLES20.glDeleteTextures(1, new int[]{ mTexName }, 0);
        GLES20.glDeleteRenderbuffers(1, new int[]{ mRenderbufferName }, 0);
        GLES20.glDeleteFramebuffers(1, new int[]{ mFramebufferName }, 0);
        mTexName = 0;
        mRenderbufferName = 0;
        mFramebufferName = 0;
    }

    /**
     * このフレームバッファオブジェクトをバインドして有効にします。
     */
    public void enable() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebufferName);
    }

}