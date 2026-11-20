# รวมเข้า PocketVR (ทำตอนที่โปรเจกต์หลักนิ่งแล้ว)

ค้นหาด้วยข้อความ ไม่ใช้เลขบรรทัด เพราะโปรเจกต์หลักเปลี่ยนอยู่เรื่อย ๆ

1. **ก๊อป 6 ไฟล์** `NvGameWorld, NvGunCore, NvGunHands, NvGunWorld, NvGunHud, NvGunGfx` ไปที่ `app/src/main/java/com/pocketvr/app/`
   (ถ้าอยากใช้ `Nv3D` ของเดิม: ลบ `NvGunGfx` แล้วแก้ชื่อคลาสใน `NvGunWorld` กับ `GunRenderer` — เมธอดเหมือนกัน แต่ต้องเปิด `Mesh`/`init()` ให้ public ถ้า host อยู่คนละแพ็กเกจ)
2. **VRRenderer**: เปลี่ยนฟิลด์ `public volatile NvSword3D world3` เป็น `NvGameWorld world3` และที่ใช้ตัวแปรท้องถิ่น `NvSword3D wd = world3;`
   (5 จุด: ฟิลด์, `glLost`, `draw3D`, `pointer(false…)`, `pointer(true…)`) เปลี่ยนเป็น `NvGameWorld wd`
3. **NvSword3D**: เติม `implements NvGameWorld` (เดิม `implements NvContent` ก็ยังอยู่) แล้วเพิ่มเมธอดว่าง 3 ตัว (ลายเซ็นตามนี้)
   `public void update(float[] rn,float x,float y,float z,long t){}`, `public void draw(float[] p,float[] rn,float x,float y,float z){}`,
   `public void hands(float[] d,float ar,float tH,float tV,boolean r180,long t){}`
   (`drawWorld`, `pointer`, `glLost`, `envActive` มีอยู่แล้ว ลายเซ็นตรงกัน)
4. **ส่งมือเข้าเกม** (โหมดมือ) ใน VRRenderer ตรงที่เรียก `wd.pointer(...)` ให้เติมบรรทัดเดียวต่อเฟรม:
   ```java
   if (wd != null) wd.hands(disp, ar, tanHc, tanVc, rot180, nowMs);   // disp = อาร์เรย์มือที่ renderer มีอยู่แล้ว (1 + n*63), ar = กว้าง/สูงของภาพกล้อง
   ```
   (ชื่อตัวแปรมาจากโค้ดตอนที่ผมอ่าน ปรับให้ตรงของจริง; ตอนไม่เห็นมือส่ง `null` หรือ `d[0]=0` ก็ได้ — **ต้องเรียกทุกเฟรม**)
   แล้วตั้ง `world.handMode = true` ใน NativeVRActivity ตอนเปิดเกม และเปิดตัวเลือก **สองมือ** ใน Settings (โหมดสองมือกินเครื่อง/ร้อนกว่ามือเดียว)
   ถ้าไม่ตั้ง `handMode` เกมจะเล่นแบบหัว (เล็งด้วยหัว ไกด้วยปุ่ม) เหมือนแอปเดี่ยว
5. **ลงทะเบียนใน NativeVRActivity** ตามบล็อก `sword3` (addWindow, `win.game = true`, `rend.world3 = …`), ปุ่มใน `tools`, คำสั่งเสียง, `hideWin`, และคำแปลใน `Tr.java`
   หน้าต่าง HUD = `NvContent` ที่แสดง `hud.bitmap()` + ปุ่มเริ่ม/ปิด (เขียน `NvGunWindow` ตามโค้ดหน้าต่างของ `NvSword3D` ตอนรวม)
6. **build.yml**: เพิ่มชื่อไฟล์ใหม่เข้ารายการ `rm -f` ของ safety net ชั้นแรก (และ `NvFruit/NvSword/NvSword3D` ที่ใช้ `NvContent` อยู่ด้วย — ตอนนี้ยังไม่อยู่ในรายการ)
7. ลบ `gundemo/` และ `NvGunGfx` (ถ้าใช้ `Nv3D`) แล้ว build

## เรื่องเดินในฉากตอนรวม
เกมวาดพื้น/ตารางกริด/เสาของตัวเอง (`sceneryOn`) เพราะนั่นคือสิ่งที่ทำให้เห็นว่าเดิน และ `envActive()` สลับห้องจริงเป็นฉากเกมตอนเล่นอยู่แล้ว
การเดินคือการเลื่อนตำแหน่งตาในเกม (`NvGunWorld.walkX/walkY`) ไม่ได้ขยับกล้อง ถ้าอยากใช้ `NvMotion` (เดินจริง) ในภายหลัง ให้ส่งตำแหน่งตาจริงเข้า `update()` เหมือนเดิม — จะรวมกับการเดินด้วยมือได้เอง
