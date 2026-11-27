# Feature 8: Thiết kế lại cử chỉ (trước bước Design UI)

**Ngày thực hiện:** 2026-11-28
**Thay thế mapping trong:** `05_Phase1_Gesture_Engine.md` mục 1, `06_Phase2_3_ActionMapper_AndroidControl.md` mục 1

---

## 1. Mapping mới

| Cử chỉ tay | Gesture | Action | Trên màn hình |
|---|---|---|---|
| Vuốt tay **lên** | `SWIPE_UP` | `NEXT` | Kéo màn hình lên → video tiếp |
| Vuốt tay **xuống** | `SWIPE_DOWN` | `PREVIOUS` | Kéo màn hình xuống → video trước |
| Vuốt tay **trái** | `SWIPE_LEFT` | `SWIPE_LEFT` | Kéo màn hình sang trái |
| Vuốt tay **phải** | `SWIPE_RIGHT` | `SWIPE_RIGHT` | Kéo màn hình sang phải |
| **Like: nắm 4 ngón, giơ ngón cái** (xem mục 7) | `THUMB_UP` | `DOUBLE_TAP` | Chạm 2 lần giữa màn hình (thả tim TikTok/Reels) |
| Xòe tay (giữ 0.5 s) | `OPEN_PALM` | `PLAY_PAUSE` | Giữ nguyên |
| Chỉ ngón trỏ lên / xuống (giữ 0.5 s) | `POINT_UP` / `POINT_DOWN` | `VOLUME_UP` / `VOLUME_DOWN` | Giữ nguyên |

## 2. Vuốt lên / xuống

Dùng chung bộ phát hiện với vuốt ngang: quãng đường ≥ 1.2 palm, vận tốc ≥ 2.0 palm/s, trục chính ≥ 1.5 × trục phụ (`swipeAxisRatio`, thay cho `swipeHorizontalRatio`).

Vuốt dọc dễ bị nhận nhầm hơn vuốt ngang, nên có thêm 3 lớp chặn:

| Tình huống nhầm | Cách chặn | Tham số |
|---|---|---|
| Giơ tay vào khung hình (tay đi lên) | Vuốt dọc chỉ tính khi tay đã được theo dõi ≥ 350 ms | `swipeVerticalMinTrackingMs` |
| Hạ tay ra khỏi khung (tay đi xuống) | Vuốt dọc chỉ bắn khi tay **dừng lại trong khung** (< 1.5 palm/s), hoặc sau 500 ms nếu tay vẫn còn trong khung. Tay mất khỏi khung trước đó thì huỷ | `swipeVerticalSettleSpeed`, `swipeVerticalMaxPendingMs` |
| Tay dừng sát mép trên/dưới | Huỷ nếu tâm bàn tay nằm trong 8% mép khung | `swipeVerticalEdgeMargin` |

Vì phải chờ tay dừng, vuốt dọc phản hồi chậm hơn vuốt ngang khoảng 100–200 ms. Chiều về vẫn bị chặn 1.2 s: vuốt lên rồi hạ tay về không tính là vuốt xuống.

## 3. Chụm 2 lần (`DOUBLE_PINCH`) — ĐÃ BỎ, xem mục 7

Cách làm: tạo dáng gần giống ký hiệu **"OK"**: ngón cái chạm đầu ngón trỏ, 3 ngón còn lại duỗi. Chạm → mở → chạm thật nhanh.

- **Chạm**: khoảng cách đầu ngón cái ↔ đầu ngón trỏ < 0.35 palm **và** ít nhất 2 trong 3 ngón giữa/áp út/út duỗi. Điều kiện thứ 2 để nắm tay (ngón cái tì lên ngón trỏ) không bị tính là chụm.
- **Mở**: khoảng cách > 0.55 palm. Hai ngưỡng khác nhau để chống rung.
- Mỗi lần chạm ≤ 450 ms, lần chạm thứ 2 bắt đầu trong vòng 700 ms tính từ lần 1. Bắn ngay khi bắt đầu lần chạm thứ 2.
- Trong lúc đang chụm thì không tính Open Palm. Sau khi bắn, tư thế tay lúc mở ra bị latch nên không kích hoạt Play/Pause. Cooldown 700 ms.
- Chụm 4 lần liên tiếp = 2 lần double tap, không phải 3.

