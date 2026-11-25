# Feature 1: Phase 0 — CameraX Preview Spike

**Ngày thực hiện:** 2026-11-25  
**Tham chiếu Plan:** `md_file/Plan.md` — Mục `5.2 CameraX` (Phase 0 — Spike / Proof of Concept)

---

## 1. Mục tiêu của Feature 1

Kiểm chứng bước đầu tiên trong Phase 0 trước khi tích hợp MediaPipe hay làm UI Flutter:
- Xin quyền Camera (`android.permission.CAMERA`) đúng chuẩn Android.
- Khởi chạy **CameraX Preview** với camera trước (`CameraSelector.DEFAULT_FRONT_CAMERA`).
- Xử lý lifecycle an toàn (`ProcessCameraProvider.bindToLifecycle`), tránh crash khi chuyển app hoặc quay lại từ Cài đặt hệ thống.

---

## 2. Các thay đổi đã thực hiện

### 2.1. Thêm thư viện CameraX
- **`gradle/libs.versions.toml`**:
  - Thêm version `camerax = "1.4.1"`.
  - Khai báo 4 thư viện: `androidx-camera-core`, `androidx-camera-camera2`, `androidx-camera-lifecycle`, `androidx-camera-view`.
- **`app/build.gradle.kts`**:
  - Thêm 4 dependencies CameraX vào block `dependencies`.

### 2.2. Khai báo quyền Camera trong Manifest
- **`app/src/main/AndroidManifest.xml`**:
  - Thêm `<uses-feature android:name="android.hardware.camera.any" android:required="false" />`.
  - Thêm `<uses-permission android:name="android.permission.CAMERA" />`.

### 2.3. Triển khai màn hình CameraX Spike
- **`app/src/main/java/com/khoi/handlecontrol/spike/CameraPreviewScreen.kt`**:
  - `CameraPreviewScreen`:
    - Tự động kiểm tra quyền `Manifest.permission.CAMERA` khi mở màn hình và re-check mỗi lần `ON_RESUME` (hỗ trợ người dùng cấp quyền từ Cài đặt rồi quay lại app).
    - Nếu chưa có quyền: hiển thị màn hình giải thích ngắn gọn (xử lý on-device, không lưu/gửi ảnh lên server) kèm nút **"Cấp quyền Camera"** và **"Mở Cài đặt ứng dụng"**.
    - Nếu đã có quyền: hiển thị `CameraXPreview` full-screen cùng overlay debug nhỏ ở phía trên hiển thị trạng thái (`FRONT` và trạng thái bind camera). App **chỉ dùng camera trước**, không có nút chuyển sang camera sau.
  - `CameraXPreview`:
    - Dùng `AndroidView` bọc `PreviewView` (`ImplementationMode.COMPATIBLE`, `ScaleType.FILL_CENTER`).
    - Dùng `ProcessCameraProvider.getInstance(context)` và `bindToLifecycle(lifecycleOwner, cameraSelector, preview)` trong `DisposableEffect`, chỉ dùng `CameraSelector.DEFAULT_FRONT_CAMERA` (nếu thiết bị không có camera trước thì báo *"Thiết bị không có camera trước"*, không fallback sang camera sau; bọc `try/catch` để báo lỗi thay vì crash).
    - Gọi `unbindAll()` trong `onDispose` để giải phóng camera sạch sẽ.
    - Dùng cờ `disposed`: nếu `onDispose` chạy trước khi `cameraProviderFuture` sẵn sàng (chuyển tab nhanh), listener sẽ bỏ qua, không bind camera sau khi màn hình đã bị huỷ.
- **`app/src/main/java/com/khoi/handlecontrol/MainActivity.kt`**:
  - Thay `Greeting` mặc định bằng `CameraPreviewScreen`.

---

## 3. Kết quả kiểm tra (Verification)

- **Build Debug APK**:
  - Lệnh: `.\gradlew.bat :app:assembleDebug` (với `JAVA_HOME="E:\Android Studio\jbr"`, OpenJDK 25.0.4).
  - Kết quả: **`BUILD SUCCESSFUL in 1m 8s`** (36 tasks executed).
- **Kiểm tra thiết bị ADB**:
  - Lệnh: `adb.exe devices`
  - Kết quả: Chưa có thiết bị Android thật nào kết nối qua USB/ADB tại thời điểm build (`List of devices attached` trống).

---

## 4. Checklist Phase 0 — 5.2 CameraX (cần test trên máy thật)

- [x] Khai báo quyền Camera & tích hợp CameraX build thành công
- [ ] Xin quyền camera trên điện thoại Android thật
- [ ] Camera trước hoạt động & preview ổn định
- [ ] Không crash khi thu nhỏ app / chuyển app khác / quay lại app (test lifecycle)

---

## 5. Bước tiếp theo

Sau khi cài đặt APK debug lên điện thoại Android thật và xác nhận các mục trong checklist trên hoạt động ổn định, ta sẽ chuyển sang **Feature 2: Phase 0 — MediaPipe Hand Landmarks (5.3)** để nhận diện 21 điểm bàn tay và đo FPS.

