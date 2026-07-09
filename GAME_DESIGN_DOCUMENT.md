# TÀI LIỆU THIẾT KẾ TRÒ CHƠI (GAME DESIGN DOCUMENT)
# MIMOPE.IO

---

## 2. VÒNG LẶP LỐI CHƠI CỐT LÕI

Lối chơi của Mimope.io được xây dựng quanh một vòng lặp trung tâm: **Ăn → Tích lũy
kinh nghiệm → Tiến hóa → Săn mồi mạnh hơn**. Người chơi bắt đầu ở dạng sinh vật
yếu nhất và dần leo lên đỉnh chuỗi thức ăn thông qua việc lặp lại vòng lặp này.

### 2.1. Vòng lặp chính (Core Loop)

Một chu kỳ chơi điển hình diễn ra như sau:

1. **Tham gia trận đấu**: Người chơi nhập tên, chọn sinh vật khởi đầu và được
   sinh ra tại một vị trí ngẫu nhiên trong vùng môi trường tương ứng.
2. **Thu thập thức ăn**: Người chơi di chuyển để ăn thức ăn rải rác trong bản đồ
   nhằm tích lũy điểm kinh nghiệm (XP).
3. **Tích lũy XP**: Mỗi loại thức ăn hoặc con mồi cung cấp một lượng XP khác nhau.
4. **Tiến hóa**: Khi đạt đủ ngưỡng XP của bậc kế tiếp, người chơi được đề nghị
   tiến hóa lên sinh vật ở bậc cao hơn, mạnh và to hơn.
5. **Săn mồi và sinh tồn**: Với sức mạnh mới, người chơi có thể săn các sinh vật
   bậc thấp hơn, đồng thời né tránh hoặc phản công các sinh vật bậc cao hơn.
6. **Lặp lại hoặc tái sinh**: Vòng lặp tiếp diễn cho tới khi người chơi bị hạ
   gục; sau khi chết, người chơi có thể chơi lại từ đầu.

```
        ┌────────────────────────────────────────────┐
        │                                             │
        ▼                                             │
   [Sinh ra] → [Ăn thức ăn] → [Tích lũy XP] → [Tiến hóa]
        ▲                                          │
        │                                          ▼
   [Chơi lại] ◄── [Bị hạ gục] ◄── [Săn mồi / Sinh tồn]
```

### 2.2. Vòng lặp sinh tồn (Survival Loop)

Song song với vòng lặp tiến hóa, người chơi liên tục cân nhắc rủi ro:

- **Kẻ săn mồi**: Sinh vật bậc cao hơn có thể cắn và hạ gục người chơi.
- **Phản công**: Người chơi vẫn có thể cắn lại sinh vật bậc cao hơn; do khác bậc
  nên đòn cắn vẫn gây sát thương, tạo cơ hội lật ngược tình thế theo nhóm.
- **Kỹ năng**: Mỗi sinh vật có một kỹ năng riêng (tăng tốc, phòng thủ, đòn cận
  chiến hoặc đòn khu vực) dùng để tấn công hoặc thoát hiểm.
- **Áp lực môi trường**: Một số sinh vật (loài đại dương) bắt buộc phải ở trong
  môi trường phù hợp, nếu rời đi quá lâu sẽ mất mạng.

### 2.3. Vòng lặp mô phỏng phía máy chủ (Server Simulation Loop)

Toàn bộ trạng thái trò chơi do máy chủ quản lý (mô hình *server-authoritative*).
Máy chủ chạy một vòng lặp cố định **20 nhịp/giây (tick)**. Mỗi nhịp thực hiện tuần
tự các bước:

| Thứ tự | Bước xử lý | Mô tả |
|--------|-----------|-------|
| 1 | Xử lý input | Áp dụng hướng di chuyển, tăng tốc, kỹ năng của từng người chơi |
| 2 | Kiểm tra sinh tồn môi trường | Trừ thời gian sinh tồn cho loài đại dương khi rời nước |
| 3 | Cập nhật lưới không gian | Đưa toàn bộ thực thể vào lưới để truy vấn va chạm nhanh |
| 4 | Va chạm thức ăn | Người chơi chạm thức ăn đủ điều kiện → cộng XP, xóa thức ăn |
| 5 | Săn mồi giữa người chơi | Xử lý các đòn cắn giữa những người chơi ở gần nhau |
| 6 | Kiểm tra tiến hóa | Gửi lựa chọn tiến hóa khi người chơi đủ XP |
| 7 | Tái tạo thức ăn | Xóa thức ăn cũ và sinh thêm thức ăn nếu chưa đủ số lượng |
| 8 | Phát trạng thái | Gửi snapshot trạng thái tới các client trong tầm nhìn |

Sau mỗi nhịp, máy chủ gửi cho mỗi người chơi một *snapshot* chỉ chứa các thực thể
nằm trong bán kính tầm nhìn (mặc định 2000 đơn vị) nhằm giảm dung lượng truyền tải.

---

## 3. CƠ CHẾ TRÒ CHƠI

### 3.1. Điều khiển và di chuyển

- **Di chuyển theo con trỏ chuột**: Sinh vật luôn di chuyển về hướng con trỏ so
  với tâm màn hình. Khoảng cách con trỏ quyết định cường độ (tốc độ tương đối).
- **Tăng tốc (boost)**: Khi kích hoạt, tốc độ di chuyển tăng **1.5 lần**.
- **Giới hạn tốc độ phía máy chủ**: Mọi chuyển động được máy chủ kiểm tra để chống
  gian lận; client không tự quyết định vị trí.
- **Ảnh hưởng môi trường**: Khi sinh vật ở sai vùng môi trường của nó, tốc độ giảm
  còn **0.75 lần**; ở đúng môi trường thì giữ nguyên (hệ số 1.0).

### 3.2. Hệ thống sinh vật và bậc tiến hóa

Trò chơi có **47 sinh vật** chia thành **15 bậc (tier)**, trải trên 3 vùng môi
trường: Đồng bằng (Land), Đại dương (Ocean) và Bắc cực (Arctic).

- Mỗi sinh vật được định nghĩa bởi: bậc, tốc độ, bán kính (kích thước), lượng máu,
  ngưỡng XP cần để tiến hóa, môi trường và kỹ năng.
- **Sinh vật khởi đầu** người chơi được chọn: Chuột (Mouse – Đồng bằng),
  Tôm (Shrimp – Đại dương), Sóc chuột (Chipmunk – Bắc cực).
- Bậc cuối cùng đặc biệt là **Rồng Đen (Black Dragon)** — chỉ mở khóa từ sinh vật
  bậc 14 (Rồng, Kraken hoặc Yeti) khi đạt **1.000.000 XP**.

**Bảng ngưỡng XP để tiến hóa theo bậc:**

| Bậc | XP yêu cầu | Bậc | XP yêu cầu |
|-----|-----------|-----|-----------|
| 1 | 0 | 9 | 16.000 |
| 2 | 50 | 10 | 32.000 |
| 3 | 200 | 11 | 64.000 |
| 4 | 500 | 12 | 125.000 |
| 5 | 1.000 | 13 | 250.000 |
| 6 | 2.000 | 14 | 500.000 |
| 7 | 4.000 | 15 | 1.000.000 |
| 8 | 8.000 | | |

### 3.3. Cơ chế tiến hóa

- Khi người chơi đạt đủ XP cho bậc kế tiếp, máy chủ gửi danh sách **lựa chọn tiến
  hóa** (các sinh vật ở bậc tiếp theo thuộc nhiều môi trường khác nhau).
- Người chơi chọn một sinh vật; máy chủ kiểm tra tính hợp lệ rồi cập nhật loài,
  kích thước, tốc độ, máu và hồi đầy máu.
- Sau khi tiến hóa, người chơi được đưa tới vị trí phù hợp với môi trường của sinh
  vật mới (ví dụ tiến hóa thành loài đại dương sẽ được đưa xuống vùng biển).

### 3.4. Hệ thống thức ăn

Thức ăn xuất hiện theo môi trường và có **yêu cầu bậc tối thiểu** để ăn được.