Accessibility: `performDoubleTap()` gửi 2 nét chạm 80 ms, cách nhau 90 ms, trong một `GestureDescription`.

## 4. File đã sửa

- `core/gesture/Gesture.kt`: thêm `GestureKind`, `SWIPE_UP`, `SWIPE_DOWN`, `DOUBLE_PINCH`, `Gesture.opposite`, các tham số mới.
- `core/gesture/StaticGestureClassifier.kt`: `pinchDistance()`, `isPinchShape()`.
- `core/gesture/GestureEngine.kt`: vuốt 4 hướng, vuốt dọc chờ tay dừng, double pinch.
- `core/action/ActionMapper.kt`: action `DOUBLE_TAP`, mapping mới.
- `core/action/ActionExecutor.kt`, `service/HandControlAccessibilityService.kt`: double tap.
- `ui/control/ControlScreen.kt`: dòng hướng dẫn.

## 5. Unit test

Thêm 11 test: vuốt lên (chiều về bị chặn), vuốt xuống, giơ tay vào khung không phải vuốt lên, hạ tay ra khỏi khung không phải vuốt xuống, vuốt dọc bằng tay xòe không Pause, classifier dáng chụm, chụm 2 lần bắn 1 lần và không Pause, chụm 4 lần = 2 lần, chụm chậm không bắn, chụm giữ lâu rồi chạm 1 lần không bắn, nắm/xòe tay liên tục không phải chụm.

Kết quả: **33/33 pass** (28 GestureEngine + 4 ActionMapper + 1 Example). Build `assembleDebug` OK.

## 6. Cần tune trên máy thật

- `pinchCloseDistance` / `pinchOpenDistance`: nếu khó nhận chụm thì tăng ngưỡng chạm lên 0.45. Nếu nhận nhầm thì giảm xuống.
- `swipeMinDistanceVertical`: khung camera dọc nên vuốt dọc có thể cần quãng lớn hơn để tránh nhầm.
- `swipeVerticalMinTrackingMs`: nếu hay bị nhận vuốt lên khi giơ tay thì tăng lên 500.

---

## 7. Cập nhật 2026-11-28: Like thay cho chụm 2 lần, tăng thời gian nghỉ

**Thay thế mục 3.** Bỏ hẳn `DOUBLE_PINCH` (khó làm, hay nhận nhầm). Double tap giờ dùng cử chỉ **Like**:

| Cử chỉ tay | Gesture | Action |
|---|---|---|
| Nắm 4 ngón, giơ ngón cái lên (giữ 0.5 s) | `THUMB_UP` | `DOUBLE_TAP` |

Cách nhận diện (`StaticGestureClassifier.isThumbUp()`):
- 4 ngón trỏ/giữa/áp út/út đều **gập** (cùng quy tắc với nắm tay).
- Đầu ngón cái cách khớp gốc ngón trỏ ≥ 0.7 palm (nắm tay thường thì ngón cái tì lên ngón trỏ → không tính).
- Ngón cái **hướng lên** trong khoảng ~40° so với phương thẳng đứng. Ngón cái chỉ xuống không tính.
- Là cử chỉ giữ yên nên dùng chung cơ chế với Open Palm: giữ 0.5 s, bắn 1 lần, phải thả ra mới bắn lại.

**Thời gian nghỉ mới** (trước đây quá ngắn nên hay bị nhập 2 hành động liên tiếp):

| Tham số | Cũ | Mới | Ý nghĩa |
|---|---|---|---|
| `actionGapMs` | — | 1000 ms | Mới: sau **bất kỳ** hành động nào, chặn mọi cử chỉ 1 s |
| `staticCooldownMs` | 800 ms | 1500 ms | Giữa 2 cử chỉ giữ yên |
| `swipeCooldownMs` | 700 ms | 1200 ms | Sau khi vuốt |
| `swipeReturnSuppressMs` | 1200 ms | 1800 ms | Chặn vuốt chiều ngược lại (tay quay về) |

