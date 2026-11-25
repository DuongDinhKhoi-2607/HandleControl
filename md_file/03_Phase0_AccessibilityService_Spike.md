# Feature 3: Phase 0 — AccessibilityService Spike

**Ngày thực hiện:** 2026-11-25  
**Tham chiếu Plan:** `md_file/Plan.md` — Mục `6. AccessibilityService Spike` (Phase 0 — Spike / Proof of Concept)

---

## 1. Mục tiêu của Feature 3

Kiểm chứng khả năng thực thi thao tác điều khiển hệ thống (`dispatchGesture()` và `performGlobalAction()`) thông qua Android `AccessibilityService` — chưa cần kết nối AI:
- Tạo 6 nút test: `TEST SWIPE UP`, `TEST SWIPE DOWN`, `TEST SWIPE LEFT`, `TEST SWIPE RIGHT`, `TEST TAP`, `TEST BACK`.
- Hỗ trợ cơ chế **Delay 3s** để người dùng bấm nút test rồi chuyển nhanh sang **TikTok**, **YouTube Shorts**, hoặc **Facebook Reels** để kiểm chứng cử chỉ hoạt động trên app thật.

---

## 2. Các thay đổi đã thực hiện

### 2.1. Cấu hình `AccessibilityService` trong Manifest & XML
- **`app/src/main/res/xml/hand_control_accessibility_service.xml`**:
  - Bật `android:canPerformGestures="true"` để cho phép gọi `dispatchGesture()`.
  - Cấu hình `accessibilityEventTypes="typeWindowStateChanged"`, `accessibilityFeedbackType="feedbackGeneric"`, `notificationTimeout="100"`, và `description="@string/accessibility_service_description"`.
- **`app/src/main/res/values/strings.xml`**:
  - Thêm `accessibility_service_label` (`Hand Control Gesture Service`) và `accessibility_service_description` giải thích rõ mục đích sử dụng quyền Accessibility (không thu thập nội dung màn hình hay dữ liệu cá nhân).
- **`app/src/main/AndroidManifest.xml`**:
  - Đăng ký `<service android:name=".service.HandControlAccessibilityService" ...>` với quyền `android.permission.BIND_ACCESSIBILITY_SERVICE`, `android:exported="false"`, và `<meta-data android:name="android.accessibilityservice" android:resource="@xml/hand_control_accessibility_service" />`.

### 2.2. Triển khai `HandControlAccessibilityService`
- **`app/src/main/java/com/khoi/handlecontrol/service/HandControlAccessibilityService.kt`**:
  - Quản lý trạng thái kết nối qua `StateFlow<Boolean>` (`isConnected`) và `instance` singleton, cập nhật trong `onServiceConnected()`, `onUnbind()`, `onDestroy()`.
  - Triển khai các hàm điều khiển dựa trên kích thước màn hình thực tế (`resources.displayMetrics`):
    - `performSwipeUp` (từ 75% lên 25% chiều cao màn hình, duration `280ms`)
    - `performSwipeDown` (từ 25% xuống 75% chiều cao màn hình)
    - `performSwipeLeft` (từ 80% sang 20% chiều ngang màn hình)
    - `performSwipeRight` (từ 20% sang 80% chiều ngang màn hình)
    - `performTap` (chạm tại tâm màn hình, duration `80ms`)
    - `performBackAction` (gọi `performGlobalAction(GLOBAL_ACTION_BACK)`)
  - Bọc `try/catch` và `GestureResultCallback` (`onCompleted` / `onCancelled`) để log kết quả và tránh crash.

### 2.3. Giao diện Spike & Điều hướng Tab
- **`app/src/main/java/com/khoi/handlecontrol/spike/AccessibilitySpikeScreen.kt`**:
  - Hiển thị trạng thái kết nối `Accessibility Service: ON (Connected) / OFF` kèm nút **"Mở Cài đặt Accessibility"** (`Settings.ACTION_ACCESSIBILITY_SETTINGS`).
  - Switch **"Delay 3s trước khi thực hiện"** (mặc định bật) để có 3 giây chuyển sang TikTok / YouTube Shorts / Facebook Reels trước khi cử chỉ được bắn ra.
  - 6 nút test theo đúng `Plan.md` mục 6 và khung `Status Log` hiển thị đếm ngược / kết quả thực thi.
- **`app/src/main/java/com/khoi/handlecontrol/MainActivity.kt`**:
  - Thêm `PrimaryTabRow` với 2 tab: **Camera + AI** (`CameraPreviewScreen`) và **Accessibility** (`AccessibilitySpikeScreen`). Khi chuyển sang tab Accessibility, CameraX/MediaPipe tự động dispose sạch sẽ.

---

## 3. Kết quả kiểm tra (Verification)

- **Build Debug APK**:
  - Lệnh: `.\gradlew.bat :app:assembleDebug` (với `JAVA_HOME="E:\Android Studio\jbr"`).
  - Kết quả: **`BUILD SUCCESSFUL in 55s`** (36 tasks executed).

---

## 4. Checklist Phase 0 — 6. AccessibilityService Spike (cần test trên máy ảo / máy thật)

- [x] Khai báo service, XML config & build APK thành công
- [ ] Bật `Hand Control Gesture Service` trong Accessibility Settings (trạng thái chuyển sang `ON (Connected)`)
- [ ] Test `Swipe Up / Down / Left / Right` (thử trên TikTok / YouTube Shorts / Facebook Reels với Delay 3s)
- [ ] Test `Tap`
- [ ] Test `Back`
- [ ] Không crash

---

## 5. Bước tiếp theo

Sau khi bạn test xong tab **Accessibility** (trên máy ảo hoặc máy thật), ta sẽ chuyển sang **Feature 4: Phase 0 — Volume Spike (mục 7 trong Plan.md)** dùng Android `AudioManager` (`VOLUME +` / `VOLUME -`).

