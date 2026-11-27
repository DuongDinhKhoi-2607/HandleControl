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
- [ ] Unit test pass: `gradlew.bat :app:testDebugUnitTest` (28 GestureEngine + 4 ActionMapper)

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

> Giữ tư thế **yên**: nắm tay 0.8 giây, các cử chỉ khác 0.5 giây. Thanh tiến độ chạy đầy là kích hoạt. Xòe 5 ngón không phải cử chỉ.

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 2.1 | Nắm tay, giữ 0.8 giây | Video Play/Pause (chạm giữa màn hình) | [ ] |
| 2.2 | Giữ nắm tay 5 giây | Chỉ kích hoạt **1 lần** | [ ] |
| 2.3 | Xòe tay rồi nắm lại (sau > 1.5 giây) | Kích hoạt lại lần nữa | [ ] |
| 2.4 | Nắm tay < 0.6 giây rồi xòe ra | **Không** kích hoạt | [ ] |
| 2.5 | Xòe 5 ngón, giữ 3 giây | **Không** kích hoạt gì | [ ] |
| 2.6 | Like xong thu ngón cái về thành nắm tay, giữ 3 giây | **Không** Pause | [ ] |
| 2.7 | Nắm tay nhanh rồi giơ ngón cái (Like) | Chỉ Like, **không** Pause | [ ] |
| 2.8 | Chỉ ngón trỏ lên (Point Up) | Âm lượng +1 nấc | [ ] |
| 2.9 | Chỉ ngón trỏ xuống (Point Down) | Âm lượng −1 nấc | [ ] |
| 2.10 | Chỉ ngón trỏ sang ngang | **Không** kích hoạt | [ ] |
| 2.11 | Point Up/Down khi Accessibility OFF | Âm lượng vẫn đổi (dùng AudioManager) | [ ] |

---

## 3. Cử chỉ vuốt (Swipe)

> Lia tay nhanh, quãng khoảng nửa khung hình, trong < 1 giây. Vuốt lên/xuống: giơ tay vào khung, **chờ ~0.5 giây** rồi mới vuốt, vuốt xong **dừng tay lại trong khung**.
> Nếu không nhận: mở tab **Camera + AI**, xem `Speed` khi vuốt. Cần vượt khoảng **2.0 palm/s**.
> Mapping mới: xem `08_Thiet_Ke_Lai_Cu_Chi.md`.

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 3.1 | Vuốt tay lên | Video tiếp theo (Next) | [ ] |
| 3.2 | Vuốt tay xuống | Video trước (Previous) | [ ] |
| 3.3 | Vuốt tay sang trái | Màn hình bị kéo sang trái | [ ] |
| 3.4 | Vuốt tay sang phải | Màn hình bị kéo sang phải | [ ] |
| 3.5 | Vuốt lên rồi hạ tay về ngay | Chỉ **1** lần Next, chiều về không tính | [ ] |
| 3.6 | Vuốt phải rồi kéo tay về trái ngay | Chỉ **1** lần kéo phải | [ ] |
| 3.7 | Giơ tay từ dưới vào khung hình | **Không** Next | [ ] |
| 3.8 | Hạ tay ra khỏi khung hình | **Không** Previous | [ ] |
| 3.9 | Di tay thật chậm (3–4 giây) theo bất kỳ hướng nào | **Không** kích hoạt | [ ] |
| 3.10 | Vuốt bằng bàn tay xòe, xong nắm tay lại giữ 2 giây | **Không** Pause | [ ] |
| 3.11 | Nắm tay (Pause chạy), đợi > 1 giây, xòe tay rồi vuốt | Pause **và** vuốt đều chạy | [ ] |
| 3.12 | Vuốt chéo (45°) | **Không** kích hoạt | [ ] |

### 3b. Like (Double Tap)

> Dáng "Like": nắm 4 ngón lại, chỉ giơ ngón cái hướng lên. Giữ yên ~0.5 giây.

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 3b.1 | Giơ Like, giữ 0.5 giây | Chạm 2 lần giữa màn hình (TikTok: thả tim) | [ ] |
| 3b.2 | Giữ Like 3 giây | Chỉ double tap **1 lần** | [ ] |
| 3b.3 | Giơ Like < 0.3 giây | **Không** kích hoạt | [ ] |
| 3b.4 | Nắm tay bình thường (ngón cái gập) | **Không** kích hoạt | [ ] |
| 3b.5 | Ngón cái chỉ xuống | **Không** kích hoạt | [ ] |
| 3b.6 | Like → nắm tay → Like lại (sau > 1.5 giây) | Đúng 2 lần double tap | [ ] |

### 3b2. Ngón cái sang trái (Back)

> Nắm 4 ngón, chỉ **thẳng** ngón cái sang **bên trái của bạn** (gần nằm ngang). Giữ yên ~1 giây.

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 3b2.1 | Ngón cái sang trái, giữ 1 giây | Quay lại trang trước (Back) | [ ] |
| 3b2.2 | Giữ 3 giây | Chỉ Back **1 lần** | [ ] |
| 3b2.3 | Ngón cái sang phải | **Không** kích hoạt | [ ] |
| 3b2.4 | Like rồi đổi sang ngón cái trái (sau > 1.5 giây) | Double tap rồi Back | [ ] |
| 3b2.5 | Ngón cái sang trái < 0.8 giây | **Không** Back | [ ] |
| 3b2.6 | Ngón cái chéo lên-trái (~45°) hoặc cong | **Không** Back | [ ] |