Hệ quả: xòe tay (Pause) rồi vuốt ngay trong vòng 1 s sẽ **không** vuốt nữa (trước đây có vuốt).

Unit test: bỏ 6 test chụm, thêm test Like (nhận diện, giữ bắn 1 lần, giữ ngắn không bắn, thả rồi Like lại), test thời gian nghỉ. Kết quả: **33/33 pass**, `assembleDebug` OK.

Nếu tune trên máy thật: Like khó nhận → giảm `THUMB_OUT_DISTANCE` (0.7 → 0.55); cử chỉ vẫn bị nhập dồn → tăng `actionGapMs` lên 1500.

---

## 8. Cập nhật 2026-11-28: chỉ nhận bàn tay, không để cánh tay gây sai lệnh

MediaPipe chỉ trả về 21 điểm của bàn tay. Nhưng trước đây di chuyển cánh tay vẫn dễ thành lệnh vì: (1) ngưỡng tin cậy thấp nên MediaPipe "đoán" ra bàn tay trên cẳng tay hoặc bàn tay mới lộ một phần; (2) không kiểm tra bàn tay có nằm trọn trong khung; (3) vuốt với **bất kỳ** dáng tay nào cũng tính.

| Lớp lọc | Cách làm | Tham số |
|---|---|---|
| Ngưỡng MediaPipe | Detection / presence 0.5 → 0.7, tracking 0.5 → 0.6 | `HandLandmarkerHelper` |
| Ngưỡng engine | Độ tin cậy 0.6 → 0.7 | `minConfidence` |
| Bàn tay trọn trong khung | Cổ tay + 5 khớp lòng bàn tay (0, 1, 5, 9, 13, 17) phải nằm trong khung (dung sai 2%). Không đạt thì coi như không có tay | `frameMargin` |
| Bỏ phát hiện quá nhỏ | Lòng bàn tay < 2.5% chiều cao khung thì bỏ | `minPalmSize` |
| Vuốt phải xòe tay | ≥ 60% số frame trong lúc vuốt phải có ≥ 3 ngón duỗi. Nắm tay / thả lỏng tay di chuyển thì không tính | `swipeMinOpenRatio` |

**Cách dùng thay đổi:** muốn vuốt thì phải **xòe bàn tay** rồi vuốt. Để cả bàn tay (gồm cổ tay) nằm trong khung hình.

Unit test: thêm 4 test (nắm tay di chuyển không phải vuốt, bàn tay ra ngoài khung bị bỏ, phát hiện quá nhỏ bị bỏ, kiểm tra hàm lọc). Các test vuốt đổi sang tay xòe. Kết quả: **37/37 pass**, `assembleDebug` OK.

Nếu tune trên máy thật: hay bị mất tay khi vuốt nhanh → giảm `swipeMinOpenRatio` xuống 0.4; vẫn còn nhận nhầm → tăng ngưỡng MediaPipe lên 0.8.

---

## 9. Cập nhật 2026-11-28: ngón cái sang trái = Back

| Cử chỉ tay | Gesture | Action |
|---|---|---|
| Nắm 4 ngón, chỉ ngón cái sang **trái của người dùng** (giữ 0.5 s) | `THUMB_LEFT` | `BACK` (quay lại trang trước) |

- Dùng chung hàm với Like: `StaticGestureClassifier.thumbGesture()`. 4 ngón gập, đầu ngón cái cách khớp gốc ngón trỏ ≥ 0.7 palm, rồi xét hướng ngón cái (khớp 2 → đầu ngón 4):
  - Hướng lên (trục dọc ≥ 1.2 × trục ngang) → `THUMB_UP`.
  - Hướng sang trái (trục ngang ≥ 1.2 × trục dọc, x giảm) → `THUMB_LEFT`. Khung hình đã lật như gương nên "trái" là trái của người dùng.
  - Chỉ xuống / sang phải → không tính.
- Là cử chỉ giữ yên: bắn 1 lần, phải thả ra mới bắn lại, cooldown 1.5 s như các cử chỉ giữ yên khác.
- `BACK` dùng `performGlobalAction(GLOBAL_ACTION_BACK)`, cần bật Accessibility.