| Thức ăn | XP | Bậc tối thiểu | Môi trường |
|---------|----|---------------|------------|
| Berry (Quả mọng) | 5 | 1 | Đồng bằng |
| Arctic Berry | 12 | 1 | Bắc cực |
| Banana (Chuối) | 15 | 1 | Đồng bằng |
| Seaweed (Rong biển) | 20 | 1 | Đại dương |
| Coconut (Dừa) | 25 | 2 | Đồng bằng |
| Snail (Ốc sên) | 25 | 1 | Đồng bằng |
| Meat (Thịt) | 50 | 3 | Đồng bằng |
| Watermelon (Dưa hấu) | 80 | 4 | Đồng bằng |

- Thức ăn được sinh tự động và duy trì ở một số lượng tối đa trong bản đồ.
- Người chơi chỉ cần chạm vào thức ăn (va chạm hình tròn) và đạt bậc tối thiểu để
  ăn; XP được cộng ngay và thức ăn biến mất.

### 3.5. Hệ thống máu và săn mồi (cắn)

Khác với ý tưởng "ăn tức thì", cơ chế săn mồi thực tế dựa trên **đòn cắn nhiều nhát**:

- **Máu = số nhát cắn để hạ gục**, tăng dần theo bậc: bậc 1 có 2 máu, mỗi bậc cao
  hơn thêm 1 máu, bậc 14 có 16 máu và bậc 15 (Rồng Đen) có 20 máu. Sinh vật càng
  cao bậc càng khó bị hạ.
- **Điều kiện thực hiện một đòn cắn**:
  - Hai sinh vật phải **khác bậc** (cùng bậc không cắn được nhau).
  - Có **va chạm** (hai hình tròn chạm nhau).
  - Kẻ tấn công phải **hướng mặt về con mồi** (trong góc quạt 120°).
  - Tuân thủ **thời gian hồi cắn**: mỗi cặp tấn công chỉ cắn lại sau 20 nhịp (1 giây).
- **Mỗi nhát cắn** trừ 1 máu của mục tiêu và **cướp 10% XP** của mục tiêu.
- **Đòn kết liễu** (khi máu mục tiêu còn tối thiểu): mục tiêu bị hạ gục và kẻ tấn
  công **cướp toàn bộ XP** còn lại.

### 3.6. Cơ chế phản công

Vì đòn cắn chỉ yêu cầu **khác bậc** (không bắt buộc kẻ tấn công phải mạnh hơn),
sinh vật bậc thấp vẫn có thể cắn lại sinh vật bậc cao hơn:

- Điều này cho phép con mồi **phản công** hoặc phối hợp theo nhóm để hạ một mục
  tiêu lớn.
- Tuy nhiên, do sinh vật bậc cao có nhiều máu hơn và thường mạnh hơn, phản công
  đơn lẻ khó thành công — tạo nên sự cân bằng rủi ro/phần thưởng.

### 3.7. Hệ thống kỹ năng

Mỗi sinh vật sở hữu một kỹ năng, dùng chung **thời gian hồi 100 nhịp (5 giây)**.
Các nhóm kỹ năng chính:

| Nhóm kỹ năng | Ví dụ | Hiệu ứng |
|--------------|-------|----------|
| Tăng tốc (Dash) | dash, fire_dash, ink_dash | Tăng tốc tức thời (~3 lần) |
| Lao húc (Charge) | charge | Lao nhanh về phía trước (~3.2 lần) |
| Trượt băng (Ice Slide) | ice_slide | Tăng tốc, mạnh hơn khi ở Bắc cực (3.4 lần) |
| Phòng thủ (Guard) | shell_guard, inflate_guard | Giảm 50% sát thương, chậm lại trong 40 nhịp |
| Cận chiến (Melee) | claw, croc_bite, back_kick | Gây sát thương trong góc quạt phía trước |
| Đòn khu vực (Pulse) | roar_pulse, wave_pulse, freeze_pulse | Gây sát thương mọi mục tiêu trong bán kính |

### 3.8. Cơ chế chết và chơi lại

- Người chơi bị hạ gục khi hết máu (bị cắn) hoặc khi loài đại dương ở ngoài nước
  quá lâu (thời gian sinh tồn ~10 giây).
