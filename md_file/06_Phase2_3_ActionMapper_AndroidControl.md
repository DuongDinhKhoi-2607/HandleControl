# Feature 6: Phase 2 — Action Mapper + Phase 3 — Android Control

**Ngày thực hiện:** 2026-11-27
**Tham chiếu Plan:** `md_file/Plan.md` — Mục `14`, `15`, `19`

---

## 1. Action Mapper (`core/action/ActionMapper.kt`)

Ánh xạ `Gesture → ControlAction`, bất biến, đổi mapping bằng `with(gesture, action)` (chuẩn bị cho màn Gesture Mapping ở Phase 4/7).

> **Đã đổi mapping ngày 2026-11-28**, xem `08_Thiet_Ke_Lai_Cu_Chi.md`.

| Gesture | Action mặc định |
|---|---|
| Swipe Up | `NEXT` |
| Swipe Down | `PREVIOUS` |
| Swipe Left | `SWIPE_LEFT` (kéo màn hình sang trái) |
| Swipe Right | `SWIPE_RIGHT` (kéo màn hình sang phải) |
| Thumb Up (Like) | `DOUBLE_TAP` |
| Thumb Left | `BACK` |
| Fist (nắm tay) | `PLAY_PAUSE` |
| Open Palm (xòe tay) | không làm gì, chỉ dùng để vuốt |
| Point Up | `VOLUME_UP` |
| Point Down | `VOLUME_DOWN` |

Không hard-code theo app: `NEXT` = vuốt lên, `PREVIOUS` = vuốt xuống, `PLAY_PAUSE` = chạm giữa màn hình. Cách này chạy được trên TikTok, YouTube Shorts, Facebook Reels, Instagram Reels. Ai cần phím media thật thì map sang `MEDIA_PLAY_PAUSE`.

## 2. Android Control (`core/action/ActionExecutor.kt`)

Đầy đủ danh sách action ở Plan mục 15:

| Action | Thực hiện bằng |
|---|---|
| `NEXT`, `SWIPE_UP` | Accessibility: vuốt 75% → 25% chiều cao |
| `PREVIOUS`, `SWIPE_DOWN` | Accessibility: vuốt 25% → 75% |
| `SWIPE_LEFT` / `SWIPE_RIGHT` | Accessibility: vuốt ngang 80% ↔ 20% |
| `PLAY_PAUSE`, `CLICK` | Accessibility: chạm giữa màn hình |
| `BACK` | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| `SCROLL_UP` / `SCROLL_DOWN` | Accessibility: kéo ngắn (35% ↔ 65%), chậm hơn (450 ms) |
| `VOLUME_UP` / `VOLUME_DOWN` | `AudioManager` |
| `MEDIA_PLAY_PAUSE` | `AudioManager.dispatchMediaKeyEvent` |

- Action luôn được post lên main thread (kết quả MediaPipe đến từ thread nền).
- Accessibility OFF thì action vuốt/chạm báo lỗi rõ ràng, còn Volume vẫn hoạt động.

## 3. Luồng hoàn chỉnh

```text
CameraX (ImageAnalysis, camera trước)
   ↓
MediaPipe HandLandmarker (21 landmarks)
   ↓
GestureEngine (state machine)
   ↓  GestureEvent
ActionMapper
   ↓  ControlAction
ActionExecutor ──▶ AccessibilityService / AudioManager
   ↓
TikTok / YouTube / Facebook / ...
```

Tất cả chạy trong `HandControlForegroundService`.

## 4. UI tạm (Compose) — tab **Điều khiển**

Màn hình mặc định khi mở app, theo bố cục Plan mục 19:
- `HAND CONTROL`, `● ACTIVE` / `○ OFF`, nút `TURN ON` / `TURN OFF`.
- Trạng thái Camera / AI / Accessibility (kèm nút mở Cài đặt Accessibility nếu OFF).
- Live: tay, FPS, state, pose + thanh tiến độ giữ, gesture → action gần nhất, kết quả, số gesture đã chạy.
- Bảng Gesture Mapping.
- Khi bấm TURN ON: xin quyền Camera (bắt buộc) + Notification (Android 13+, tuỳ chọn).

UI này sẽ được thay bằng Flutter ở Phase 4; Flutter chỉ cần gọi `startControl` / `stopControl` và đọc `ControlStatus`.

## 5. Kiểm thử

- `ActionMapperTest`: 4 test pass (mapping mặc định, đổi mapping, gesture chưa map = `NONE`, volume không cần Accessibility).
- Build `assembleDebug` OK, `lintDebug` không lỗi.

## 6. Checklist MVP cần test trên máy thật (Plan mục 26)

- [ ] Bật Hand Control → Camera + AI ON
- [ ] Swipe Right → Next, Swipe Left → Previous
- [ ] Fist (nắm tay) → Play/Pause; Open Palm không làm gì
- [ ] Point Up → Volume +, Point Down → Volume -
- [ ] Chuyển sang TikTok: gesture vẫn hoạt động
- [ ] Chuyển sang YouTube: gesture vẫn hoạt động
- [ ] Tắt Hand Control → Camera/AI/control dừng

## 7. Việc tiếp theo

- **Phase 4 — Flutter**: tạo app Flutter, đưa code Kotlin hiện tại vào `android/` làm lõi, MethodChannel (`startControl`, `stopControl`, `volumeUp`...) + EventChannel cho `ControlStatus`.
- **Phase 7**: lưu `GestureConfig` + mapping (DataStore / SharedPreferences).
- **Tối ưu**: nâng MediaPipe lên bản có thư viện native căn 16 KB (lint `Aligned16KB` với 0.10.14, cần cho máy Android 15+ dùng page 16 KB), đo CPU / pin / nhiệt độ, giảm FPS suy luận khi không thấy tay.
