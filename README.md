# HandleControl — Điều khiển điện thoại bằng cử chỉ tay

Ứng dụng Android cho phép **điều khiển điện thoại bằng cử chỉ tay từ xa**, không cần chạm vào màn hình. Ví dụ: vuốt tay trong không khí để chuyển video TikTok / YouTube Shorts / Facebook Reels, xoè bàn tay để Play/Pause, chỉ tay lên/xuống để chỉnh âm lượng.

Toàn bộ AI chạy **trên thiết bị** (on-device), không cần đăng nhập, không backend, **không lưu và không gửi hình ảnh camera lên server**.

> 🚧 **Trạng thái:** đã có lõi Android chạy end-to-end (Phase 0 → 3): Camera → MediaPipe → Gesture Engine → Action Mapper → Accessibility / AudioManager, chạy nền bằng Foreground Service. **Cần test trên máy thật** trước khi sang Phase 4 (Flutter).

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

## Những gì đã có

App hiện có 3 tab:

### 0. Tab **Điều khiển** (mặc định)
- Nút **TURN ON / TURN OFF** Hand Control (Foreground Service, có notification với nút **Tắt**).
- Trạng thái Camera / AI / Accessibility, FPS, trạng thái state machine, pose đang giữ, gesture → action gần nhất.
- Bảng Gesture Mapping. Bật xong có thể chuyển sang TikTok / YouTube và điều khiển bằng tay.

### 1. Tab **Camera + AI**
- Xin quyền Camera (có màn hình giải thích và nút mở Cài đặt nếu bị từ chối).
- Preview camera trước full-screen.
- MediaPipe nhận diện bàn tay, vẽ **21 landmarks** và các đường nối xương tay đè lên preview.
- HUD debug: có phát hiện tay không, số landmarks, **FPS**, tay trái/phải + độ tin cậy, thời gian inference (ms).
- HUD Gesture Engine: state, pose, % giữ, tốc độ tay (palm/s), gesture gần nhất (chỉ nhận diện, không thực hiện action), dùng để tune ngưỡng.

### 2. Tab **Accessibility**
- Hiển thị trạng thái `HandControlAccessibilityService` (ON/OFF) và nút mở Cài đặt Accessibility.
- Nút test: **Swipe Up / Down / Left / Right**, **Scroll Up / Down**, **Tap**, **Back**.
- Volume Spike: **VOLUME +**, **VOLUME -**, **MEDIA PLAY / PAUSE** (AudioManager).
- Tuỳ chọn **delay 3 giây** để kịp chuyển sang TikTok / YouTube Shorts / Reels trước khi thao tác được thực hiện.

### Tiến độ
- [x] Phase 0, CameraX Spike: [md_file/01_Phase0_CameraX_Spike.md](md_file/01_Phase0_CameraX_Spike.md)
- [x] Phase 0, MediaPipe Hand Landmarks: [md_file/02_Phase0_MediaPipe_HandLandmarks.md](md_file/02_Phase0_MediaPipe_HandLandmarks.md)
- [x] Phase 0, AccessibilityService Spike: [md_file/03_Phase0_AccessibilityService_Spike.md](md_file/03_Phase0_AccessibilityService_Spike.md)
- [x] Phase 0, Volume + Foreground Service: [md_file/04_Phase0_Volume_ForegroundService_Spike.md](md_file/04_Phase0_Volume_ForegroundService_Spike.md)
- [x] Phase 1, Gesture Engine: [md_file/05_Phase1_Gesture_Engine.md](md_file/05_Phase1_Gesture_Engine.md)
- [x] Phase 2 + 3, Action Mapper + Android Control: [md_file/06_Phase2_3_ActionMapper_AndroidControl.md](md_file/06_Phase2_3_ActionMapper_AndroidControl.md)
- [ ] Test trên máy thật (khoảng cách, ánh sáng, TikTok / YouTube / Reels)
- [ ] Phase 4, Flutter

---

## Cấu trúc mã nguồn

```text
app/src/main/
├── AndroidManifest.xml
├── assets/
│   └── hand_landmarker.task           # Model MediaPipe Hand Landmarker
├── java/com/khoi/handlecontrol/
│   ├── MainActivity.kt                # 3 tab: Điều khiển / Camera + AI / Accessibility
│   ├── core/
│   │   ├── vision/HandLandmarkerHelper.kt      # Bọc MediaPipe HandLandmarker
│   │   ├── gesture/                   # Gesture Engine: classifier + state machine (Kotlin thuần)
│   │   └── action/                    # ActionMapper, ActionExecutor, VolumeController
│   ├── service/
│   │   ├── HandControlForegroundService.kt     # Camera + AI + gesture chạy nền
│   │   └── HandControlAccessibilityService.kt  # Swipe / Scroll / Tap / Back
│   ├── ui/control/ControlScreen.kt    # Tab Điều khiển
│   ├── spike/
│   │   ├── CameraPreviewScreen.kt     # CameraX + quyền camera + HUD debug
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
./gradlew testDebugUnitTest     # unit test Gesture Engine + Action Mapper
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

1. **Phase 0**: Spike / Proof of Concept *(code xong, chờ test máy thật)*
2. **Phase 1**: Gesture Engine (state machine, phát hiện swipe, ngưỡng confidence) *(xong)*
3. **Phase 2**: Action Mapper *(xong)*
4. **Phase 3**: Android Control (Accessibility + AudioManager) *(xong)*
5. **Phase 4**: Tích hợp Flutter qua MethodChannel
6. **Phase 5**: Foreground Service hoàn chỉnh
7. **Phase 6–8**: UI Flutter, lưu cấu hình, kiểm thử trên nhiều hãng máy

---

## Tác giả

**Dương Đình Khôi**: [@DuongDinhKhoi-2607](https://github.com/DuongDinhKhoi-2607)
