# Feature 9: Chỉ hoạt động trong app đã chọn

**Ngày thực hiện:** 2026-11-28

---

## 1. Mục tiêu

Hand Control chỉ nhận cử chỉ khi đang mở **app đã chọn** (TikTok, YouTube, ...). Ở **màn hình Home** hoặc app khác thì không làm gì, tránh lỡ tay vuốt / Back ngoài ý muốn.

## 2. Cách dùng

1. Tab **Điều khiển** → thẻ **"Chỉ hoạt động trong app đã chọn"** (mặc định **bật**).
2. Bấm **Chọn app** → tick các app muốn dùng cử chỉ. Mặc định chọn sẵn TikTok, YouTube, Instagram, Facebook (nếu có cài).
3. **Bắt buộc bật Accessibility**: app dựa vào Accessibility để biết đang mở app nào. Chưa bật thì cử chỉ bị tạm dừng.
4. TURN ON như bình thường. Trên thẻ có dòng "App đang mở: ..." và "● Đang điều khiển" / "○ Tạm dừng". Thông báo cũng hiện lý do tạm dừng.

Tắt công tắc thì cử chỉ hoạt động ở mọi nơi như trước.

## 3. Cách hoạt động

| Phần | File | Việc làm |
|---|---|---|
| Biết app đang mở | `service/HandControlAccessibilityService.kt` | Nghe `TYPE_WINDOW_STATE_CHANGED`. Chỉ tính là **đổi app** khi cửa sổ mới là một **Activity**. Bỏ qua System UI (kéo thanh thông báo, bảng âm lượng) và bàn phím. Popup / dialog trong app không đổi trạng thái. Kết quả: `foregroundPackage` (StateFlow) |
| Quyết định | `core/app/AppGate.kt` | `ACTIVE` / `NOT_SELECTED_APP` / `UNKNOWN_APP` từ: công tắc, app đang mở, danh sách đã chọn |
| Lưu cài đặt | `core/app/ControlSettings.kt` | SharedPreferences `control_settings`: công tắc + danh sách package |
| Chặn | `service/HandControlForegroundService.kt` | Mỗi frame camera: nếu không `ACTIVE` thì **bỏ frame trước MediaPipe** (không chạy AI, đỡ tốn pin), reset gesture engine để cử chỉ dở dang không bắn khi quay lại. Kiểm tra lần nữa ngay trước khi thực thi action |
| Giao diện | `ui/control/AppFilterSection.kt` | Công tắc, app đang mở, trạng thái, hộp thoại chọn app (các app có icon ngoài launcher, trừ Home và chính app này) |
| Manifest | `AndroidManifest.xml` | `<queries>` cho intent LAUNCHER + HOME (Android 11+ package visibility). Không cần `QUERY_ALL_PACKAGES` |

Lưu ý:
- Camera **vẫn mở** khi tạm dừng (đèn báo camera vẫn sáng), chỉ không phân tích ảnh. Muốn tắt hẳn camera thì TURN OFF.
- Ngay sau khi bật Accessibility, app chưa biết app nào đang mở cho tới lần chuyển app kế tiếp → mở app muốn dùng là được.
- Khi mở chính app Hand Control thì cũng tạm dừng.

## 4. Unit test

`AppGateTest`: 7 test (app đã chọn chạy, Home tạm dừng, app khác tạm dừng, không biết app thì tạm dừng, không chọn app nào thì tạm dừng, tắt công tắc thì chạy mọi nơi, System UI / bàn phím không tính là đổi app).

Kết quả: **55/55 pass** (43 GestureEngine + 7 AppGate + 4 ActionMapper + 1 Example). `assembleDebug` OK.

---

## 5. Cập nhật 2026-11-28: Chế độ test trong Hand Control

Khi đang mở **chính app Hand Control**, cử chỉ không còn bị tạm dừng mà chuyển sang **chế độ test**:

- Camera + AI vẫn chạy, cử chỉ được nhận diện như thật (cùng thời gian giữ, cooldown, bộ lọc).
- **Không thực hiện hành động**: không vuốt, không Back, không chạm, không đổi âm lượng. Back hay vuốt trong chính app sẽ làm thoát app hoặc cuộn màn hình nên không thực hiện.
- Tab **Điều khiển** hiện khung xanh **CHẾ ĐỘ TEST** ngay dưới nút TURN ON/OFF: dáng tay đang thấy, thanh tiến độ giữ yên, và **8 cử chỉ gần nhất** đã nhận (`Cử chỉ → Hành động sẽ làm`).
- Không cần Accessibility: app biết mình đang mở qua `MainActivity.onResume/onPause` (`core/app/OwnAppVisibility.kt`).
- Áp dụng cả khi tắt công tắc "Chỉ hoạt động trong app đã chọn".
- Khi chuyển giữa test ↔ app thật ↔ tạm dừng, gesture engine được reset: nắm tay dở dang lúc test không thành Pause khi sang TikTok.

| File | Thay đổi |
|---|---|
| `core/app/AppGate.kt` | Thêm `Decision.TEST`, tham số `ownAppVisible`, `Decision.recognises` |
| `core/app/OwnAppVisibility.kt` | Mới: cờ "Hand Control đang hiện" |
| `MainActivity.kt` | Bật / tắt cờ trong `onResume` / `onPause` |
| `service/HandControlForegroundService.kt` | Chạy AI khi ACTIVE hoặc TEST. Ở TEST chỉ ghi `testLog`, không gọi `ActionExecutor` |
| `ui/control/ControlScreen.kt` | Khung `TestModeCard` |
| `ui/control/AppFilterSection.kt` | Trạng thái "◐ Chế độ test" |

Unit test: thêm 2 test vào `AppGateTest` (app mình luôn là TEST kể cả khi không biết app đang mở hoặc tắt công tắc; TEST có chạy nhận diện, tạm dừng thì không). Kết quả: **59/59 pass**, `assembleDebug` OK.