### 3c. Thời gian nghỉ giữa các hành động

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 3c.1 | Nắm tay (Pause) rồi xòe ra vuốt ngay trong < 1 giây | Chỉ Pause, **không** vuốt | [ ] |
| 3c.2 | Vuốt rồi làm cử chỉ khác trong < 1.2 giây | **Không** kích hoạt cử chỉ thứ 2 | [ ] |
| 3c.3 | Hai cử chỉ giữ yên cách nhau < 1.5 giây | Chỉ cử chỉ đầu kích hoạt | [ ] |

### 3d. Chỉ nhận bàn tay (không nhận cánh tay)

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 3d.1 | Nắm tay rồi đưa cánh tay ngang qua khung | **Không** vuốt | [ ] |
| 3d.2 | Chỉ đưa cẳng tay / nửa bàn tay vào mép khung | **Không** kích hoạt gì | [ ] |
| 3d.3 | Xòe tay rồi vuốt nhanh 4 hướng | Vuốt đúng hướng | [ ] |
| 3d.5 | Giật tay nhẹ một đoạn ngắn | **Không** vuốt | [ ] |
| 3d.6 | Đưa tay chéo (~45°) | **Không** vuốt | [ ] |
| 3d.7 | Vuốt ngang hơi vòng cung, lòng bàn tay hơi nghiêng | Vẫn vuốt đúng hướng | [ ] |
| 3d.4 | Gãi đầu / với lấy điện thoại trước camera | **Không** kích hoạt gì | [ ] |

---

### 3e. Chỉ hoạt động trong app đã chọn

> Bật Accessibility. Thẻ "Chỉ hoạt động trong app đã chọn" đang bật, đã chọn TikTok.

| # | Thao tác | Kết quả mong đợi | Pass |
|---|---|---|---|
| 3e.1 | Mở TikTok, vuốt lên | Video tiếp theo, thẻ hiện "● Đang điều khiển" | [ ] |
| 3e.2 | Về màn hình Home, vuốt / nắm tay / Back | **Không** làm gì, hiện "○ Tạm dừng" | [ ] |
| 3e.3 | Mở app chưa chọn (vd. Chrome), làm cử chỉ | **Không** làm gì | [ ] |
| 3e.4 | Trong TikTok, kéo thanh thông báo xuống rồi đóng | Vẫn "Đang điều khiển" | [ ] |
| 3e.5 | Trong TikTok, mở bàn phím (ô bình luận) | Vẫn "Đang điều khiển" | [ ] |
| 3e.6 | Giữ nắm tay dở dang trong TikTok rồi bấm Home | **Không** Pause khi quay lại | [ ] |
| 3e.7 | Tắt Accessibility khi công tắc đang bật | Tạm dừng, thông báo nhắc bật Accessibility | [ ] |
| 3e.8 | Tắt công tắc | Cử chỉ hoạt động ở mọi nơi, kể cả Home | [ ] |
| 3e.9 | Chọn thêm YouTube, mở YouTube Shorts | Cử chỉ hoạt động | [ ] |
| 3e.10 | Tắt app rồi mở lại | Công tắc + danh sách app vẫn giữ nguyên | [ ] |
| 3e.11 | TURN ON, ở lại tab Điều khiển, nắm tay giữ 1 giây | Khung CHẾ ĐỘ TEST hiện "Nắm tay → Play / Pause", video **không** bị ảnh hưởng | [ ] |
| 3e.12 | Trong chế độ test: ngón cái sang trái giữ 1 giây | Hiện trong danh sách, app **không** bị Back ra ngoài | [ ] |
| 3e.13 | Trong chế độ test: vuốt, Like, chỉ lên / xuống | Đều hiện trong danh sách, không vuốt / chạm / đổi âm lượng | [ ] |
| 3e.14 | Chế độ test khi Accessibility OFF | Vẫn nhận diện và hiện danh sách | [ ] |
| 3e.15 | Giữ nắm tay dở trong Hand Control rồi chuyển sang TikTok | TikTok **không** bị Pause | [ ] |

---

## 4. Khoảng cách và môi trường

Làm mỗi cử chỉ **10 lần**, ghi số lần đúng.

| Gesture | 0.5 m | 1.0 m | 1.5 m | Thiếu sáng |
|---|---|---|---|---|
| Nắm tay (Play/Pause) | ../10 | ../10 | ../10 | ../10 |
| Point Up | ../10 | ../10 | ../10 | ../10 |
| Point Down | ../10 | ../10 | ../10 | ../10 |
| Swipe Up | ../10 | ../10 | ../10 | ../10 |
| Swipe Down | ../10 | ../10 | ../10 | ../10 |
| Swipe Right | ../10 | ../10 | ../10 | ../10 |
| Swipe Left | ../10 | ../10 | ../10 | ../10 |
| Like | ../10 | ../10 | ../10 | ../10 |
| Ngón cái trái (Back) | ../10 | ../10 | ../10 | ../10 |

---

## 5. Chạy trong app khác

| # | App | Next | Previous | Kéo trái/phải | Double tap | Play/Pause | Volume | Pass |
|---|---|---|---|---|---|---|---|---|
| 5.1 | TikTok | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 5.2 | YouTube Shorts | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 5.3 | Facebook Reels | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 5.4 | Instagram Reels | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |

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
