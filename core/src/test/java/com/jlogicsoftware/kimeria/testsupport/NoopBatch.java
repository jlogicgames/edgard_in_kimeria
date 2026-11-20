package com.jlogicsoftware.kimeria.testsupport;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;

/**
 * A {@link Batch} that draws nothing and allocates nothing itself, so a frame loop through it measures only the
 * code under test. The colour and blend state are kept because game code reads them back (e.g. {@code getColor()}).
 */
public final class NoopBatch implements Batch {
    private final Color color = new Color(Color.WHITE);
    private final Matrix4 projection = new Matrix4(), transform = new Matrix4();
    private int srcFunc, dstFunc, srcAlpha, dstAlpha;
    private boolean blending = true, drawing;
    /** Optional log of the {@code draw(region, x, y, width, height)} calls, in order: set by {@link #record(int)}, null (and free) otherwise. */
    private TextureRegion[] drawnRegions;
    private float[] drawnX, drawnY;
    private int drawnCount;

    /** Starts logging up to {@code capacity} region draws (the five-argument form the tile layers use); clears an earlier log. */
    public void record(int capacity) { drawnRegions = new TextureRegion[capacity]; drawnX = new float[capacity]; drawnY = new float[capacity]; drawnCount = 0; }
    public int drawnCount() { return drawnCount; }
    public TextureRegion drawnRegion(int i) { return drawnRegions[i]; }
    public float drawnX(int i) { return drawnX[i]; }
    public float drawnY(int i) { return drawnY[i]; }

    @Override public void begin() { drawing = true; }
    @Override public void end() { drawing = false; }
    @Override public void setColor(Color tint) { color.set(tint); }
    @Override public void setColor(float r, float g, float b, float a) { color.set(r, g, b, a); }
    @Override public Color getColor() { return color; }
    @Override public void setPackedColor(float packedColor) { Color.abgr8888ToColor(color, packedColor); }
    @Override public float getPackedColor() { return color.toFloatBits(); }
    @Override public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height,
        float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) { }
    @Override public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY,
        int srcWidth, int srcHeight, boolean flipX, boolean flipY) { }
    @Override public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) { }
    @Override public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) { }
    @Override public void draw(Texture texture, float x, float y) { }
    @Override public void draw(Texture texture, float x, float y, float width, float height) { }
    @Override public void draw(Texture texture, float[] spriteVertices, int offset, int count) { }
    @Override public void draw(TextureRegion region, float x, float y) { }
    @Override public void draw(TextureRegion region, float x, float y, float width, float height) {
        if (drawnRegions != null && drawnCount < drawnRegions.length) { drawnRegions[drawnCount] = region; drawnX[drawnCount] = x; drawnY[drawnCount++] = y; }
    }
    @Override public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height,
        float scaleX, float scaleY, float rotation) { }
    @Override public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height,
        float scaleX, float scaleY, float rotation, boolean clockwise) { }
    @Override public void draw(TextureRegion region, float width, float height, Affine2 transform) { }
    @Override public void flush() { }
    @Override public void disableBlending() { blending = false; }
    @Override public void enableBlending() { blending = true; }
    @Override public void setBlendFunction(int src, int dst) { srcFunc = srcAlpha = src; dstFunc = dstAlpha = dst; }
    @Override public void setBlendFunctionSeparate(int src, int dst, int alphaSrc, int alphaDst) {
        srcFunc = src; dstFunc = dst; srcAlpha = alphaSrc; dstAlpha = alphaDst;
    }
    @Override public int getBlendSrcFunc() { return srcFunc; }
    @Override public int getBlendDstFunc() { return dstFunc; }
    @Override public int getBlendSrcFuncAlpha() { return srcAlpha; }
    @Override public int getBlendDstFuncAlpha() { return dstAlpha; }
    @Override public Matrix4 getProjectionMatrix() { return projection; }
    @Override public Matrix4 getTransformMatrix() { return transform; }
    @Override public void setProjectionMatrix(Matrix4 matrix) { projection.set(matrix); }
    @Override public void setTransformMatrix(Matrix4 matrix) { transform.set(matrix); }
    @Override public void setShader(ShaderProgram shader) { }
    @Override public ShaderProgram getShader() { return null; }
    @Override public boolean isBlendingEnabled() { return blending; }
    @Override public boolean isDrawing() { return drawing; }
    @Override public void dispose() { }
}
