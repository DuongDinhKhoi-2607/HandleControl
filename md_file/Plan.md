# Hand Control — Plan.md

## 1. Mục tiêu

Xây dựng ứng dụng Android **Hand Control** bằng **Flutter/Dart + Kotlin**, cho phép người dùng điều khiển điện thoại bằng cử chỉ tay từ xa mà không cần chạm màn hình.

Ví dụ:
- 👋 Swipe Left → Video trước
- 👋 Swipe Right → Video tiếp theo
- ✋ Open Palm → Play/Pause
- ☝️ Point Up → Tăng âm lượng
- 👇 Point Down → Giảm âm lượng

Nguyên tắc:
- Không cần đăng nhập.
- Không cần backend.
- Không cần Firebase.
- Ưu tiên AI xử lý trực tiếp trên thiết bị.
- Khi tắt Hand Control thì camera/AI/điều khiển từ xa phải dừng.

---

## 2. Công nghệ

### Flutter / Dart
Dùng cho:
- UI
- Settings
- Gesture Mapping
- Trạng thái ứng dụng
- Local configuration
- MethodChannel bridge

### Kotlin
Dùng cho Android System:
- CameraX
- MediaPipe
- Gesture Engine
- Foreground Service
- AccessibilityService
- AudioManager
- Android permissions

### AI
Ưu tiên:
- MediaPipe / MediaPipe Tasks
- On-device processing

Không gửi hình ảnh camera lên server nếu không cần thiết.

---

## 3. Kiến trúc

```text
                    Flutter / Dart
                         │
                         │ MethodChannel
                         ▼
                  Kotlin Android Core
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
       CameraX       MediaPipe      Gesture Engine
          │              │              │
          └──────────────┼──────────────┘
                         ▼
                    Action Mapper
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
    AccessibilityService       AudioManager
              │                     │
              └──────────┬──────────┘
                         ▼
                    Android System
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
       TikTok         YouTube       Facebook
```

**Nguyên tắc:** Flutter không xử lý camera frame qua MethodChannel. MethodChannel chỉ dùng để gửi command/configuration giữa Flutter và Kotlin.

---

## 4. Cấu trúc project

```text
hand_control/
│
├── lib/
│   ├── main.dart
│   ├── app/
│   │   └── app.dart
│   ├── screens/
│   │   ├── home_screen.dart
│   │   ├── gesture_screen.dart
│   │   ├── settings_screen.dart
│   │   └── permission_screen.dart
│   ├── models/
│   │   ├── gesture.dart
│   │   ├── action.dart
│   │   └── gesture_mapping.dart
│   ├── services/
│   │   ├── android_bridge.dart
│   │   ├── gesture_service.dart
│   │   └── settings_service.dart
│   └── core/
│       ├── constants.dart
│       └── enums.dart
│
├── android/
│   └── app/
│       └── src/main/kotlin/
│           └── ...
│               ├── MainActivity.kt
│               ├── HandControlAccessibilityService.kt
│               └── HandControlForegroundService.kt
│
├── assets/
└── pubspec.yaml
```

---

# 5. Phase 0 — Spike / Proof of Concept

**Chưa làm UI Flutter.**

Mục tiêu kiểm chứng 3 rủi ro lớn:

1. Camera + AI có thể hoạt động khi người dùng chuyển sang app khác không?
2. AccessibilityService có thể thực hiện swipe/tap trên app thật không?
3. AI có nhận diện bàn tay ổn ở khoảng cách thực tế không?

### 5.1 Tạo project test

Android Studio:

```text
Project: HandControlSpike
Language: Kotlin
Minimum SDK: Android 10 / API 29
```

Chạy trên **điện thoại Android thật**.

### 5.2 CameraX

```text
Camera
   ↓
Camera Preview
```

Checklist:

```text
[ ] Xin quyền camera
[ ] Camera trước hoạt động
[ ] Preview ổn định
[ ] Không crash
[ ] Test lifecycle
```

