package com.pocketvr.app;

/**
 * THE SEAM between a 3D game in the room and the renderer. Anything that implements this can be drawn by the renderer
 * (the standalone demo host here, or VRRenderer after merging: its field `world3` takes one of these instead of an NvSword3D).
 *
 * Conventions (the same as NvSword3D): world is Z up, metres. `rn` is the 4x4 world->view turn (column-major, as android.opengl.Matrix),
 * so the way you look is (-rn[2], -rn[6], -rn[10]), right is (rn[0], rn[4], rn[8]), up is (rn[1], rn[5], rn[9]).
 * (eyeX, eyeY, eyeZ) is where the eyes are in the world. `proj` is the projection that goes with rn.
 */
public interface NvGameWorld {
    /** run the game one step (call once per frame) */
    void update(float[] rn, float eyeX, float eyeY, float eyeZ, long nowNs);
    /** draw it (call once per eye if the picture is split) */
    void draw(float[] proj, float[] rn, float eyeX, float eyeY, float eyeZ);
    /** update + draw in one go: what NvSword3D.drawWorld does, for a renderer that draws once */
    default void drawWorld(float[] proj, float[] rn, float eyeX, float eyeY, float eyeZ) {
        update(rn, eyeX, eyeY, eyeZ, System.nanoTime());
        draw(proj, rn, eyeX, eyeY, eyeZ);
    }
    /** the finger pointer's direction in the room (unit vector) while the hand is in view; valid=false when it is gone */
    void pointer(boolean valid, float wx, float wy, float wz, long nowMs);
    /** the gun hand: where it points (unit vector in the room) and whether the trigger is pulled. valid=false: no hand, aim with the head */
    void gun(boolean valid, float dx, float dy, float dz, boolean trigger, long nowMs);
    /** the GL context was lost: forget programs and buffers */
    void glLost();
    /** true while the room should be swapped for the game's own scene */
    boolean envActive();
}
