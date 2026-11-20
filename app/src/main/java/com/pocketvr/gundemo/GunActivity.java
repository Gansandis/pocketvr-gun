package com.pocketvr.gundemo;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import com.pocketvr.app.NvGunCore;

/**
 * Stand-alone host for the shooter (to build and play it by itself, before it is merged into PocketVR).
 * Put the phone in a VR box (or just hold it in landscape). Look around to aim: the crosshair is the middle of the screen.
 *   tap / hold ........ fire (a tap on the title or game-over screen starts the game)
 *   two-finger tap .... reload          three-finger tap .... switch between one picture and the split VR picture
 *   volume up ......... change weapon (the rifle opens at wave 2)           volume down ...... reload
 *   a Bluetooth pad: A / R1 / R2 fire, X reload, Y change weapon
 */
public class GunActivity extends Activity implements SensorEventListener {
    private GLSurfaceView view;
    private GunRenderer rend;
    private SensorManager sm;
    private Sensor rotSensor;
    private GunSound sound;
    private Vibrator vib;
    private final float[] rv = new float[16];

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        rend = new GunRenderer();
        sound = new GunSound(this);
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        rend.world.hostListener = new NvGunCore.Listener() {
            @Override public void onShot(NvGunCore.Shot s) { sound.shot(rend.world.core.weapon == NvGunCore.RIFLE); buzz(10, 70); }
            @Override public void onHit(NvGunCore.Enemy e, boolean head, boolean kill) { if (kill) { sound.kill(); buzz(25, 120); } else if (head) sound.head(); else sound.hit(); }
            @Override public void onHurt(float fromAz, int dmg) { sound.hurt(); buzz(70, 220); }
            @Override public void onDry() { sound.dry(); }
            @Override public void onReload() { sound.reload(); }
            @Override public void onReloaded() { sound.reloaded(); }
            @Override public void onWave(int wave, boolean boss) { sound.wave(); }
            @Override public void onGameOver(int score) { sound.over(); buzz(250, 255); }
        };

        view = new GLSurfaceView(this);
        view.setEGLContextClientVersion(2);
        view.setEGLConfigChooser(8, 8, 8, 8, 24, 0);
        view.setPreserveEGLContextOnPause(false);
        view.setRenderer(rend);
        setContentView(view);

        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotSensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);                 // no compass: it never jumps because of a magnet in the VR box
        if (rotSensor == null) rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    private void buzz(int ms, int amp) {
        try { if (vib != null) vib.vibrate(VibrationEffect.createOneShot(ms, amp)); } catch (Throwable ignored) { }
    }

    @Override protected void onResume() {
        super.onResume();
        view.onResume();
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (rotSensor != null) sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override protected void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
        rend.world.setTrigger(false);
        view.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (sound != null) sound.release();
    }

    // ---------- the head turn ----------
    @Override public void onSensorChanged(SensorEvent e) {
        try {
            float[] v = e.values.length > 4 ? java.util.Arrays.copyOf(e.values, 4) : e.values;    // some phones give 5 numbers and the call below then throws
            SensorManager.getRotationMatrixFromVector(rv, v);
            int ax, ay;
            switch (getWindowManager().getDefaultDisplay().getRotation()) {
                case Surface.ROTATION_90: ax = SensorManager.AXIS_Y; ay = SensorManager.AXIS_MINUS_X; break;
                case Surface.ROTATION_180: ax = SensorManager.AXIS_MINUS_X; ay = SensorManager.AXIS_MINUS_Y; break;
                case Surface.ROTATION_270: ax = SensorManager.AXIS_MINUS_Y; ay = SensorManager.AXIS_X; break;
                default: ax = SensorManager.AXIS_X; ay = SensorManager.AXIS_Y; break;
            }
            float[] out = new float[16];
            SensorManager.remapCoordinateSystem(rv, ax, ay, out);
            // the sensor gives "screen -> world", row by row; the renderer wants "world -> screen" as a column-major matrix:
            // the very same 16 numbers, read the other way (a turn's inverse is its transpose)
            rend.rot = out;
        } catch (Throwable ignored) { }
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }

    // ---------- trigger and buttons ----------
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (rend.world.core.state != NvGunCore.PLAYING) rend.world.requestStart();
                rend.world.setTrigger(true);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) rend.world.requestReload();
                else if (e.getPointerCount() >= 3) rend.stereo = !rend.stereo;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                rend.world.setTrigger(false);
                break;
            default: break;
        }
        return true;
    }

    private static boolean isTrigger(int k) {
        return k == KeyEvent.KEYCODE_BUTTON_A || k == KeyEvent.KEYCODE_BUTTON_R1 || k == KeyEvent.KEYCODE_BUTTON_R2 || k == KeyEvent.KEYCODE_SPACE || k == KeyEvent.KEYCODE_ENTER;
    }

    @Override public boolean onKeyDown(int k, KeyEvent ev) {
        if (isTrigger(k)) {
            if (ev.getRepeatCount() == 0) { if (rend.world.core.state != NvGunCore.PLAYING) rend.world.requestStart(); rend.world.setTrigger(true); }
            return true;
        }
        if (ev.getRepeatCount() == 0) {
            if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_BUTTON_Y) { rend.world.requestSwitch(); return true; }
            if (k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_BUTTON_X) { rend.world.requestReload(); return true; }
        } else if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN) return true;
        return super.onKeyDown(k, ev);
    }

    @Override public boolean onKeyUp(int k, KeyEvent ev) {
        if (isTrigger(k)) { rend.world.setTrigger(false); return true; }
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN) return true;
        return super.onKeyUp(k, ev);
    }
}
