# Feature 2: Phase 0 — MediaPipe Hand Landmarks (21 điểm + FPS)

**Ngày thực hiện:** 2026-11-25  
**Tham chiếu Plan:** `md_file/Plan.md` — Mục `5.3 MediaPipe` & `5.4 Test khoảng cách` (Phase 0 — Spike / Proof of Concept)

---

## 1. Mục tiêu của Feature 2

Tích hợp **MediaPipe Tasks Vision** (`HandLandmarker`) chạy hoàn toàn on-device trên luồng frame từ CameraX để:
- Nhận diện bàn tay theo thời gian thực (`RunningMode.LIVE_STREAM`).
- Trích xuất **21 Hand Landmarks** và vẽ trực tiếp lên màn hình Preview.
- Hiển thị thông số debug: `Hand detected (YES/NO)`, `Landmarks (21)`, `FPS`, `Handedness (Left/Right)`, `Score`, `Inference time (ms)`.
- Chuẩn bị để người dùng test thực tế với tay trái, tay phải, các khoảng cách `0.5m`, `1.0m`, `1.5m` và điều kiện ánh sáng tốt/yếu.

---

## 2. Các thay đổi đã thực hiện

### 2.1. Thêm thư viện MediaPipe & Model AI
- **`gradle/libs.versions.toml`**:
  - Thêm version `mediapipeTasksVision = "0.10.14"`.
  - Khai báo `mediapipe-tasks-vision = { group = "com.google.mediapipe", name = "tasks-vision", version.ref = "mediapipeTasksVision" }`.
- **`app/build.gradle.kts`**:
  - Thêm `implementation(libs.mediapipe.tasks.vision)`.
  - Thêm `androidResources { noCompress += "task" }` để file model `.task` không bị nén trong APK.
- **`app/src/main/assets/hand_landmarker.task`**:
  - Tải model chính thức từ Google MediaPipe (`7,819,105` bytes).

### 2.2. Triển khai `HandLandmarkerHelper`
- **`app/src/main/java/com/khoi/handlecontrol/spike/HandLandmarkerHelper.kt`**:
  - Khởi tạo `HandLandmarker` ở chế độ `RunningMode.LIVE_STREAM` với `Delegate.CPU`, `numHands = 1`, `minHandDetectionConfidence = 0.5f`, `minHandPresenceConfidence = 0.5f`, `minTrackingConfidence = 0.5f`.
  - `detectLiveStream(imageProxy)` (chỉ camera trước):
    - Đọc buffer `RGBA_8888` từ CameraX `ImageAnalysis`, copy theo đúng `rowStride` / `pixelStride` (bỏ byte đệm cuối mỗi dòng mà nhiều máy Samsung/Xiaomi thêm vào) để ảnh không bị méo hoặc lỗi *"Buffer not large enough"*.
    - Xoay ảnh theo `imageProxy.imageInfo.rotationDegrees` và luôn lật ngang (mirror) bằng `Canvas` + `Matrix` để tọa độ landmark khớp với `PreviewView` của camera trước.
    - **Tái sử dụng bitmap**: chỉ giữ 2 bitmap (ảnh gốc + ảnh đã xoay/lật), chỉ tạo lại khi kích thước frame thay đổi, giúp giảm áp lực GC (MediaPipe copy pixel ngay trong `detectAsync()` nên tái sử dụng là an toàn).
    - Gọi `landmarker.detectAsync(mpImage, frameTimeMs)` và luôn đóng `imageProxy.close()` trong `finally`.
  - **Thread-safety**: `detectLiveStream()` (luồng phân tích) và `close()` (main thread) dùng chung `lock` + cờ `closed`, nên không gọi `detectAsync` trên landmarker đã đóng, đồng thời bỏ qua kết quả/lỗi trả về muộn sau khi đóng.
  - `handleResult(result)`:
    - Tính FPS thực tế mỗi giây, thời gian inference (`ms`), số lượng landmarks, `handedness` (Left/Right) và confidence score, trả về qua `HandDetectionState`.

