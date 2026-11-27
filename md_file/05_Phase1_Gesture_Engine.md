# Feature 5: Phase 1 — Gesture Engine

**Ngày thực hiện:** 2026-11-27
**Tham chiếu Plan:** `md_file/Plan.md` — Mục `10` đến `13`

Code: `app/src/main/java/com/khoi/handlecontrol/core/gesture/` (Kotlin thuần, không phụ thuộc Android → unit test được).

---

## 1. Gesture

| Gesture | Loại | Cách nhận diện |
|---|---|---|
| `OPEN_PALM` | Tĩnh | 4 ngón (trỏ → út) đều duỗi |
| `POINT_UP` | Tĩnh | Chỉ ngón trỏ duỗi, hướng MCP→đầu ngón gần thẳng đứng, đi lên |
| `POINT_DOWN` | Tĩnh | Như trên nhưng đi xuống |
| `SWIPE_LEFT` / `SWIPE_RIGHT` | Động | Tâm lòng bàn tay di chuyển nhanh theo phương ngang |

**Ngón duỗi** = khoảng cách cổ tay→đầu ngón > 1.1 × cổ tay→khớp PIP. So sánh khoảng cách từ cổ tay nên **không phụ thuộc góc xoay** của bàn tay (Point Down dùng cùng một luật).

Ngón cái bị bỏ qua để tư thế dễ làm hơn.

## 2. Đơn vị "palm" (không phụ thuộc khoảng cách)

Mọi khoảng cách / vận tốc được chia cho **kích thước lòng bàn tay** (cổ tay → gốc ngón giữa, có làm mượt). Cùng một cú vuốt tay ~30 cm luôn là ~3 palm dù đứng 0.5 m hay 1.5 m. Đã kiểm chứng bằng test `swipe_works_for_small_far_away_hand`.

Toạ độ x được nhân với tỉ lệ khung hình (width/height) trước khi đo.

## 3. State Machine

```text
IDLE ──tay xuất hiện──▶ TRACKING ──pose / chuyển động──▶ ARMED
ARMED ──giữ pose──▶ CONFIRMING ──đủ staticHoldMs──▶ EXECUTE
ARMED ──đủ quãng đường + vận tốc──────────────────▶ EXECUTE
EXECUTE ──▶ COOLDOWN ──hết thời gian──▶ TRACKING (hoặc IDLE nếu mất tay)
```

Chống kích hoạt nhầm:
- **Confidence**: frame có handedness score < `0.60` bị bỏ qua.
- **Giữ yên**: pose tĩnh phải giữ 500 ms và tay gần đứng yên (< 1.2 palm/s). Đang vuốt thì không tính pose.
- **Latch**: pose tĩnh đã bắn thì phải **bỏ ra ≥ 300 ms** mới bắn lại. Giữ Open Palm 10 giây = 1 lần Play/Pause; giữ Point Up = +1 nấc âm lượng.
- **Chịu nhiễu**: pose biến mất ≤ 150 ms trong lúc giữ không làm reset; tay mất ≤ 300 ms vẫn coi như còn.
- **Chiều về của cú vuốt**: sau Swipe Right, Swipe Left bị chặn 1.2 s (và ngược lại).
- **Tay vừa vào khung**: phải được theo dõi ≥ 150 ms mới bắt đầu tính vuốt.
- **Cooldown**: 800 ms sau pose tĩnh (chỉ chặn pose tĩnh, vẫn vuốt được ngay sau Play/Pause), 700 ms sau swipe (chặn tất cả); chuyển động trong cooldown swipe không được tích luỹ.
- **Sau swipe**: tư thế tay lúc dừng bị latch, nên vuốt bằng tay xòe rồi giữ yên không kích hoạt Play/Pause.

## 4. Thông số (`GestureConfig`)

| Tham số | Mặc định | Ý nghĩa |
|---|---|---|
| `minConfidence` | 0.60 | Ngưỡng confidence |
| `staticHoldMs` | 500 | Thời gian giữ pose |
| `staticCooldownMs` | 800 | Cooldown pose tĩnh |
| `staticReleaseMs` | 300 | Thời gian phải bỏ pose trước khi bắn lại |
| `swipeMinDistance` | 1.2 palm | `minDistance` |
| `swipeMinVelocity` | 2.0 palm/s | `minVelocity` |
| `swipeMaxDurationMs` | 800 | `maxDuration` |
| `swipeCooldownMs` | 700 | `cooldown` |
| `swipeReturnSuppressMs` | 1200 | Chặn chiều ngược lại |
| `enabledGestures` | tất cả | Bật/tắt từng gesture (Phase 7) |

Các giá trị này là điểm xuất phát, **cần tune trên máy thật**. Tab **Camera + AI** giờ hiển thị thêm `Engine state`, `Pose`, `Hold %`, `Speed (palm/s)` và `Last gesture` để tune bằng mắt (tab này chỉ nhận diện, không thực hiện action).

## 5. Unit test

`app/src/test/.../core/gesture/GestureEngineTest.kt` dùng bàn tay tổng hợp (`SyntheticHand`), 17 test:
pose classifier, giữ pose bắn đúng 1 lần, giữ quá ngắn không bắn, nhả rồi giơ lại, glitch 1 frame không bắn lại, point up/down, confidence thấp, gesture bị tắt, swipe trái/phải, chiều về bị chặn, 2 swipe liên tiếp, trôi chậm không phải swipe, vuốt bằng lòng bàn tay mở không kích hoạt Play/Pause, tay nhỏ ở xa, vuốt ngay sau Play/Pause vẫn nhận, giữ tay xòe sau khi vuốt không Pause.

```bash
gradlew.bat :app:testDebugUnitTest
```

Kết quả: **17/17 pass**.

## 6. Cần đo trên máy thật (Plan mục 21)

- [ ] Accuracy / False Positive / False Negative cho từng gesture ở 0.5 m, 1.0 m, 1.5 m
- [ ] Response time (từ lúc làm cử chỉ đến khi action chạy)
- [ ] False trigger / giờ khi để tay tự nhiên trước camera
