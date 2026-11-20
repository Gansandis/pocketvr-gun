import com.pocketvr.app.NvGunCore;
import com.pocketvr.app.NvGunCore.Enemy;

/** Runs on any computer, no Android needed:  see test/run.sh */
public class NvGunCoreTest {
    static int fails = 0, checks = 0;
    static void check(String name, boolean ok) {
        checks++;
        if (!ok) { fails++; System.out.println("FAIL  " + name); } else System.out.println("ok    " + name);
    }

    static NvGunCore fresh() {
        NvGunCore c = new NvGunCore(42);
        c.start(0f, 0f, 0f, 0f);                       // eyes at the origin, looking along +X; the floor is 1.6 m below
        for (int i = 0; i < 10; i++) c.tick(0.05f, 0, 0, 0, 1, 0, 0, false);    // let the start-up delay pass (banner stops spawns for 2.2 s)
        c.enemies.clear();
        return c;
    }

    /** pull the trigger once: one tick up, one tick down, then wait out the cool-down */
    static NvGunCore.Shot pull(NvGunCore c, float dx, float dy, float dz) {
        c.tick(0.05f, 0, 0, 0, dx, dy, dz, false);
        c.tick(0.01f, 0, 0, 0, dx, dy, dz, true);
        NvGunCore.Shot s = new NvGunCore.Shot();
        s.fired = c.shot.fired; s.result = c.shot.result; s.kill = c.shot.kill; s.enemy = c.shot.enemy; s.dry = c.shot.dry;
        c.tick(0.05f, 0, 0, 0, dx, dy, dz, false);
        for (int i = 0; i < 6; i++) c.tick(0.05f, 0, 0, 0, dx, dy, dz, false);
        return s;
    }

