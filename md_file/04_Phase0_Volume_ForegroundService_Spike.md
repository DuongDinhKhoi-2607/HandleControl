# Feature 4: Phase 0 — Volume Spike + Foreground Service Spike

**Ngày thực hiện:** 2026-11-27
**Tham chiếu Plan:** `md_file/Plan.md` — Mục `7. Volume Spike`, `8. Foreground Service Spike`, `18. Phase 5`

---

## 1. Volume Spike (`AudioManager`)

- **`core/action/VolumeController.kt`**
  - `volumeUp()` / `volumeDown()`: `adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE/LOWER, FLAG_SHOW_UI)`. Mỗi lần gọi đúng **1 nấc**, nên một cử chỉ không thể tăng/giảm liên tục.
  - `mediaPlayPause()`: gửi phím `KEYCODE_MEDIA_PLAY_PAUSE` (DOWN + UP) qua `dispatchMediaKeyEvent`, cho các app nghe phím media (YouTube, Spotify...).
  - Bắt `SecurityException` khi chế độ Không làm phiền chặn đổi âm lượng.
  - Không cần quyền Accessibility.
- **Tab Accessibility** có thêm: `VOLUME +`, `VOLUME -`, `TEST MEDIA PLAY / PAUSE` (có delay 3s), `TEST SCROLL UP/DOWN`.

## 2. Foreground Service (`HandControlForegroundService`)

- Kế thừa `LifecycleService` (thêm dependency `androidx.lifecycle:lifecycle-service`) để bind CameraX vào vòng đời service, **chỉ `ImageAnalysis`, không Preview**: frame chỉ nằm trong bộ nhớ.
- Manifest: `foregroundServiceType="camera"`, quyền `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `POST_NOTIFICATIONS`.
- Luồng bật: kiểm tra quyền Camera → `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_CAMERA)` → bind camera → MediaPipe → Gesture Engine → Action Executor.
- Luồng tắt (đúng thứ tự Plan mục 18): đóng MediaPipe → gỡ analyzer + `unbind(analysis)` → tắt executor → reset engine → gỡ notification → `stopSelf()`.
- Notification cố định: tiêu đề `Hand Control: ĐANG BẬT`, dòng trạng thái `Gần nhất: Swipe Right → Video tiếp theo`, nút **Tắt** ngay trên notification, chạm vào để mở app.
- `START_NOT_STICKY`: Android không cho camera FGS khởi động lại từ nền, nên không tự restart.
- Trạng thái được expose qua `HandControlForegroundService.status: StateFlow<ControlStatus>` cho UI.

### Lưu ý quan trọng
- Service **phải được bật khi app đang hiển thị** (giới hạn "while-in-use" của Android 11+ với camera).
- `ProcessCameraProvider` dùng chung cả process: khi service chạy, tab **Camera + AI** không bind camera mà hiện thông báo + nút tắt, để không giành camera của service.

## 3. Checklist (cần test trên máy thật)

- [x] Build APK thành công, lint không lỗi
- [ ] Volume + / Volume - đổi đúng 1 nấc, có thanh âm lượng hệ thống
- [ ] Notification hiện khi bật, có nút Tắt
- [ ] Mở TikTok / YouTube: camera + AI vẫn chạy (FPS trong tab Điều khiển khi quay lại vẫn > 0, gesture vẫn thực hiện)
- [ ] Tắt từ notification: camera tắt (đèn báo camera / chỉ báo quyền riêng tư tắt)
- [ ] Không crash khi bật/tắt nhiều lần liên tiếp
