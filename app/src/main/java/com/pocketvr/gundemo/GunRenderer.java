package com.pocketvr.gundemo;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.pocketvr.app.NvGunGfx;
import com.pocketvr.app.NvGunWorld;

import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The stand-alone "host": everything PocketVR's VRRenderer would normally provide to the game, in the smallest form:
 * the head turn (from the activity's sensors), the eye position, the projection, one or two eyes, and a night scene to stand in
 * (in PocketVR this is the real room seen through the camera). The game itself is NvGunWorld.
 */
final class GunRenderer implements GLSurfaceView.Renderer {
    final NvGunWorld world = new NvGunWorld();
    private final NvGunGfx gfx = new NvGunGfx();

    volatile float[] rot = new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};   // world -> view turn of the head (written by the sensor callback)
    volatile boolean stereo = true;

    static final float EYE_Z = 1.6f;          // the eyes are 1.6 m above the floor (the floor is z = 0)
    static final float IPD = 0.064f;          // distance between the eyes, metres

    private int w = 1, h = 1;
    private final float[] proj = new float[16], vp = new float[16], mon = new float[16], t1 = new float[16];

    // the scenery: tall pillars far away all around (so turning your head is felt) and a moon
    private static final int NP = 24;
    private final float[] pa = new float[NP], pr = new float[NP], ph = new float[NP];
    private final int[] pc = new int[NP];

    GunRenderer() {
        Random r = new Random(11);
        int[] cols = {0xFF2D3A5C, 0xFF3A2D5C, 0xFF2D5C54, 0xFF5C2D4A, 0xFF4A4F66};
        for (int i = 0; i < NP; i++) {
            pa[i] = (float) (i * 2 * Math.PI / NP + r.nextFloat() * 0.15f);
            pr[i] = 17f + r.nextFloat() * 6f;
            ph[i] = 3f + r.nextFloat() * 8f;
            pc[i] = cols[r.nextInt(cols.length)];
        }
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        gfx.lost(); world.glLost();                                  // a new GL context: nothing from the old one is valid
        GLES20.glClearColor(0.03f, 0.05f, 0.11f, 1f);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) { w = Math.max(1, width); h = Math.max(1, height); }

    @Override public void onDrawFrame(GL10 gl) {
        float[] rn = rot;
        world.update(rn, 0f, 0f, EYE_Z, System.nanoTime());          // the game moves once, however many eyes are drawn

        GLES20.glViewport(0, 0, w, h);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (!gfx.ready()) gfx.init();

        boolean s = stereo;
        int eyes = s ? 2 : 1;
        int ew = w / eyes;
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        for (int e = 0; e < eyes; e++) {
            GLES20.glViewport(e * ew, 0, ew, h);
            GLES20.glScissor(e * ew, 0, ew, h);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
            Matrix.perspectiveM(proj, 0, s ? 92f : 72f, (float) ew / h, 0.05f, 120f);
            float sh = s ? (e == 0 ? -IPD / 2f : IPD / 2f) : 0f;      // each eye is a little to its side of the head's middle
            float ex = rn[0] * sh, ey = rn[4] * sh, ez = EYE_Z + rn[8] * sh;
            drawScenery(rn, ex, ey, ez);
            world.draw(proj, rn, ex, ey, ez);
            world.drawHud(proj);
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    // ---------- the night scene ----------
    private void solid(NvGunGfx.Mesh m, float x, float y, float z, float sx, float sy, float sz, int color, float ex, float ey, float ez) {
        Matrix.setIdentityM(mon, 0);
        Matrix.translateM(mon, 0, x - ex, y - ey, z - ez);
        Matrix.scaleM(mon, 0, sx, sy, sz);
        Matrix.multiplyMM(t1, 0, vp, 0, mon, 0);
        gfx.draw(m, t1, mon, color, 0f, 1f);
    }

    private void drawScenery(float[] rn, float ex, float ey, float ez) {
        Matrix.multiplyMM(vp, 0, proj, 0, rn, 0);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        gfx.begin();
        solid(gfx.box, 0, 0, -0.02f, 60f, 60f, 0.02f, 0xFF1D2433, ex, ey, ez);                    // the floor
        for (int i = -10; i <= 10; i++) {                                                          // a grid on it: the floor's motion is what tells you you are standing still
            float g = i * 4f;
            solid(gfx.box, g, 0, 0.005f, 0.04f, 40f, 0.004f, 0xFF3C4A66, ex, ey, ez);
            solid(gfx.box, 0, g, 0.005f, 40f, 0.04f, 0.004f, 0xFF3C4A66, ex, ey, ez);
        }
        for (int i = 0; i < NP; i++) {
            float x = (float) Math.cos(pa[i]) * pr[i], y = (float) Math.sin(pa[i]) * pr[i];
            solid(gfx.box, x, y, ph[i], 0.8f, 0.8f, ph[i], pc[i], ex, ey, ez);
            solid(gfx.sphere, x, y, ph[i] * 2f + 0.3f, 0.35f, 0.35f, 0.35f, 0xFFFFC24D, ex, ey, ez);   // a lamp on top of each
        }
        solid(gfx.sphere, 55f, 40f, 38f, 4f, 4f, 4f, 0xFFE8EEFF, ex, ey, ez);                        // the moon
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }
}
