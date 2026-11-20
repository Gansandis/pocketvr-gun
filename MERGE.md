# รวมเข้า PocketVR (ทำตอนที่โปรเจกต์หลักนิ่งแล้ว)

ค้นหาด้วยข้อความ ไม่ใช้เลขบรรทัด เพราะโปรเจกต์หลักเปลี่ยนอยู่เรื่อย ๆ

1. **ก๊อป 5 ไฟล์** `NvGameWorld, NvGunCore, NvGunWorld, NvGunHud, NvGunGfx` ไปที่ `app/src/main/java/com/pocketvr/app/`
   (ถ้าอยากใช้ `Nv3D` ของเดิม: ลบ `NvGunGfx` แล้วแก้ชื่อคลาสใน `NvGunWorld` กับ `GunRenderer` — เมธอดเหมือนกัน แต่ต้องเปิด `Mesh`/`init()` ให้ public ถ้า host อยู่คนละแพ็กเกจ)
2. **VRRenderer**: เปลี่ยนฟิลด์ `public volatile NvSword3D world3` เป็น `NvGameWorld world3` และที่ใช้ตัวแปรท้องถิ่น `NvSword3D wd = world3;`
   (5 จุด: ฟิลด์, `glLost`, `draw3D`, `pointer(false…)`, `pointer(true…)`) เปลี่ยนเป็น `NvGameWorld wd`
3. **NvSword3D**: เติม `implements NvGameWorld` (เดิม `implements NvContent` ก็ยังอยู่) แล้วเพิ่มเมธอดว่าง 3 ตัว
   `public void update(float[] rn,float x,float y,float z,long t){}`, `public void draw(float[] p,float[] rn,float x,float y,float z){}`,
   `public void gun(boolean v,float dx,float dy,float dz,boolean tr,long t){}`
   (`drawWorld`, `pointer`, `glLost`, `envActive` มีอยู่แล้ว ลายเซ็นตรงกัน)
4. **ส่งท่ามือเข้าเกม** ใน VRRenderer ตรงบล็อก "the fingertip's direction in the room, for the 3D game" เติม (ยังไม่ได้ทดสอบ ต้องจูนบนเครื่อง):
   ```java
   // finger-gun: aim = index fingertip (landmark 8), fire = thumb folds onto the middle finger (same distance as ratioM)
   if (wd != null) {
       float gx = r[1 + 8 * 3], gy = r[1 + 8 * 3 + 1];
       if (rot180) { gx = 1f - gx; gy = 1f - gy; }
       float vx = (gx * 2f - 1f) * tanHc, vy = (1f - gy * 2f) * tanVc, vz = -1f;
       float[] q = rotView;
       wd.gun(true, q[0]*vx + q[1]*vy + q[2]*vz, q[4]*vx + q[5]*vy + q[6]*vz, q[8]*vx + q[9]*vy + q[10]*vz, ratioM < 0.30f, nowMs);
   }
   ```
   และในกิ่ง "ไม่เจอมือ" (ที่เรียก `wd.pointer(false, …)`) เติม `wd.gun(false, 0f, 0f, 0f, false, nowMs);` — ถ้าไม่ส่งมือ เกมจะเล็งด้วยหัวเอง
5. **ลงทะเบียนใน NativeVRActivity** ตามบล็อก `sword3` (addWindow, `win.game = true`, `rend.world3 = …`), ปุ่มใน `tools`, คำสั่งเสียง, `hideWin`, และคำแปลใน `Tr.java`
   หน้าต่าง HUD = `NvContent` ที่แสดง `hud.bitmap()` + ปุ่มเริ่ม/ปิด (เขียน `NvGunWindow` ตามโค้ดหน้าต่างของ `NvSword3D` ตอนรวม)
6. **build.yml**: เพิ่มชื่อไฟล์ใหม่เข้ารายการ `rm -f` ของ safety net ชั้นแรก (และ `NvFruit/NvSword/NvSword3D` ที่ใช้ `NvContent` อยู่ด้วย — ตอนนี้ยังไม่อยู่ในรายการ)
7. ลบ `gundemo/` และ `NvGunGfx` (ถ้าใช้ `Nv3D`) แล้ว build
