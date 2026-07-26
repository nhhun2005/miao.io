# Danh sách các con vật đã triển khai trong game

Nguồn dữ liệu: `backend/src/main/java/com/mimope/server/game/data/`

- Con vật chơi được: `AnimalDefinition.java`
- Con vật AI (thức ăn di chuyển): `FoodDefinition.java`

Ghi chú về biome:
- **LAND** = đồng cỏ/đất liền
- **OCEAN** = đại dương
- **ARCTIC** = vùng băng giá
- **FINAL** = dạng tiến hóa cuối cùng

---

## Con vật chơi được (46 con)

Chuỗi tiến hóa chia theo tier (cấp) và biome.

| Tier | LAND | OCEAN | ARCTIC | XP yêu cầu |
|:----:|------|-------|--------|:----------:|
| 1 | Mouse (chuột) 🌱 | Shrimp (tôm) 🌱 | Chipmunk (sóc chuột) 🌱, Lemming (chuột lemming) | 0 |
| 2 | Rabbit (thỏ) | Trout (cá hồi) | Arctic Hare (thỏ Bắc Cực) | 50 |
| 3 | Mole (chuột chũi) | Crab (cua) | Penguin (chim cánh cụt) | 200 |
| 4 | Pig (lợn) | Seahorse (cá ngựa) | Seal (hải cẩu) | 500 |
| 5 | Deer (hươu) | Squid (mực ống) | Reindeer (tuần lộc) | 1.000 |
| 6 | Fox (cáo) | Jellyfish (sứa) | Arctic Fox (cáo Bắc Cực) | 2.000 |
| 7 | Zebra (ngựa vằn), Donkey (lừa) | Turtle (rùa) | Musk Ox (bò xạ hương) | 4.000 |
| 8 | Cheetah (báo săn) | Stingray (cá đuối) | Wolf (sói) | 8.000 |
| 9 | Gorilla (khỉ đột) | Pufferfish (cá nóc) | Snow Leopard (báo tuyết) | 16.000 |
| 10 | Bear (gấu) | Swordfish (cá kiếm) | Walrus (hải mã) | 32.000 |
| 11 | Lion (sư tử), Crocodile (cá sấu) | Octopus (bạch tuộc) | Polar Bear (gấu Bắc Cực) | 64.000 |
| 12 | Rhino (tê giác) | Shark (cá mập) | Wolverine (chồn sói) | 125.000 |
| 13 | Hippo (hà mã) | Killer Whale (cá voi sát thủ) | Mammoth (voi ma mút) | 250.000 |
| 14 | Dragon (rồng) | Kraken (bạch tuộc khổng lồ) | Yeti (người tuyết) | 500.000 |
| 15 | — Black Dragon (rồng đen) — dạng tiến hóa cuối 🐉 | | | 1.000.000 |

🌱 = con vật khởi đầu (starter). Chỉ `mouse`, `shrimp`, `chipmunk` là starter hợp lệ.

🐉 Black Dragon là **dạng cuối cùng** (biome FINAL), chỉ mở khóa từ Dragon / Kraken / Yeti khi đạt đủ 1.000.000 XP.

### Chi tiết đầy đủ

