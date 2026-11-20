package com.pocketvr.gundemo;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** The game's sounds, made from maths at start-up (no audio files in the project): a few short WAVs written to the cache and loaded into a SoundPool. */
final class GunSound {
    private static final int SR = 22050;
    private final SoundPool pool;
    private final int shot, rifle, hit, head, kill, hurt, reload, reloaded, dry, wave, over;

    GunSound(Context ctx) {
        pool = new SoundPool.Builder().setMaxStreams(10)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        File d = ctx.getCacheDir();
        shot = load(d, "shot", synthShot(0.13f, 34f, 0.38f, 150f));
        rifle = load(d, "rifle", synthShot(0.08f, 55f, 0.50f, 190f));
        hit = load(d, "hit", tone(1500f, 1500f, 0.05f, 55f));
        head = load(d, "head", mix(tone(2300f, 2300f, 0.08f, 40f), tone(3100f, 3100f, 0.08f, 40f)));
        kill = load(d, "kill", tone(700f, 1500f, 0.14f, 12f));
        hurt = load(d, "hurt", mix(tone(110f, 70f, 0.22f, 9f), noise(0.22f, 14f, 0.25f, 0.5f)));
        reload = load(d, "reload", concat(click(), silence(0.40f), click()));
        reloaded = load(d, "reloaded", concat(click(), silence(0.05f), tone(900f, 900f, 0.07f, 30f)));
        dry = load(d, "dry", click());
        wave = load(d, "wave", concat(tone(520f, 520f, 0.10f, 14f), tone(660f, 660f, 0.10f, 14f), tone(780f, 780f, 0.18f, 10f)));
        over = load(d, "over", tone(400f, 110f, 0.7f, 3.5f));
    }

    void shot(boolean rifleGun) { pool.play(rifleGun ? rifle : shot, 1f, 1f, 1, 0, 1f); }
    void hit() { pool.play(hit, 0.8f, 0.8f, 1, 0, 1f); }
    void head() { pool.play(head, 0.9f, 0.9f, 1, 0, 1f); }
    void kill() { pool.play(kill, 0.8f, 0.8f, 1, 0, 1f); }
    void hurt() { pool.play(hurt, 1f, 1f, 1, 0, 1f); }
    void reload() { pool.play(reload, 0.8f, 0.8f, 1, 0, 1f); }
    void reloaded() { pool.play(reloaded, 0.8f, 0.8f, 1, 0, 1f); }
    void dry() { pool.play(dry, 0.8f, 0.8f, 1, 0, 1f); }
    void wave() { pool.play(wave, 0.7f, 0.7f, 1, 0, 1f); }
    void over() { pool.play(over, 0.9f, 0.9f, 1, 0, 1f); }
    void release() { pool.release(); }

    // ---------- synthesis ----------
    private static final Random R = new Random(5);

    private static short[] tone(float f0, float f1, float sec, float decay) {
        int n = (int) (sec * SR);
        short[] o = new short[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) SR, k = i / (float) n;
            ph += 2 * Math.PI * (f0 + (f1 - f0) * k) / SR;
            o[i] = (short) (Math.sin(ph) * Math.exp(-t * decay) * 0.7 * 32767);
        }
        return o;
    }

    private static short[] noise(float sec, float decay, float amp, float lp) {
        int n = (int) (sec * SR);
        short[] o = new short[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) SR;
            y += (R.nextFloat() * 2f - 1f - y) * lp;                      // one-pole low-pass: a smaller lp = a duller sound
            o[i] = (short) (y * Math.exp(-t * decay) * amp * 32767);
        }
        return o;
    }

    private static short[] synthShot(float sec, float decay, float lp, float thumpHz) {
        short[] n = noise(sec, decay, 0.85f, lp);
        short[] t = tone(thumpHz, thumpHz * 0.5f, sec, decay * 0.8f);
        return mix(n, t);
    }

    private static short[] click() { return noise(0.03f, 120f, 0.7f, 0.7f); }
    private static short[] silence(float sec) { return new short[(int) (sec * SR)]; }

    private static short[] mix(short[] a, short[] b) {
        short[] o = new short[Math.max(a.length, b.length)];
        for (int i = 0; i < o.length; i++) {
            int v = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
            o[i] = (short) Math.max(-32767, Math.min(32767, v * 3 / 4));
        }
        return o;
    }

    private static short[] concat(short[]... parts) {
        int n = 0;
        for (short[] p : parts) n += p.length;
        short[] o = new short[n];
        int at = 0;
        for (short[] p : parts) { System.arraycopy(p, 0, o, at, p.length); at += p.length; }
        return o;
    }

    private int load(File dir, String name, short[] pcm) {
        try {
            File f = new File(dir, "gun_" + name + ".wav");
            try (FileOutputStream os = new FileOutputStream(f)) {
                int bytes = pcm.length * 2;
                byte[] h = new byte[44];
                put(h, 0, "RIFF"); le(h, 4, 36 + bytes); put(h, 8, "WAVE"); put(h, 12, "fmt "); le(h, 16, 16);
                h[20] = 1; h[22] = 1; le(h, 24, SR); le(h, 28, SR * 2); h[32] = 2; h[34] = 16; put(h, 36, "data"); le(h, 40, bytes);
                os.write(h);
                byte[] b = new byte[bytes];
                for (int i = 0; i < pcm.length; i++) { b[2 * i] = (byte) (pcm[i] & 0xFF); b[2 * i + 1] = (byte) ((pcm[i] >> 8) & 0xFF); }
                os.write(b);
            }
            return pool.load(f.getAbsolutePath(), 1);
        } catch (Throwable t) { return 0; }
    }

    private static void put(byte[] b, int at, String s) { for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i); }
    private static void le(byte[] b, int at, int v) { b[at] = (byte) v; b[at + 1] = (byte) (v >> 8); b[at + 2] = (byte) (v >> 16); b[at + 3] = (byte) (v >> 24); }
}
