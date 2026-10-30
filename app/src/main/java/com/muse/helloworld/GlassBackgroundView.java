package com.muse.helloworld;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class GlassBackgroundView extends View {
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int palette = 0;
    private float tiltX = 0f, tiltY = 0f;
    private float glow = 1.0f;
    private float softness = 90f;

    // base, blob1, blob2, blob3, blob4 per palette
    private int[][] palettes = {
        {0xFF0B1026, 0xFF7C3AED, 0xFFEC4899, 0xFF14B8A6, 0xFF3B82F6}, // Aurora
        {0xFF041525, 0xFF0072FF, 0xFF00C2FF, 0xFF0EA5E9, 0xFF312E81}, // Ocean
        {0xFF1D0A14, 0xFFF97316, 0xFFEC4899, 0xFFF43F5E, 0xFFF59E0B}, // Sunset
        {0xFF061A12, 0xFF10B981, 0xFF2DD4BF, 0xFFA3E635, 0xFF065F46}  // Emerald
    };

    public GlassBackgroundView(Context c) { super(c); init(); }
    public GlassBackgroundView(Context c, AttributeSet a) { super(c, a); init(); }
    private void init() { setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

    public void setPalette(int p) { palette = p % palettes.length; invalidate(); }
    public void setTilt(float x, float y) { tiltX = x; tiltY = y; invalidate(); }
    public void setGlow(float g) { glow = g; invalidate(); }
    public void setSoftness(float s) { softness = s; invalidate(); }
    public int getPalette() { return palette; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int[] pal = palettes[palette];

        // base gradient
        paint.setShader(new LinearGradient(0, 0, w, h, pal[0], Color.BLACK, Shader.TileMode.CLAMP));
        paint.setMaskFilter(null);
        canvas.drawRect(0, 0, w, h, paint);

        // soft blurred blobs - the colour the glass refracts / frosts over
        paint.setMaskFilter(new BlurMaskFilter(softness, BlurMaskFilter.Blur.NORMAL));
        float dx = tiltX * w * 0.045f, dy = tiltY * h * 0.045f;
        drawBlob(canvas, w*0.18f - dx,       h*0.16f - dy,       w*0.55f, pal[1]);
        drawBlob(canvas, w*0.88f + dx*1.6f,  h*0.28f + dy*0.6f,  w*0.50f, pal[2]);
        drawBlob(canvas, w*0.75f + dx*0.7f,  h*0.82f + dy*1.5f,  w*0.58f, pal[3]);
        drawBlob(canvas, w*0.12f - dx*1.3f,  h*0.78f - dy*0.8f,  w*0.42f, pal[4]);
        paint.setMaskFilter(null);
        paint.setShader(null);
    }

    private void drawBlob(Canvas c, float cx, float cy, float r, int color) {
        int a1 = Math.min(255, (int)(235 * glow)), a2 = Math.min(255, (int)(110 * glow));
        paint.setShader(new RadialGradient(cx, cy, r,
            new int[]{withAlpha(color, a1), withAlpha(color, a2), withAlpha(color, 0)},
            new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, paint);
    }
    private int withAlpha(int c, int a) { return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c)); }
}