Unit test: thêm 3 test (nhận diện ngón cái trái / phải / nắm tay nằm ngang, giữ lâu bắn 1 lần, Like rồi Back). Kết quả: **40/40 pass**, `assembleDebug` OK.

---

## 10. Cập nhật 2026-11-28: nắm tay = Play/Pause, xòe tay không làm gì

| Cử chỉ tay | Gesture | Action |
|---|---|---|
| **Nắm tay** (giữ 0.8 s) | `FIST` (thay `OPEN_PALM`) | `PLAY_PAUSE` |
| Xòe 5 ngón | không còn là cử chỉ | Không làm gì, chỉ dùng để vuốt |

- Nhận diện: 4 ngón gập **và** ngón cái gập vào (đầu ngón cái cách khớp gốc ngón trỏ < 0.7 palm). Nếu ngón cái chìa ra thì là Like / Back (xem mục 7, 9).
- Nắm tay là dáng trung gian của Like / Back, nên có 2 lớp chặn:
  - Nắm tay phải giữ **0.8 s** (`fistHoldMs`), lâu hơn 0.5 s của các cử chỉ khác. Nắm tay nhanh rồi giơ ngón cái thì chỉ Like.
  - Sau khi Like / Back bắn, nắm tay bị **khóa** cho tới khi xòe tay ra hoặc đưa tay ra khỏi khung. Like xong thu ngón cái về thành nắm tay thì không Pause.
- Sau khi vuốt, nắm tay lại trong lúc cooldown thì bị latch, không Pause.

Unit test: viết lại các test Open Palm thành test nắm tay, thêm test xòe tay không làm gì, nắm tay nhanh trên đường tới Like, nắm tay bị khóa sau Like / Back, xòe tay mở khóa, nắm tay sau vuốt dọc. Kết quả: **44/44 pass** (39 GestureEngine + 4 ActionMapper + 1 Example), `assembleDebug` OK.

Nếu tune trên máy thật: hay bị Pause nhầm → tăng `fistHoldMs` lên 1000; khó Pause → giảm xuống 600.

---

## 11. Cập nhật 2026-11-28: giảm độ nhạy của Back và vuốt

**Back (ngón cái sang trái)** khó kích hoạt hơn:

| Điều kiện | Cũ | Mới |
|---|---|---|
| Thời gian giữ | 0.5 s | **1.0 s** (`thumbLeftHoldMs`) |
| Độ ngang của ngón cái | trong ~40° (1.2×) | trong **~27°** (2×, `THUMB_LEFT_AXIS_RATIO`) |
| Ngón cái thẳng | không xét | MCP → đầu ngón / tổng 2 đốt ≥ 0.9 (`THUMB_STRAIGHT_RATIO`) |
| Đầu ngón cái vượt ra ngoài nắm tay | không xét | ≥ 0.25 palm bên trái khớp ngón ngoài cùng (`THUMB_LEFT_PAST_KNUCKLES`) |

Like (ngón cái lên) giữ nguyên.

**Vuốt** kém nhạy hơn:

| Tham số | Cũ | Mới |
|---|---|---|
| `swipeMinDistance` / `swipeMinDistanceVertical` | 1.2 palm | **1.8 palm** |
| `swipeMinVelocity` | 2.0 palm/s | **3.0 palm/s** |
| `swipeMinDurationMs` | 60 ms | **100 ms** (chặn landmark nhảy giữa 2 frame) |
| `swipeAxisRatio` | 1.5× | **2.0×** (vuốt chéo không tính) |
| `swipeMinOpenRatio` | 60% | **75%** frame phải xòe tay |
| `swipeMinTrackingMs` | 150 ms | **250 ms** |

Unit test: thêm 4 test (Back < 1 s không bắn, ngón cái chéo 45° không phải Back, giật tay ngắn không phải vuốt, đưa tay chéo không phải vuốt). Kết quả: **48/48 pass**, `assembleDebug` OK.

Nếu tune trên máy thật: vuốt giờ khó quá thì hạ `swipeMinDistance` về 1.5; Back khó quá thì hạ `thumbLeftHoldMs` về 800 hoặc `THUMB_LEFT_AXIS_RATIO` về 1.6.

