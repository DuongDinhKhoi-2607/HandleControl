# HandleControl — Điều khiển điện thoại bằng cử chỉ tay

Ứng dụng Android cho phép **điều khiển điện thoại bằng cử chỉ tay từ xa**, không cần chạm vào màn hình. Ví dụ: vuốt tay trong không khí để chuyển video TikTok / YouTube Shorts / Facebook Reels, xoè bàn tay để Play/Pause, chỉ tay lên/xuống để chỉnh âm lượng.

Toàn bộ AI chạy **trên thiết bị** (on-device), không cần đăng nhập, không backend, **không lưu và không gửi hình ảnh camera lên server**.

> 🚧 **Trạng thái:** đang ở **Phase 0: Spike / Proof of Concept**, kiểm chứng các rủi ro kỹ thuật lớn trước khi xây app hoàn chỉnh.

---

## Ý tưởng cử chỉ (mục tiêu MVP)

| Cử chỉ | Hành động |
|---|---|
| 👋 Swipe Left | Video trước |
| 👋 Swipe Right | Video tiếp theo |
| ✋ Open Palm | Play / Pause |
| ☝️ Point Up | Tăng âm lượng |
| 👇 Point Down | Giảm âm lượng |

---

## Công nghệ

- **Kotlin** + **Jetpack Compose** (Material 3)
- **CameraX** (`camera-core`, `camera2`, `lifecycle`, `view`): chỉ dùng camera trước
- **MediaPipe Tasks Vision**: `HandLandmarker` nhận diện 21 điểm khớp bàn tay (chế độ `LIVE_STREAM`, CPU)
- **AccessibilityService**: thực hiện Swipe / Tap / Back trên toàn hệ thống
- Android Gradle Plugin 9, `compileSdk` / `targetSdk` 37, `minSdk` 29 (Android 10+)

Theo kế hoạch, giao diện chính sẽ chuyển sang **Flutter**, còn phần Kotlin giữ vai trò lõi Android (camera, AI, gesture engine, service). Xem [md_file/Plan.md](md_file/Plan.md).

---

## Phase 0: những gì đã có

App hiện có 2 tab để test:

### 1. Tab **Camera + AI**
- Xin quyền Camera (có màn hình giải thích và nút mở Cài đặt nếu bị từ chối).
- Preview camera trước full-screen.
- MediaPipe nhận diện bàn tay, vẽ **21 landmarks** và các đường nối xương tay đè lên preview.
- HUD debug: có phát hiện tay không, số landmarks, **FPS**, tay trái/phải + độ tin cậy, thời gian inference (ms).

### 2. Tab **Accessibility**
- Hiển thị trạng thái `HandControlAccessibilityService` (ON/OFF) và nút mở Cài đặt Accessibility.
- 6 nút test: **Swipe Up / Down / Left / Right**, **Tap**, **Back**.
- Tuỳ chọn **delay 3 giây** để kịp chuyển sang TikTok / YouTube Shorts / Reels trước khi thao tác được thực hiện.

### Tiến độ Phase 0
- [x] CameraX Spike: [md_file/01_Phase0_CameraX_Spike.md](md_file/01_Phase0_CameraX_Spike.md)
- [x] MediaPipe Hand Landmarks: [md_file/02_Phase0_MediaPipe_HandLandmarks.md](md_file/02_Phase0_MediaPipe_HandLandmarks.md)
- [x] AccessibilityService Spike: [md_file/03_Phase0_AccessibilityService_Spike.md](md_file/03_Phase0_AccessibilityService_Spike.md)
- [ ] Volume Spike (`AudioManager`)
- [ ] Foreground Service Spike (camera + AI chạy khi đang ở app khác)

---

## Cấu trúc mã nguồn

```text
app/src/main/
├── AndroidManifest.xml
├── assets/
│   └── hand_landmarker.task           # Model MediaPipe Hand Landmarker
├── java/com/khoi/handlecontrol/
│   ├── MainActivity.kt                # 2 tab: Camera + AI / Accessibility
│   ├── service/
│   │   └── HandControlAccessibilityService.kt   # Swipe / Tap / Back
│   ├── spike/
│   │   ├── CameraPreviewScreen.kt     # CameraX + quyền camera + HUD
│   │   ├── HandLandmarkerHelper.kt    # Bọc MediaPipe HandLandmarker
│   │   ├── HandLandmarkOverlay.kt     # Vẽ 21 landmarks
│   │   └── AccessibilitySpikeScreen.kt
│   └── ui/theme/
└── res/xml/
    └── hand_control_accessibility_service.xml
md_file/                                # Kế hoạch & ghi chú từng bước
```

---

## Cách chạy

**Yêu cầu:** Android Studio bản mới (hỗ trợ AGP 9), JDK 17+, **điện thoại Android thật** (Android 10 trở lên).

> ⚠️ MediaPipe không có thư viện native cho emulator **x86_64**, nên tab Camera + AI sẽ báo *"MediaPipe init failed"* trên máy ảo. Hãy test trên máy thật.

```bash
git clone https://github.com/DuongDinhKhoi-2607/HandleControl.git
cd HandleControl
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
./gradlew installDebug         # cài lên điện thoại đang cắm USB
```

Hoặc mở thư mục bằng Android Studio rồi bấm **Run**.

### Bật Accessibility Service
1. Mở tab **Accessibility**, bấm **"Mở Cài đặt Accessibility"**.
2. Chọn **Hand Control Gesture Service** rồi bật lên.
3. Trên Android 13+, nếu cài APK ngoài Play Store mà công tắc bị mờ: vào *Cài đặt → Ứng dụng → HandleControl → ⋮ → "Cho phép cài đặt bị hạn chế"*, sau đó bật lại.

---

## Quyền riêng tư

- Hình ảnh camera chỉ được xử lý **trong bộ nhớ, trên thiết bị**; không lưu, không gửi đi đâu.
- Accessibility Service chỉ dùng để **thực hiện thao tác** (vuốt/chạm/quay lại), **không đọc nội dung màn hình**.
- Không đăng nhập, không backend, không Firebase.

---

## Lộ trình

Chi tiết trong [md_file/Plan.md](md_file/Plan.md):

1. **Phase 0**: Spike / Proof of Concept *(đang làm)*
2. **Phase 1**: Gesture Engine (state machine, phát hiện swipe, ngưỡng confidence)
3. **Phase 2**: Action Mapper
4. **Phase 3**: Android Control (Accessibility + AudioManager)
5. **Phase 4**: Tích hợp Flutter qua MethodChannel
6. **Phase 5**: Foreground Service hoàn chỉnh
7. **Phase 6–8**: UI Flutter, lưu cấu hình, kiểm thử trên nhiều hãng máy

---

## Tác giả

**Dương Đình Khôi**: [@DuongDinhKhoi-2607](https://github.com/DuongDinhKhoi-2607)
