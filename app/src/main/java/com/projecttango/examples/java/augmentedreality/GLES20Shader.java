package com.projecttango.examples.java.augmentedreality;

/*
 * Copyright (c) 2012-2013 OrangeSignal.com All Rights Reserved.
 */


import java.util.HashMap;

import android.annotation.TargetApi;
import android.opengl.GLES20;
import android.os.Build;

/**
 * OpenGL ES 2.0 向けのシェーダーオブジェクト管理クラスを提供します。
 *
 * @author 杉澤 浩二
 */
@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class GLES20Shader {

    /**
     * デフォルトのポリゴン描画用のバーテックスシェーダ (頂点シェーダ) のソースコードです。
     */
    protected static final String DEFAULT_VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying highp vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "gl_Position = aPosition;\n" +
                    "vTextureCoord = aTextureCoord.xy;\n" +
                    "}\n";

    /**
     * デフォルトの色描画用のピクセル/フラグメントシェーダのソースコードです。
     */
    protected static final String DEFAULT_FRAGMENT_SHADER =
            "precision mediump float;\n" +	// 演算精度を指定します。
                    "varying highp vec2 vTextureCoord;\n" +
                    "uniform lowp sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    /**
     * 頂点データとテクスチャ座標 (UV マッピング) の構造体配列形式データです。
     */
    private static final float[] VERTICES_DATA = new float[] {
            // X, Y, Z, U, V
            -1f,  1f, 0f, 0f, 1f,	// 左上
            1f,  1f, 0f, 1f, 1f,	// 右上
            -1f, -1f, 0f, 0f, 0f,	// 左下
            1f, -1f, 0f, 1f, 0f	// 右下
    };

    private static final int FLOAT_SIZE_BYTES = 4;
    protected static final int VERTICES_DATA_POS_SIZE = 3;
    protected static final int VERTICES_DATA_UV_SIZE = 2;
    protected static final int VERTICES_DATA_STRIDE_BYTES = (VERTICES_DATA_POS_SIZE + VERTICES_DATA_UV_SIZE) * FLOAT_SIZE_BYTES;
    protected static final int VERTICES_DATA_POS_OFFSET = 0 * FLOAT_SIZE_BYTES;
    protected static final int VERTICES_DATA_UV_OFFSET = VERTICES_DATA_POS_OFFSET + VERTICES_DATA_POS_SIZE * FLOAT_SIZE_BYTES;

    //////////////////////////////////////////////////////////////////////////

    /**
     * 頂点シェーダーのソースコードを保持します。
     */
    private final String mVertexShaderSource;

    /**
     * フラグメントシェーダーのソースコードを保持します。
     */
    private final String mFragmentShaderSource;

    /**
     * プログラム識別子を保持します。
     */
    private int mProgram;

    /**
     * 頂点シェーダーの識別子を保持します。
     */
    private int mVertexShader;

    /**
     * フラグメントシェーダーの識別子を保持します。
     */
    private int mFragmentShader;

    /**
     * 頂点バッファオブジェクト名を保持します。
     */
    private int mVertexBufferName;

    /**
     * 変数名とハンドル識別子のマッピングを保持します。
     */
    private final HashMap<String, Integer> mHandleMap = new HashMap<String, Integer>();

    //////////////////////////////////////////////////////////////////////////
    // コンストラクタ

    /**
     * デフォルトコンストラクタです。
     */
    public GLES20Shader() {
        this(DEFAULT_VERTEX_SHADER, DEFAULT_FRAGMENT_SHADER);
    }

    /**
     * シェーダーのソースコードを指定してこのクラスのインスタンスを構築するコンストラクタです。
     *
     * @param vertexShaderSource ポリゴン描画用のバーテックスシェーダ (頂点シェーダ) のソースコード
     * @param fragmentShaderSource 色描画用のピクセル/フラグメントシェーダのソースコード
     */
    public GLES20Shader(final String vertexShaderSource, final String fragmentShaderSource) {
        mVertexShaderSource = vertexShaderSource;
        mFragmentShaderSource = fragmentShaderSource;
    }

    //////////////////////////////////////////////////////////////////////////

    /**
     * 指定された GLSL ソースコードをコンパイルしてプログラムオブジェクトを構成します。
     */
    public void setup() {
        release();
        mVertexShader   = GLES20Utils.loadShader(GLES20.GL_VERTEX_SHADER,   mVertexShaderSource);
        mFragmentShader = GLES20Utils.loadShader(GLES20.GL_FRAGMENT_SHADER, mFragmentShaderSource);
        mProgram        = GLES20Utils.createProgram(mVertexShader, mFragmentShader);
        mVertexBufferName   = GLES20Utils.createBuffer(VERTICES_DATA);
    }

    /**
     * フレームサイズを指定します。
     *
     * @param width フレームの幅
     * @param height フレームの高さ
     */
    public void setFrameSize(final int width, final int height) {
    }

    /**
     * このシェーダーオブジェクトの構成を破棄します。
     */
    public void release() {
        GLES20.glDeleteProgram(mProgram);
        GLES20.glDeleteShader(mVertexShader);
        GLES20.glDeleteShader(mFragmentShader);
        GLES20.glDeleteBuffers(1, new int[]{ mVertexBufferName }, 0);
        mProgram = 0;
        mVertexShader = 0;
        mFragmentShader = 0;
        mVertexBufferName = 0;
        mHandleMap.clear();
    }

    /**
     * 指定されたテクスチャ識別子を入力データとして描画します。
     *
     * @param texName テクスチャ識別子
     */
    public void draw(final int texName) {
        useProgram();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBufferName);
        GLES20.glEnableVertexAttribArray(getHandle("aPosition"));
        GLES20.glVertexAttribPointer(getHandle("aPosition"), VERTICES_DATA_POS_SIZE, GLES20.GL_FLOAT, false, VERTICES_DATA_STRIDE_BYTES, VERTICES_DATA_POS_OFFSET);
        GLES20.glEnableVertexAttribArray(getHandle("aTextureCoord"));
        GLES20.glVertexAttribPointer(getHandle("aTextureCoord"), VERTICES_DATA_UV_SIZE, GLES20.GL_FLOAT, false, VERTICES_DATA_STRIDE_BYTES, VERTICES_DATA_UV_OFFSET);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texName);
        GLES20.glUniform1i(getHandle("sTexture"), 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(getHandle("aPosition"));
        GLES20.glDisableVertexAttribArray(getHandle("aTextureCoord"));
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    //////////////////////////////////////////////////////////////////////////

    /**
     * プログラムを有効にします。
     */
    protected final void useProgram() {
        GLES20.glUseProgram(mProgram);
    }

    /**
     * 頂点バッファオブジェクトの識別子を返します。
     *
     * @return 頂点バッファオブジェクトの識別子。または {@code 0}
     */
    protected final int getVertexBufferName() {
        return mVertexBufferName;
    }

    /**
     * 指定された変数のハンドルを返します。
     *
     * @param name 変数
     * @return 変数のハンドル
     */
    protected final int getHandle(final String name) {
        final Integer value = mHandleMap.get(name);
        if (value != null) {
            return value.intValue();
        }

        int location = GLES20.glGetAttribLocation(mProgram, name);
        if (location == -1) {
            location = GLES20.glGetUniformLocation(mProgram, name);
        }
        if (location == -1) {
            throw new IllegalStateException("Could not get attrib or uniform location for " + name);
        }
        mHandleMap.put(name, Integer.valueOf(location));
        return location;
    }

}