---

## 12. Cập nhật 2026-11-28: vuốt nhạy lại một chút, vuốt ngang dễ hơn vuốt dọc

Mục 11 làm vuốt quá khó, nhất là vuốt ngang. Lý do vuốt ngang khó hơn: điện thoại dựng đứng nên khung hình **hẹp chiều ngang**, tay vuốt ngang hay đi **vòng cung**, và lòng bàn tay hay **xoay nghiêng** khi đưa sang bên (mất dáng xòe tay). Giờ tách ngưỡng riêng cho 2 chiều:

| Tham số | Mục 10 (nhạy) | Mục 11 (khó) | **Mới: ngang** | **Mới: dọc** |
|---|---|---|---|---|
| Quãng đường tối thiểu | 1.2 palm | 1.8 palm | **1.2** (`swipeMinDistance`) | **1.5** (`swipeMinDistanceVertical`) |
| Tốc độ tối thiểu | 2.0 palm/s | 3.0 palm/s | **2.5** | **2.5** (`swipeMinVelocity`) |
| Trục chính / trục phụ | 1.5× | 2.0× | **1.5×** (`swipeAxisRatioHorizontal`) | **1.8×** (`swipeAxisRatioVertical`) |
| % frame xòe tay | 60% | 75% | **50%** (`swipeMinOpenRatioHorizontal`) | **65%** (`swipeMinOpenRatioVertical`) |
| Thời gian tối thiểu | 60 ms | 100 ms | **80 ms** | **80 ms** (`swipeMinDurationMs`) |

`swipeMinTrackingMs` giữ 250 ms. Thay `swipeAxisRatio` / `swipeMinOpenRatio` bằng cặp tham số ngang / dọc.

Unit test: thêm 2 test (vuốt ngang hơi cong vẫn tính, vuốt ngang mà lòng bàn tay xoay nghiêng nửa số frame vẫn tính). Sửa 2 test cho ngưỡng mới (giật tay ~1 palm không tính, đưa tay chéo ~40° không tính). Kết quả: **57/57 pass**, `assembleDebug` OK.

Nếu tune tiếp: vuốt ngang vẫn khó → `swipeMinDistance` 1.0 hoặc `swipeMinOpenRatioHorizontal` 0.4; vuốt ngang bị nhầm nhiều → `swipeAxisRatioHorizontal` 1.7.

---

## 13. Cập nhật 2026-11-28: tinh chỉnh vuốt nhạy hơn một chút, ưu tiên vuốt ngang

Sau khi test trên máy thật (`451c65cf0223`), vuốt dọc hơi ít nhạy và vuốt ngang vẫn khó nhận trên khung hình dọc. Điều chỉnh lại cân bằng giữa mục 10 (nhạy) và mục 12 (khó), tách thêm `swipeMinVelocityHorizontal` riêng cho chiều ngang:

| Tham số | Mục 12 | **Mới: ngang** | **Mới: dọc** |
|---|---|---|---|
| Quãng đường tối thiểu | 1.2 / 1.5 | **0.95 palm** (`swipeMinDistance`) | **1.35 palm** (`swipeMinDistanceVertical`) |
| Tốc độ tối thiểu | 2.5 palm/s | **1.7 palm/s** (`swipeMinVelocityHorizontal`) | **2.2 palm/s** (`swipeMinVelocity`) |
| Trục chính / trục phụ | 1.5× / 1.8× | **1.35×** (`swipeAxisRatioHorizontal`) | **1.65×** (`swipeAxisRatioVertical`) |
| % frame xòe tay | 50% / 65% | **35%** (`swipeMinOpenRatioHorizontal`) | **55%** (`swipeMinOpenRatioVertical`) |
| Thời gian theo dõi trước vuốt | 250 ms | **200 ms** (`swipeMinTrackingMs`) | **200 ms** (`swipeMinTrackingMs`) |

File đã sửa: `core/gesture/Gesture.kt`, `core/gesture/GestureEngine.kt`, `GestureEngineTest.kt`.