### 5.3 MediaPipe

```text
Camera
   ↓
MediaPipe
   ↓
21 Hand Landmarks
```

Debug:

```text
Hand detected: YES
Landmarks: 21
FPS: XX
```

Checklist:

```text
[ ] Nhận diện bàn tay
[ ] Vẽ landmarks
[ ] Test tay trái
[ ] Test tay phải
[ ] Test background
```

### 5.4 Test khoảng cách

```text
0.5m
1.0m
1.5m
```

Test ánh sáng tốt/yếu.

| Khoảng cách | Detect | FPS | Ổn định |
|---|---|---|---|
| 0.5m | ? | ? | ? |
| 1.0m | ? | ? | ? |
| 1.5m | ? | ? | ? |

---

# 6. AccessibilityService Spike

Chưa cần AI.

Tạo các nút:

```text
[ TEST SWIPE UP ]
[ TEST SWIPE DOWN ]
[ TEST SWIPE LEFT ]
[ TEST SWIPE RIGHT ]
[ TEST TAP ]
[ TEST BACK ]
```

Luồng:

```text
Button
   ↓
AccessibilityService
   ↓
dispatchGesture()
   ↓
Android
```

Test trực tiếp:
- TikTok
- YouTube Shorts
- Facebook Reels

Checklist:

```text
[ ] Swipe
[ ] Tap
[ ] Back
[ ] Không crash
```

---

# 7. Volume Spike

Dùng Android AudioManager.

```text
[ VOLUME + ]
[ VOLUME - ]
```

Checklist:

```text
[ ] Volume +
[ ] Volume -
[ ] Không tăng/giảm liên tục ngoài ý muốn
```

---

# 8. Foreground Service Spike

```text
Hand Control
      ↓
Foreground Service
      ↓
Mở TikTok
      ↓
Camera / AI vẫn hoạt động
```

Checklist:

```text
[ ] Notification
[ ] Service không bị dừng ngay
[ ] Camera lifecycle ổn
[ ] Không crash
[ ] Có thể Stop Service
```

---

# 9. Definition of Done — Phase 0

Phase 0 PASS khi:

```text
[ ] Camera chạy ổn định
[ ] MediaPipe nhận diện tay
[ ] 21 landmarks hoạt động
[ ] Nhận diện ở khoảng cách thực tế
[ ] Accessibility swipe hoạt động
[ ] Accessibility tap hoạt động
[ ] Back hoạt động
[ ] Volume + hoạt động
[ ] Volume - hoạt động
[ ] Foreground Service hoạt động
[ ] Chuyển sang TikTok vẫn test được
[ ] Chuyển sang YouTube vẫn test được
[ ] Không crash nghiêm trọng
```

PASS → Phase 1.  
FAIL → xác định nguyên nhân → sửa → test lại.

---

# 10. Phase 1 — Gesture Engine

Gesture tĩnh:

```text
OPEN_PALM
POINT_UP
POINT_DOWN
```

Gesture động:

```text
SWIPE_LEFT
SWIPE_RIGHT
```

---

# 11. Gesture State Machine

```text
IDLE
 ↓
TRACKING
 ↓
ARMED
 ↓
CONFIRMING
 ↓
EXECUTE
 ↓
COOLDOWN
 ↓
IDLE
```

Mục tiêu:
- Giảm false trigger.
- Không thực hiện một gesture nhiều lần.
- Phân biệt gesture thật và chuyển động ngẫu nhiên.

---

# 12. Swipe Detection

```text
Start Position
      ↓
Tracking
      ↓
Distance
      ↓
Velocity
      ↓
Direction
      ↓
Swipe Confirmed
```

Thông số cần tune:

```text
minDistance
minVelocity
maxDuration
cooldown
```

---

# 13. Confidence

```text
confidence < 0.80
→ Ignore

confidence >= 0.80
→ Process
```

Cho phép chỉnh trong Settings sau này.

---

# 14. Phase 2 — Action Mapper