    public static void main(String[] a) {
        // ---- start ----
        NvGunCore c = new NvGunCore(1);
        check("starts on the title screen", c.state == NvGunCore.TITLE);
        c.start(0, 0, 0, 0);
        check("start -> playing, wave 1, full hp", c.state == NvGunCore.PLAYING && c.wave == 1 && c.hp == 100);
        check("floor is eye height below", Math.abs(c.floorZ + 1.6f) < 1e-5);

        // ---- hit-scan: head, body, miss ----
        c = fresh();
        Enemy g = c.spawn(NvGunCore.GRUNT, 0f, 6f);
        NvGunCore.Shot s = pull(c, 6f, 0f, 0.02f);                 // the head is at x=6, z=-1.6+1.62
        check("aim at the head -> head hit", s.fired && s.result == 2);
        check("head does double damage (2 x2 of 6 hp)", Math.abs(g.hp - 2f) < 1e-4);
        s = pull(c, 6f, 0f, 0.02f);
        check("second head shot kills", s.kill && c.enemies.isEmpty() && c.kills == 1);
        check("a kill gives score (10 + 5 head)", c.score == 15);

        c = fresh();
        g = c.spawn(NvGunCore.GRUNT, 0f, 6f);
        s = pull(c, 6f, 0f, -0.6f);
        check("aim at the chest -> body hit", s.result == 1 && Math.abs(g.hp - 4f) < 1e-4);
        s = pull(c, 0f, 1f, 0f);
        check("aim away -> miss", s.fired && s.result == 0);
        s = pull(c, 6f, 0.3f, 0.02f);
        check("a little off the head, inside the aim help -> still a head hit", s.result == 2);
        c = fresh();
        c.spawn(NvGunCore.GRUNT, 0f, 6f);
        s = pull(c, 6f, 0.8f, 0.02f);
        check("far off -> miss", s.result == 0);

        c = fresh();
        Enemy near = c.spawn(NvGunCore.GRUNT, 0f, 3f);
        Enemy far = c.spawn(NvGunCore.GRUNT, 0f, 8f);
        s = pull(c, 1f, 0f, -0.35f);
        check("the nearer enemy takes the bullet", s.enemy == near || (s.enemy == null));
        check("the far one behind it is untouched", far.hp == far.maxHp);

        c = fresh();
        Enemy d = c.spawn(NvGunCore.DRONE, 0f, 6f);
        c.tick(0.05f, 0, 0, 0, 1, 0, 0, false);                     // it hovers and sways side to side: aim at where it is now
        float ddx = c.xOf(d), ddy = c.yOf(d), ddz = c.zOf(d) + 0.4f;
        c.tick(0.01f, 0, 0, 0, ddx, ddy, ddz, true);
        check("a hovering drone can be hit", c.shot.fired && c.shot.result == 1);

        // ---- ammo and reload ----
        c = fresh();
        for (int i = 0; i < 12; i++) pull(c, 0f, 1f, 0f);
        check("12 shots empty the pistol and start the reload", c.ammo[0] == 0 && c.reloadT > 0f);
        for (int i = 0; i < 30; i++) c.tick(0.05f, 0, 0, 0, 0, 1, 0, false);
        check("reload refills the magazine", c.ammo[0] == 12 && c.reloadT == 0f);
        c = fresh();
        c.reload();
        check("reload with a full magazine does nothing", c.reloadT == 0f);

        // ---- the held trigger: pistol is semi-auto, rifle is full-auto ----
        c = fresh();
        c.tick(0.05f, 0, 0, 0, 0, 1, 0, false);
        int shots = 0;
        for (int i = 0; i < 20; i++) { c.tick(0.05f, 0, 0, 0, 0, 1, 0, true); if (c.shot.fired) shots++; }
        check("pistol: holding the trigger fires once", shots == 1);
        c = fresh();
        c.rifleUnlocked = true;
        check("rifle switch works once unlocked", c.switchWeapon() && c.weapon == NvGunCore.RIFLE);
        for (int i = 0; i < 10; i++) c.tick(0.05f, 0, 0, 0, 0, 1, 0, false);
        shots = 0;
        for (int i = 0; i < 20; i++) { c.tick(0.05f, 0, 0, 0, 0, 1, 0, true); if (c.shot.fired) shots++; }
        check("rifle: holding the trigger fires many times (" + shots + ")", shots >= 8);

        // ---- enemies hurt you ----
        c = fresh();
        final int[] hurt = {0};
        c.listener = new NvGunCore.Listener() { @Override public void onHurt(float az, int dmg) { hurt[0] += dmg; } };
        c.spawn(NvGunCore.GRUNT, 0f, 1.0f);
        c.tick(0.05f, 0, 0, 0, 0, 1, 0, false);
        check("an enemy at your feet hits at once (8 dmg)", c.hp == 92 && hurt[0] == 8);
        final boolean[] over = {false};
        c.listener = new NvGunCore.Listener() { @Override public void onGameOver(int sc) { over[0] = true; } };
        for (int i = 0; i < 400 && c.state == NvGunCore.PLAYING; i++) c.tick(0.05f, 0, 0, 0, 0, 1, 0, false);
        check("left alone you lose: game over, hp 0", c.state == NvGunCore.OVER && c.hp == 0 && over[0]);

        // ---- waves ----
        c = new NvGunCore(7);
        c.start(0, 0, 0, 0);
        int maxWave = 0;
        for (int i = 0; i < 4000; i++) { c.tick(0.05f, 0, 0, 0, 0, 1, 0, false); if (c.state != NvGunCore.PLAYING) break; c.enemies.clear(); maxWave = Math.max(maxWave, c.wave); if (c.wave >= 6) break; }
        check("waves advance when the field is clear (reached " + maxWave + ")", maxWave >= 6);
        check("the rifle unlocks from wave 2", c.rifleUnlocked);

        // ---- enemies spawn where promised ----
        c = new NvGunCore(3);
        c.start(5, 5, 1, 0);
        for (int i = 0; i < 100; i++) c.tick(0.05f, 5, 5, 1, 1, 0, 0, false);
        boolean ring = !c.enemies.isEmpty();
        for (Enemy e : c.enemies) { if (e.dist > 12.5f || e.dist < 6f) ring = false; if (Math.abs(c.xOf(e) - 5) > 13) ring = false; }
        check("enemies appear on the ring around the start spot", ring);

        System.out.println("\n" + (checks - fails) + "/" + checks + " passed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