| ID | Tên | Tier | Biome | Speed | Radius | XP |
|----|-----|:----:|-------|:-----:|:------:|---:|
| mouse | Mouse | 1 | LAND | 200 | 22 | 0 |
| shrimp | Shrimp | 1 | OCEAN | 205 | 20 | 0 |
| chipmunk | Chipmunk | 1 | ARCTIC | 198 | 21 | 0 |
| lemming | Lemming | 1 | ARCTIC | 190 | 19 | 0 |
| rabbit | Rabbit | 2 | LAND | 190 | 28 | 50 |
| arctichare | Arctic Hare | 2 | ARCTIC | 188 | 27 | 50 |
| trout | Trout | 2 | OCEAN | 196 | 26 | 50 |
| mole | Mole | 3 | LAND | 178 | 32 | 200 |
| crab | Crab | 3 | OCEAN | 170 | 34 | 200 |
| penguin | Penguin | 3 | ARCTIC | 182 | 31 | 200 |
| pig | Pig | 4 | LAND | 175 | 34 | 500 |
| seahorse | Seahorse | 4 | OCEAN | 185 | 33 | 500 |
| seal | Seal | 4 | ARCTIC | 180 | 35 | 500 |
| deer | Deer | 5 | LAND | 180 | 44 | 1.000 |
| squid | Squid | 5 | OCEAN | 174 | 40 | 1.000 |
| reindeer | Reindeer | 5 | ARCTIC | 176 | 42 | 1.000 |
| fox | Fox | 6 | LAND | 185 | 38 | 2.000 |
| jellyfish | Jellyfish | 6 | OCEAN | 165 | 42 | 2.000 |
| arcticfox | Arctic Fox | 6 | ARCTIC | 182 | 38 | 2.000 |
| zebra | Zebra | 7 | LAND | 176 | 46 | 4.000 |
| donkey | Donkey | 7 | LAND | 172 | 46 | 4.000 |
| turtle | Turtle | 7 | OCEAN | 158 | 48 | 4.000 |
| muskox | Musk Ox | 7 | ARCTIC | 168 | 48 | 4.000 |
| cheetah | Cheetah | 8 | LAND | 205 | 48 | 8.000 |
| stingray | Stingray | 8 | OCEAN | 176 | 50 | 8.000 |
| wolf | Wolf | 8 | ARCTIC | 186 | 49 | 8.000 |
| gorilla | Gorilla | 9 | LAND | 166 | 54 | 16.000 |
| pufferfish | Pufferfish | 9 | OCEAN | 160 | 52 | 16.000 |
| snowleopard | Snow Leopard | 9 | ARCTIC | 195 | 52 | 16.000 |
| bear | Bear | 10 | LAND | 160 | 58 | 32.000 |
| swordfish | Swordfish | 10 | OCEAN | 188 | 56 | 32.000 |
| walrus | Walrus | 10 | ARCTIC | 154 | 60 | 32.000 |
| lion | Lion | 11 | LAND | 170 | 60 | 64.000 |
| croc | Crocodile | 11 | LAND | 158 | 62 | 64.000 |
| octopus | Octopus | 11 | OCEAN | 162 | 62 | 64.000 |
| polarbear | Polar Bear | 11 | ARCTIC | 158 | 63 | 64.000 |
| rhino | Rhino | 12 | LAND | 166 | 66 | 125.000 |
| shark | Shark | 12 | OCEAN | 184 | 64 | 125.000 |
| wolverine | Wolverine | 12 | ARCTIC | 174 | 62 | 125.000 |
| hippo | Hippo | 13 | LAND | 152 | 72 | 250.000 |
| killerwhale | Killer Whale | 13 | OCEAN | 176 | 74 | 250.000 |
| mammoth | Mammoth | 13 | ARCTIC | 145 | 82 | 250.000 |
| dragon | Dragon | 14 | LAND | 162 | 88 | 500.000 |
| kraken | Kraken | 14 | OCEAN | 150 | 90 | 500.000 |
| yeti | Yeti | 14 | ARCTIC | 154 | 88 | 500.000 |
| blackdragon | Black Dragon | 15 | FINAL | 156 | 105 | 1.000.000 |

---

## Con vật AI (thức ăn di chuyển)

Định nghĩa trong `FoodDefinition.java`.

| ID | Spawn weight |
|----|:------------:|
| snail | 8 |

---

## Tổng kết

- **46** con vật chơi được (bao gồm dạng cuối Black Dragon), trải dài **15 tier** qua 3 biome LAND / OCEAN / ARCTIC.
- **1** con vật AI (snail).
- Mỗi con vật có đúng **một** sprite duy nhất — game không có hệ thống skin/biến thể ngoại hình.