```text
Gesture
   ↓
Action Mapper
   ↓
Action
```

MVP:

```text
SWIPE_RIGHT → NEXT
SWIPE_LEFT  → PREVIOUS
OPEN_PALM   → PLAY_PAUSE
POINT_UP    → VOLUME_UP
POINT_DOWN  → VOLUME_DOWN
```

Không hard-code riêng cho TikTok.

---

# 15. Phase 3 — Android Control

## AccessibilityService

```text
NEXT
PREVIOUS
PLAY_PAUSE
SWIPE_UP
SWIPE_DOWN
SWIPE_LEFT
SWIPE_RIGHT
CLICK
BACK
SCROLL_UP
SCROLL_DOWN
```

## AudioManager

```text
VOLUME_UP
VOLUME_DOWN
```

---

# 16. Phase 4 — Flutter Integration

Flutter:

```text
Home
Settings
Gesture Mapping
Status
Permission guidance
Local configuration
```

Kotlin:

```text
Camera
AI
Gesture Engine
Foreground Service
Accessibility
Audio
```

---

# 17. MethodChannel

Commands:

```text
startControl
stopControl
nextVideo
previousVideo
playPause
volumeUp
volumeDown
swipeUp
swipeDown
swipeLeft
swipeRight
back
```

Flutter:

```dart
await channel.invokeMethod('volumeUp');
```

Kotlin xử lý command tương ứng.

---

# 18. Phase 5 — Foreground Service hoàn chỉnh

Bật:

```text
OFF
 ↓
START
 ↓
Permission Check
 ↓
Foreground Service
 ↓
AI ACTIVE
 ↓
Accessibility ACTIVE
```

Tắt:

```text
OFF
 ↓
Stop AI
 ↓
Stop Camera
 ↓
Stop Control
 ↓
Update Notification
```

Notification phải có trạng thái rõ ràng và cách tắt nhanh.

---

# 19. Phase 6 — Flutter UI

## Home

```text
HAND CONTROL

● ACTIVE

Camera
ON

AI
ON

Accessibility
ON

[ TURN OFF ]

Gesture Settings
```

## OFF

```text
HAND CONTROL

○ OFF

[ TURN ON ]
```

## Gesture Settings

```text
Swipe Right
→ Next Video

Swipe Left
→ Previous Video

Open Palm
→ Play / Pause

Point Up
→ Volume +

Point Down
→ Volume -
```

---

# 20. Phase 7 — Local Settings

Lưu local:

```text
Gesture Mapping
Confidence
Cooldown
Sensitivity
Selected Camera
Enable/Disable Gesture
```

Không cần tài khoản.

---

# 21. Phase 8 — Testing

## AI

```text
[ ] 0.5m
[ ] 1.0m
[ ] 1.5m
[ ] Ánh sáng mạnh
[ ] Ánh sáng yếu
[ ] Tay trái
[ ] Tay phải
[ ] Background phức tạp
```

## Gesture

Đo:

```text
Accuracy
False Positive
False Negative
Response Time
False Trigger / Hour
```

## Android

```text
[ ] TikTok
[ ] YouTube
[ ] Facebook
[ ] Instagram
[ ] Chrome
```

---

# 22. Battery / Performance

Theo dõi:

```text
CPU
RAM
FPS
Battery
Temperature
```

Tối ưu:

```text
Camera resolution
Inference FPS
Model size
Frame skipping
Lifecycle
```

---

# 23. Android OEM Testing

Ưu tiên:

```text
Samsung
Xiaomi
OPPO
Vivo
```

Kiểm tra:

```text
[ ] Battery optimization
[ ] Background service kill
[ ] Camera restrictions
[ ] Accessibility behavior
[ ] Notification behavior
```

---

# 24. Privacy

Ưu tiên:

```text
Camera
   ↓
On-device AI
   ↓
Gesture
```

Không:

```text
Camera
   ↓
Internet
   ↓
Server
```

