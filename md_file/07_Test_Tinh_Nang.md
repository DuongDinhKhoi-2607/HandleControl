# Test tính năng — Hand Control

**Ngày tạo:** 2026-11-27
**Phạm vi:** Phase 0 → Phase 3 (Camera + MediaPipe + Gesture Engine + Action Mapper + Android Control)
**Tham chiếu:** `05_Phase1_Gesture_Engine.md`, `06_Phase2_3_ActionMapper_AndroidControl.md`, `Plan.md` mục 21, 26

Đánh dấu `[x]` khi pass. Lỗi thì ghi vào bảng **Ghi lỗi** ở cuối file.

---

## 0. Chuẩn bị

| Mục | Giá trị |
|---|---|
| Máy test | ……………… (model, Android version) |
| Bản build | `gradlew.bat :app:installDebug` — commit ……… |
| Ánh sáng | Phòng sáng bình thường, không ngược sáng |
| Vị trí | Điện thoại dựng đứng, camera trước, tay cách 0.5–1 m |

- [ ] Đã cấp quyền **Camera**
- [ ] Đã cấp quyền **Notification** (Android 13+)
- [ ] Đã bật **Accessibility Service** của app (Cài đặt → Hỗ trợ tiếp cận)
- [ ] Unit test pass: `gradlew.bat :app:testDebugUnitTest` (17 GestureEngine + 4 ActionMapper)

---

## 1. Bật / tắt Hand Control (tab **Điều khiển**)

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 1.1 | Bấm `TURN ON` | Hiện `● ACTIVE`, Camera ON, AI ON, có notification | [ ] |
| 1.2 | Đưa tay vào khung hình | `Hand: YES`, FPS > 10 | [ ] |
| 1.3 | Bỏ tay ra | `Hand: NO`, State về `IDLE` | [ ] |
| 1.4 | Bấm `TURN OFF` | `○ OFF`, notification biến mất, đèn camera tắt | [ ] |
| 1.5 | Bấm `Tắt` trên notification | Giống 1.4 | [ ] |
| 1.6 | Tắt Accessibility rồi làm Swipe | Báo lỗi `Accessibility Service đang OFF`, app không crash | [ ] |

---

## 2. Cử chỉ tĩnh

> Giữ tư thế **yên** khoảng 0.5 giây. Thanh tiến độ chạy đầy là kích hoạt.

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 2.1 | Xòe bàn tay (Open Palm) | Video Play/Pause (chạm giữa màn hình) | [ ] |
| 2.2 | Giữ Open Palm 5 giây | Chỉ kích hoạt **1 lần** | [ ] |
| 2.3 | Nắm tay rồi xòe lại | Kích hoạt lại lần nữa | [ ] |
| 2.4 | Xòe tay < 0.5 giây rồi nắm lại | **Không** kích hoạt | [ ] |
| 2.5 | Chỉ ngón trỏ lên (Point Up) | Âm lượng +1 nấc | [ ] |
| 2.6 | Chỉ ngón trỏ xuống (Point Down) | Âm lượng −1 nấc | [ ] |
| 2.7 | Chỉ ngón trỏ sang ngang | **Không** kích hoạt | [ ] |
| 2.8 | Point Up/Down khi Accessibility OFF | Âm lượng vẫn đổi (dùng AudioManager) | [ ] |

---

## 3. Cử chỉ vuốt (Swipe)

> Lia tay **ngang**, nhanh, quãng khoảng nửa khung hình, trong < 1 giây.
> Nếu không nhận: mở tab **Camera + AI**, xem `Speed` khi vuốt. Cần vượt khoảng **2.0 palm/s**.

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 3.1 | Vuốt tay sang phải | Video tiếp theo (Next) | [ ] |
| 3.2 | Vuốt tay sang trái | Video trước (Previous) | [ ] |
| 3.3 | Vuốt phải rồi kéo tay về trái ngay | Chỉ **1** lần Next, chiều về không tính | [ ] |
| 3.4 | Vuốt phải, bỏ tay ra, vuốt phải lại | 2 lần Next | [ ] |
| 3.5 | Di tay sang ngang thật chậm (3–4 giây) | **Không** kích hoạt | [ ] |
| 3.6 | Vuốt bằng bàn tay xòe | Chỉ Next/Previous, **không** Pause | [ ] |
| 3.7 | Vuốt xong giữ yên tay xòe 2 giây | **Không** Pause | [ ] |
| 3.8 | Xòe tay (Pause chạy) rồi vuốt ngay | Pause **và** Next đều chạy | [ ] |
| 3.9 | Đưa tay lên/xuống theo chiều dọc | **Không** kích hoạt swipe | [ ] |

---

## 4. Khoảng cách và môi trường

Làm mỗi cử chỉ **10 lần**, ghi số lần đúng.

| Gesture | 0.5 m | 1.0 m | 1.5 m | Thiếu sáng |
|---|---|---|---|---|
| Open Palm | ../10 | ../10 | ../10 | ../10 |
| Point Up | ../10 | ../10 | ../10 | ../10 |
| Point Down | ../10 | ../10 | ../10 | ../10 |
| Swipe Right | ../10 | ../10 | ../10 | ../10 |
| Swipe Left | ../10 | ../10 | ../10 | ../10 |

---

## 5. Chạy trong app khác

| # | App | Next | Previous | Play/Pause | Volume | Pass |
|---|---|---|---|---|---|---|
| 5.1 | TikTok | [ ] | [ ] | [ ] | [ ] | [ ] |
| 5.2 | YouTube Shorts | [ ] | [ ] | [ ] | [ ] | [ ] |
| 5.3 | Facebook Reels | [ ] | [ ] | [ ] | [ ] | [ ] |
| 5.4 | Instagram Reels | [ ] | [ ] | [ ] | [ ] | [ ] |

- [ ] Khóa màn hình rồi mở lại: Hand Control vẫn chạy (hoặc báo rõ trạng thái)
- [ ] Chuyển qua lại nhiều app: không crash, notification vẫn còn

---

## 6. Kích hoạt nhầm và hiệu năng

| # | Kiểm tra | Mục tiêu | Kết quả |
|---|---|---|---|
| 6.1 | Để tay tự nhiên trước camera 10 phút (gãi đầu, cầm cốc…) | ≤ 1 lần kích hoạt nhầm | …… lần |
| 6.2 | Thời gian từ lúc làm cử chỉ đến khi action chạy | < 500 ms | …… ms |
| 6.3 | FPS trung bình | ≥ 15 | …… |
| 6.4 | Pin sau 30 phút chạy | Ghi lại | …… % |
| 6.5 | Máy có nóng không sau 30 phút | Ghi lại | …… |

---

## 7. Ghi lỗi

| # | Mục test | Mô tả lỗi | Cách tái hiện | Mức độ | Trạng thái |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

**Mức độ:** Cao (crash / không dùng được) · Trung bình (sai cử chỉ) · Thấp (giao diện, hiển thị)
