# TJ Shield

TJ Shield chặn quảng cáo, tracker và mã độc cho toàn bộ máy Android bằng VPN cục bộ lọc DNS. Không cần root, không gửi dữ liệu ra ngoài.

Điểm khác biệt đang phát triển: **Chế độ Game**. Quảng cáo trong game vẫn bị chặn như bình thường; khi bạn muốn xem quảng cáo có thưởng để nhận vật phẩm, có thể mở chặn trong vài phút, chỉ riêng cho game đó, rồi tự chặn lại.

## Trạng thái

| Giai đoạn | Nội dung | Trạng thái |
| --- | --- | --- |
| 0 | Nhập mã nguồn BlockAds, đổi tên thành TJ Shield | Xong |
| 1 | Luật cho phép/chặn theo app có hạn giờ | Đang làm |
| 2 | TTL ngắn cho phản hồi bị chặn của app game (engine Go) | Chưa |
| 3 | Giao diện Chế độ Game, thông báo gợi ý, Quick Settings tile | Chưa |
| 4 | Bộ tên miền SDK quảng cáo và thử trên game thật | Chưa |

## Build

```sh
./gradlew assembleGithubDebug
```

Cần JDK 17 và Android SDK. Engine Go (`tunnel.aar`) được tải sẵn từ bản phát hành của BlockAds; khi `tunnel/` bị sửa, xem [docs/TUNNEL.md](docs/TUNNEL.md) để tự build.

## Nguồn gốc và giấy phép

TJ Shield dựa trên [BlockAds](https://github.com/pass-with-high-score/blockads-android) của nhóm pass-with-high-score, nhập từ commit `5f4a8f0d36f45382157aab48f28c837c210987f8` (17/11/2026). README gốc nằm ở [docs/UPSTREAM_README.md](docs/UPSTREAM_README.md).

Mã nguồn phát hành theo giấy phép **GNU GPL v3.0**, giống bản gốc. Xem [LICENSE](LICENSE).