Không lưu ảnh/video camera nếu không cần thiết.

Phải giải thích rõ việc sử dụng camera và quyền Accessibility.

---

# 25. MVP Scope

```text
Flutter
+
Kotlin Android Core
+
CameraX
+
MediaPipe
+
5 Gestures
+
AccessibilityService
+
AudioManager
+
Foreground Service
+
Local Settings
```

Không cần:

```text
Login
Backend
Firebase
Cloud AI
Account
Social
```

---

# 26. MVP Definition of Done

```text
[ ] Cài app Android
[ ] Mở app
[ ] Bật Hand Control
[ ] Camera hoạt động
[ ] AI nhận diện tay
[ ] Swipe Right → Next
[ ] Swipe Left → Previous
[ ] Open Palm → Play/Pause
[ ] Point Up → Volume+
[ ] Point Down → Volume-
[ ] Chuyển sang TikTok
[ ] Gesture vẫn hoạt động
[ ] Chuyển sang YouTube
[ ] Gesture vẫn hoạt động
[ ] Tắt Hand Control
[ ] Camera/AI/control dừng
```

---

# 27. Roadmap thực tế

```text
PHASE 0
Android Kotlin Spike
        ↓
CameraX
        ↓
MediaPipe
        ↓
Accessibility
        ↓
AudioManager
        ↓
Foreground Service
        ↓
PASS / FAIL
```

```text
PHASE 1
Gesture Engine
        ↓
5 Gestures
        ↓
State Machine
        ↓
Cooldown
        ↓
Confidence
```

```text
PHASE 2
Action Mapper
        ↓
Gesture → Action
```

```text
PHASE 3
Android Control hoàn chỉnh
        ↓
Accessibility + Audio
```

```text
PHASE 4
Flutter
        ↓
UI + Settings + Mapping
```

```text
PHASE 5
Optimization
        ↓
Battery + FPS + OEM
```

```text
PHASE 6
Release
        ↓
Testing
        ↓
Privacy
        ↓
Store
```

---

# 28. Việc cần làm NGAY BÂY GIỜ

**Không làm toàn bộ project cùng lúc.**

### STEP 1
Tạo:

```text
HandControlSpike
```

bằng:

```text
Kotlin
Android Studio
```

### STEP 2
Chạy trên điện thoại Android thật.

### STEP 3
Làm CameraX.

Kết quả:

```text
Camera Preview
```

### STEP 4
Sau khi camera OK:

```text
MediaPipe
→ 21 landmarks
```

### STEP 5
Sau đó:

```text
AccessibilityService
```

### STEP 6
Cuối cùng:

```text
Flutter UI
```

**Không làm UI Flutter trước khi Phase 0 chứng minh được core Android hoạt động.**

---

# 29. Nguyên tắc

1. Kiểm chứng rủi ro trước khi làm UI.
2. Flutter/Dart là app layer.
3. Kotlin là Android system/core layer.
4. AI ưu tiên chạy local.
5. Không cần tài khoản.
6. Không cần backend.
7. Không gửi camera lên server.
8. Gesture phải có state machine.
9. Gesture phải có cooldown.
10. Phải đo false trigger.
11. Phải test trên điện thoại thật.
12. Phải test nhiều Android OEM.
13. Accessibility phải được dùng đúng mục đích và tuân thủ chính sách nền tảng.
14. Mỗi layer độc lập để dễ thay AI model về sau.

---

# 30. Mục tiêu cuối cùng

```text
Người dùng
    │
    │ 🖐️
    ▼
Camera
    │
    ▼
On-device AI
    │
    ▼
Gesture Engine
    │
    ▼
Action Mapper
    │
    ▼
Android System
    │
    ├── TikTok
    ├── YouTube
    ├── Facebook
    ├── Instagram
    └── App khác
```

> **Hand Control** là ứng dụng giúp người dùng tương tác với điện thoại bằng cử chỉ tay trong những tình huống không thể hoặc không tiện chạm vào màn hình.
