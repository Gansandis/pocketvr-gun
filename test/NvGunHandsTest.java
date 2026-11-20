import com.pocketvr.app.NvGunHands;

/** Made-up hands: tests the gun-hand logic without a phone or a camera.  Run: sh test/run.sh */
public class NvGunHandsTest {
    static int fails = 0, checks = 0;
    static void check(String name, boolean ok) {
        checks++;
        if (!ok) { fails++; System.out.println("FAIL  " + name); } else System.out.println("ok    " + name);
    }

    // the head looks along +X (right = -Y, up = +Z): world -> view, column-major
    static final float[] RN = {0, 0, -1, 0, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1};
    static final float AR = 4f / 3f, TH = 0.65f, TV = 0.49f;
    static final float EX = 0, EY = 0, EZ = 1.6f;

    // finger states for a made-up hand
    static final int OUT = 1, CURLED = 0;

    /** 21 points of a hand whose palm centre is at the world point (wx,wy,wz); fingers = index, middle, ring, pinky: OUT or CURLED */
    static float[] hand(float wx, float wy, float wz, int... fingers) {
        float dx = wx - EX, dy = wy - EY, dz = wz - EZ;
        // into the view: v = M * (w - eye)   (M = RN column-major)
        float vx = RN[0] * dx + RN[4] * dy + RN[8] * dz, vy = RN[1] * dx + RN[5] * dy + RN[9] * dz, vz = RN[2] * dx + RN[6] * dy + RN[10] * dz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = vx / -vz / TH, ny = vy / -vz / TV;                 // -1..1 on the screen
        float cxN = (nx + 1f) / 2f, cyN = (1f - ny) / 2f;             // picture units 0..1
        float P = 0.095f / (2f * dist * TV);                           // a palm's length in picture heights
        float[] h = new float[1 + 63];
        h[0] = 1;
        // build in picture heights, then divide x by the aspect. Up in the picture = -y.
        float[] ox = {0, -0.30f, -0.10f, 0.10f, 0.30f};               // knuckle offsets across the palm (palm lengths)
        float wxp = cxN * AR, wyp = cyN + 0.55f * P;                   // the wrist is below the palm's centre
        set(h, 0, wxp, wyp, AR);
        // thumb: out to the side
        set(h, 1, wxp - 0.25f * P, wyp - 0.25f * P, AR); set(h, 2, wxp - 0.45f * P, wyp - 0.45f * P, AR);
        set(h, 3, wxp - 0.60f * P, wyp - 0.60f * P, AR); set(h, 4, wxp - 0.75f * P, wyp - 0.72f * P, AR);
        for (int f = 0; f < 4; f++) {
            int mcp = 5 + f * 4;
            float kx = wxp + ox[f + 1] * P, ky = wyp - 1.0f * P;
            set(h, mcp, kx, ky, AR);
            set(h, mcp + 1, kx, ky - 0.40f * P, AR);                   // the middle joint
            if (fingers[f] == OUT) { set(h, mcp + 2, kx, ky - 0.65f * P, AR); set(h, mcp + 3, kx, ky - 0.85f * P, AR); }
            else { set(h, mcp + 2, kx, ky - 0.20f * P, AR); set(h, mcp + 3, kx, ky + 0.25f * P, AR); }    // tip folded back toward the palm
        }
        return h;
    }
    static void set(float[] h, int k, float xh, float yh, float ar) { h[1 + k * 3] = xh / ar; h[1 + k * 3 + 1] = yh; h[1 + k * 3 + 2] = 0f; }

    static float[] two(float[] a, float[] b) {
        float[] d = new float[1 + 126];
        d[0] = 2;
        System.arraycopy(a, 1, d, 1, 63); System.arraycopy(b, 1, d, 64, 63);
        return d;
    }

    static long now = 1000;
    /** feed the same picture for `ms` milliseconds, 33 ms apart */
    static void run(NvGunHands g, float[] d, int ms) {
        for (int t = 0; t < ms; t += 33) {
            now += 33;
            if (d != null) g.setFrame(d, AR, TH, TV, false, now);
            else g.setFrame(null, AR, TH, TV, false, now);
            g.update(0.033f, now, RN, EX, EY, EZ);
        }
    }