- Khi chết, máy chủ gửi thông báo kèm nguyên nhân (bị ăn, hết thời gian sinh tồn)
  và thông tin kẻ hạ gục.
- Màn hình kết thúc hiển thị và người chơi có thể chơi lại từ sinh vật khởi đầu.

### 3.9. Đồng bộ mạng và chống gian lận

- Mô hình **server-authoritative**: máy chủ quyết định toàn bộ kết quả (XP, va
  chạm, cái chết, tiến hóa); client chỉ gửi input và hiển thị.
- Máy chủ **lọc tầm nhìn** bằng lưới không gian, chỉ gửi các thực thể trong bán
  kính 2000 đơn vị để tối ưu băng thông.
- Client dùng **nội suy (interpolation)** giữa các snapshot để chuyển động mượt.

---

## 4. THIẾT KẾ BẢN ĐỒ TRÒ CHƠI

### 4.1. Tổng quan bản đồ

Bản đồ là một thế giới 2D hình chữ nhật, chia sẻ chung cho tất cả người chơi trong
cùng một phòng. Toàn bộ thực thể (người chơi, thức ăn) tồn tại trong một mặt phẳng
tọa độ duy nhất và được quản lý bởi máy chủ.

### 4.2. Phân vùng môi trường (Biome)

Bản đồ được chia thành 3 vùng môi trường dựa trên tọa độ:

| Môi trường | Vị trí trên bản đồ | Đặc điểm |
|------------|--------------------|----------|
| Đại dương (Ocean) | Dải bên trái (28% chiều rộng đầu tiên) | Loài dưới nước, thức ăn rong biển |
| Bắc cực (Arctic) | Phần dưới bên phải (từ 64% chiều cao trở xuống) | Loài xứ lạnh, thức ăn quả mọng bắc cực |
| Đồng bằng (Land) | Phần còn lại (trên bên phải) | Vùng lớn nhất, đa dạng thức ăn nhất |

```
             Chiều rộng
    0%     28%                  100%
    ┌───────┬──────────────────┐  0%
    │       │                  │
    │ ĐẠI   │    ĐỒNG BẰNG      │
    │ DƯƠNG │      (LAND)       │
    │(OCEAN)│                  │  64%
    │       ├──────────────────┤
    │       │    BẮC CỰC        │
    │       │    (ARCTIC)       │
    └───────┴──────────────────┘  100%
                Chiều cao
```

### 4.3. Cơ chế theo môi trường

- **Sinh ra theo môi trường**: Người chơi và thức ăn được sinh ra tại vùng phù
  hợp với môi trường của chúng.
- **Hệ số di chuyển**: Sinh vật ở đúng môi trường của mình di chuyển bình thường
  (×1.0); nếu ở sai môi trường, tốc độ giảm còn ×0.75.
- **Sinh tồn dưới nước**: Sinh vật đại dương chỉ hồi đầy thời gian sinh tồn khi ở
  trong vùng đại dương; nếu rời khỏi nước quá ~10 giây sẽ chết. Cơ chế này giữ các
  loài dưới nước gắn với vùng biển.

### 4.4. Vùng đặc biệt (Final)

Bậc cuối cùng (Rồng Đen) không gắn với một vùng môi trường cụ thể. Sinh vật này
không chịu phạt tốc độ khi di chuyển và có thể hoạt động khắp bản đồ, phản ánh vị
thế "bá chủ" ở đỉnh chuỗi thức ăn.

### 4.5. Tối ưu không gian (Spatial Grid)

Để xử lý va chạm và tầm nhìn hiệu quả trên bản đồ lớn, máy chủ chia thế giới thành
một **lưới ô vuông (spatial grid)**. Mỗi nhịp, các thực thể được đưa vào ô tương
ứng, giúp:

- Truy vấn thức ăn/người chơi lân cận nhanh (tránh so sánh toàn bộ với nhau).
- Lọc thực thể trong tầm nhìn của từng người chơi để tạo snapshot gọn nhẹ.
- Hỗ trợ bản đồ lớn với nhiều thực thể mà vẫn giữ hiệu năng ổn định.