### 2.3. Vẽ 21 Landmarks & tích hợp vào CameraX
- **`app/src/main/java/com/khoi/handlecontrol/spike/HandLandmarkOverlay.kt`**:
  - Dùng Compose `Canvas` vẽ 21 điểm khớp tay (đầu ngón tay màu vàng, các khớp còn lại màu đỏ) và các đường nối xương bàn tay (`HAND_CONNECTIONS`, màu xanh lá).
  - Áp dụng scale & offset theo `PreviewView.ScaleType.FILL_CENTER` để khung xương khớp với hình ảnh bàn tay trên màn hình.
- **`app/src/main/java/com/khoi/handlecontrol/spike/CameraPreviewScreen.kt`**:
  - Gắn thêm `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, `OUTPUT_IMAGE_FORMAT_RGBA_8888`) chạy trên `SingleThreadExecutor` cùng với `Preview` vào `ProcessCameraProvider.bindToLifecycle`.
  - Hiển thị `HandLandmarkOverlay` phủ lên `PreviewView`.
  - Cập nhật HUD debug theo `Plan.md` mục 5.3 (`Hand detected`, `Landmarks`, `FPS`, `Hand`, `Inference ms`).
  - Dọn dẹp `analysisExecutor.shutdown()` và `landmarkerHelper.close()` trong `onDispose`.

---

## 3. Kết quả kiểm tra (Verification)

- **Build Debug APK**:
  - Lệnh: `.\gradlew.bat :app:assembleDebug` (với `JAVA_HOME="E:\Android Studio\jbr"`).
  - Kết quả: **`BUILD SUCCESSFUL in 1m 27s`** (36 tasks executed).
  - Ghi chú: có 1 compiler warning nhỏ về `handednesses()` deprecated ở `HandLandmarkerHelper.kt:160` (vẫn hoạt động bình thường).
- **Ghi nhận lỗi trên Android Emulator (`Pixel_7`, `x86_64`)**:
  - **Nguyên nhân crash**: `mediapipe:tasks-vision:0.10.14` trong `app-debug.apk` chỉ có `libmediapipe_tasks_vision_jni.so` cho `arm64-v8a`, `armeabi-v7a`, và `x86` — **không có cho `x86_64`**. Máy ảo `Pixel_7` chạy `x86_64` nên ném `UnsatisfiedLinkError` khi khởi tạo `HandLandmarker`.
  - **Cách xử lý**: Đã cập nhật `HandLandmarkerHelper.kt` để `catch (t: Throwable)` (bắt cả `UnsatisfiedLinkError`), giúp app không bị crash trên máy ảo `x86_64` và hiển thị rõ lỗi trên HUD, đồng thời xác nhận cần test MediaPipe trên **điện thoại Android thật (`arm64-v8a`)** theo đúng `Plan.md`.

---

## 4. Checklist Phase 0 — 5.3 & 5.4 (cần test trên máy thật)

- [x] Tích hợp MediaPipe `hand_landmarker.task` & build APK thành công
- [ ] Nhận diện bàn tay (`Hand detected: YES`, `Landmarks: 21`)
- [ ] Vẽ 21 landmarks khớp với bàn tay trên preview
- [ ] Test tay trái & tay phải
- [ ] Test nền phức tạp (background)
- [ ] Test khoảng cách: `0.5m` / `1.0m` / `1.5m` (ánh sáng tốt & yếu), ghi nhận FPS

---

## 5. Bước tiếp theo

Sau khi bạn chạy thử trên điện thoại thật và kiểm tra nhận diện tay + FPS ở các khoảng cách, ta sẽ chuyển sang **Feature 3: Phase 0 — AccessibilityService Spike (mục 6 trong Plan.md)** để kiểm chứng việc tự động `Swipe / Tap / Back` trên TikTok, YouTube Shorts, Facebook Reels.