    static NvGunHands fresh() {
        NvGunHands g = new NvGunHands();
        g.anchorF = 0.45f; g.anchorR = 0.10f; g.anchorD = 0.05f;      // the holster is in the camera's view in these tests (in the game it hangs lower: look down to see it)
        g.reset(); now += 5000;
        run(g, null, 100);
        return g;
    }

    public static void main(String[] a) {
        // where the gun hangs, for the hand placement below
        NvGunHands g = fresh();
        float gx = g.gunX, gy = g.gunY, gz = g.gunZ;
        check("the gun starts hanging at the body, in nobody's hand", g.gunState == NvGunHands.HOLSTERED && g.gunBlend == 0f && gx > 0.3f);

        float[] fist = hand(gx, gy, gz, CURLED, CURLED, CURLED, CURLED);
        float[] open = hand(gx, gy, gz, OUT, OUT, OUT, OUT);

        // ---- the made-up hands read as what they are ----
        run(g, fist, 400);
        check("a fist at the gun: seen as a fist", g.hand[0].valid && g.hand[0].fist && !g.hand[0].open);
        check("...and it takes the gun", g.gunState == NvGunHands.HELD);
        check("the palm is placed where it was put (+-8 cm)", Math.hypot(Math.hypot(g.hand[0].px - gx, g.hand[0].py - gy), g.hand[0].pz - gz) < 0.08);
        check("the gun is aimed along eyes->hand", g.aimFromHand && Math.abs(g.aimX - (gx - EX) / (float) Math.sqrt((gx - EX) * (gx - EX) + (gy - EY) * (gy - EY) + (gz - EZ) * (gz - EZ))) < 0.02);
        check("the gun is in the hand after the fly-in", g.gunBlend > 0.99f);

        // ---- the other hand fires with its index finger ----
        float tx = 0.55f, ty = 0.28f, tz = EZ - 0.05f;                       // the trigger hand: up to the left (+Y is left when looking along +X), further out
        float[] gunHand = hand(0.55f, -0.25f, EZ - 0.10f, CURLED, CURLED, CURLED, CURLED);
        float[] trigOut = hand(tx, ty, tz, OUT, CURLED, CURLED, CURLED);
        float[] trigIn = hand(tx, ty, tz, CURLED, CURLED, CURLED, CURLED);
        g = fresh();
        g.requestToggle();                                                   // draw the gun with a key, then adopt a fist
        run(g, gunHand, 500);
        check("gun taken by a key, then a fist adopts it", g.gunState == NvGunHands.HELD && g.aimFromHand);
        run(g, two(gunHand, trigIn), 500);
        check("a curled index finger that was never out does NOT fire (not armed)", !g.trigger);
        run(g, two(gunHand, trigOut), 400);
        check("index finger out = ready, not firing", g.armed && !g.trigger);
        run(g, two(gunHand, trigIn), 400);
        check("index finger curled = FIRE", g.trigger);
        run(g, two(gunHand, trigIn), 1500);
        check("it keeps firing while curled (machine gun)", g.trigger);
        run(g, two(gunHand, trigOut), 400);
        check("index finger out again = stop", !g.trigger);
        run(g, two(gunHand, hand(tx, ty, tz, CURLED, CURLED, CURLED, CURLED)), 1500);
        run(g, two(gunHand, hand(tx, ty, tz, OUT, CURLED, CURLED, CURLED)), 400);
        run(g, two(gunHand, hand(tx, ty, tz, CURLED, CURLED, CURLED, CURLED)), 100);
        check("the trigger hand can shoot again after a rest", g.trigger);
        run(g, two(gunHand, hand(tx, ty, tz, CURLED, CURLED, CURLED, CURLED)), 11000);
        check("a finger curled for ten seconds is a hand at rest: shooting stops until the finger is stretched", !g.armed && !g.trigger);

        // ---- the gun hand's own index finger never fires ----
        g = fresh();
        g.requestToggle();
        run(g, gunHand, 500);
        run(g, hand(0.55f, -0.25f, EZ - 0.10f, OUT, CURLED, CURLED, CURLED), 300);
        run(g, gunHand, 400);
        check("the hand that holds the gun does not fire it", !g.trigger);

        // ---- put the gun down: open the hand ----
        g = fresh();
        run(g, fist, 400);
        check("(taken again)", g.gunState == NvGunHands.HELD);
        run(g, open, 200);
        check("an open hand for a moment does not drop the gun", g.gunState == NvGunHands.HELD);
        run(g, open, 500);
        check("an open hand for half a second puts the gun down", g.gunState == NvGunHands.HOLSTERED && !g.aimFromHand);
        run(g, null, 400);
        check("the gun flies back to the body", g.gunBlend < 0.01f);

        // ---- the hand leaves the picture while holding the gun ----
        g = fresh();
        run(g, fist, 400);
        run(g, null, 600);
        check("hand out of view: still holding the gun, but the head aims", g.gunState == NvGunHands.HELD && !g.aimFromHand);

        // ---- a fist far from the gun takes nothing ----
        g = fresh();
        float[] farFist = hand(0.6f, 0.3f, EZ + 0.1f, CURLED, CURLED, CURLED, CURLED);
        run(g, farFist, 600);
        check("a fist away from the gun does not take it", g.gunState == NvGunHands.HOLSTERED && !g.grabReady);
        run(g, hand(gx, gy, gz + 0.0f, CURLED, CURLED, CURLED, CURLED), 40);
        check("a hand arriving at the gun lights it up", g.grabReady);
        g = fresh();
        run(g, hand(gx, gy, gz, OUT, OUT, OUT, OUT), 600);
        check("an open hand at the gun does not take it (and does not walk)", g.gunState == NvGunHands.HOLSTERED && g.walkSpeed == 0f && g.grabReady);

        // ---- walking: an open hand reached out, gun not in a hand ----
        g = fresh();
        float[] near = hand(0.30f, 0.0f, EZ + 0.0f, OUT, OUT, OUT, OUT);        // the arm bent: the hand is close
        run(g, near, 800);
        check("an open hand close to you does not walk", g.walkSpeed == 0f);
        float[] reach = hand(0.60f, 0.0f, EZ + 0.0f, OUT, OUT, OUT, OUT);       // the arm stretched
        run(g, reach, 1500);
        check("an open hand reached out walks (" + String.format("%.2f", g.walkSpeed) + " m/s)", g.walkSpeed > 0.3f);
        check("...toward where the hand is (straight ahead = +X)", Math.abs(g.walkYaw) < 0.1f);
        float[] left = hand(0.55f, 0.30f, EZ, OUT, OUT, OUT, OUT);
        run(g, left, 1500);
        check("a hand reached out to the left walks to the left", g.walkYaw > 0.25f);
        float[] further = hand(0.78f, 0.0f, EZ, OUT, OUT, OUT, OUT);
        float s1 = g.walkSpeed;
        run(g, reach, 1500); float sReach = g.walkSpeed;
        run(g, further, 1500);
        check("the further the arm, the faster (" + String.format("%.2f", sReach) + " -> " + String.format("%.2f", g.walkSpeed) + ")", g.walkSpeed > sReach + 0.2f);
        run(g, hand(0.60f, 0.0f, EZ, CURLED, CURLED, CURLED, CURLED), 1500);
        check("a closed hand stops the walking", g.walkSpeed == 0f);
        run(g, reach, 1500);
        run(g, null, 1500);
        check("the hand leaving the picture stops the walking", g.walkSpeed == 0f);

        // ---- no walking while the gun is held ----
        g = fresh();
        run(g, fist, 400);
        run(g, two(fist, reach), 1500);
        check("with the gun in a hand, an open hand does not walk", g.gunState == NvGunHands.HELD && g.walkSpeed == 0f);

        // ---- two hands swapping sides in the list must not swap the roles ----
        g = fresh();
        g.requestToggle();
        float[] leftHand = hand(0.55f, 0.20f, EZ - 0.05f, CURLED, CURLED, CURLED, CURLED);
        float[] rightHand = hand(0.55f, -0.20f, EZ - 0.05f, OUT, CURLED, CURLED, CURLED);
        run(g, rightHand, 500);                                              // the right hand adopts the gun
        run(g, two(leftHand, rightHand), 600);                               // (the model lists the left of the picture first)
        int slotOfGun = g.hand[0].px != 0 && g.hand[0].py < 0 ? 0 : 1;
        float gunSideY = g.hand[slotOfGun].py;
        run(g, two(rightHand, leftHand), 300);                               // the list order flips
        check("the hand that holds the gun stays the gun hand when the list order flips", g.hand[slotOfGun].py < 0 && gunSideY < 0);

        System.out.println("\n" + (checks - fails) + "/" + checks + " passed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
