package com.pocketvr.gundemo;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.pocketvr.app.NvGunWorld;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The stand-alone "host": everything PocketVR's VRRenderer would normally provide to the game, in the smallest form:
 * the head turn (from the activity's sensors), the eye position, the projection and one or two eyes.
 * (In PocketVR the room is the real room seen through the camera.) The game, with its own floor and scenery, is NvGunWorld.
 */
final class GunRenderer implements GLSurfaceView.Renderer {
    final NvGunWorld world = new NvGunWorld();

    volatile float[] rot = new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};   // world -> view turn of the head (written by the sensor callback)
    volatile boolean stereo = true;

    static final float EYE_Z = 1.6f;          // the eyes are 1.6 m above the floor (the floor is z = 0)
    static final float IPD = 0.064f;          // distance between the eyes, metres

    private int w = 1, h = 1;
    private final float[] proj = new float[16];

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        world.glLost();                                  // a new GL context: nothing from the old one is valid
        GLES20.glClearColor(0.03f, 0.05f, 0.11f, 1f);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) { w = Math.max(1, width); h = Math.max(1, height); }

    @Override public void onDrawFrame(GL10 gl) {
        float[] rn = rot;
        world.update(rn, 0f, 0f, EYE_Z, System.nanoTime());          // the game moves once, however many eyes are drawn

        GLES20.glViewport(0, 0, w, h);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

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
            world.draw(proj, rn, ex, ey, ez);
            world.drawHud(proj);
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
