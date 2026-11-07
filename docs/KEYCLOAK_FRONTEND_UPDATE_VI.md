# Cập nhật giao diện React dùng Keycloak

## Phạm vi

Khôi phục trang `/login`, Navbar và điều hướng theo vai trò. Theo yêu cầu làm gọn giao diện, `/login` mở thẳng form username/password Keycloak; `/register` mở thẳng form tự đăng ký. React không thu mật khẩu. Không thay đổi cấu hình bảo mật backend hoặc tự động liên kết user MySQL.

## Luồng sau thay đổi

1. Chưa đăng nhập, mở `/books` hoặc `/readers`: React chuyển về `/login` và lưu đường dẫn trong router state.
2. Trang đăng nhập tự gọi adapter chuyển đến Keycloak và dùng đường dẫn nội bộ cần mở làm redirect URI. Nếu chuyển hướng lỗi, hiện nút thử lại.
3. Quay lại: adapter xử lý callback trước router; route bảo vệ kiểm tra role rồi hiển thị trang.
4. Kiểm tra SSO khi tải lại dùng URL hiện tại, không ép mọi trang về `/books`.
5. Đăng xuất quay về `/login`; lỗi chuyển hướng đăng nhập/đăng xuất hiện thông báo có thể thử lại.

Chỉ chấp nhận đường dẫn trở về thuộc cùng ứng dụng; không nhận URL ngoài hoặc đường dẫn đăng nhập gây vòng lặp. Mặc định sau đăng nhập trực tiếp là `/books`.

## Các trang

| Trang | Hành vi |
|---|---|
| `/login` | Mở thẳng form Keycloak; card chờ/lỗi tối giản |
| `/register` | Mở thẳng form tự đăng ký Keycloak |
| `/books` | Danh sách và tìm sách thật từ API; chưa hiển thị thao tác ghi |
| `/readers` | Danh sách hồ sơ MySQL, chỉ dành cho thủ thư; chưa hiển thị sửa tài khoản |
| `/dashboard`, `/loans`, `/my-loans` | Có điều hướng và quyền tương ứng; thông báo đang cập nhật, không gọi API đang bị chặn |
| Trang không tồn tại | Hiện 404 trong ứng dụng, không chuyển âm thầm về sách |

Các file trang nghiệp vụ gốc vẫn còn. Khi hoàn thành liên kết định danh và mở các API, bỏ chế độ `readOnly` và gắn lại các trang nghiệp vụ phù hợp. Danh sách độc giả hiện là dữ liệu MySQL, không phải danh sách tài khoản lấy trực tiếp từ Keycloak.

## Cấu hình Keycloak cần giữ

- Realm: `library`; client: `library-frontend`; public client; Standard flow; PKCE S256.
- Valid redirect URIs: `http://localhost:3000/*`.
- Valid post logout redirect URIs: `http://localhost:3000/*`.
- Web origins: `http://localhost:3000`.
- Audience access token: có `library-api`.

Mở frontend bằng `localhost:3000`, không tự đổi hostname. Realm đã bật tự đăng ký và role mặc định `READER`. Theme Keycloak đơn giản nằm ở `infra/keycloak/themes/library`; cách triển khai được ghi trong `infra/keycloak/README.md`.

## Kiểm tra

```powershell
cd react-frontend
npm.cmd test -- --watchAll=false --runInBand
npm.cmd run build
```

Kiểm tra thủ công: mở `/books` khi đã logout, xác nhận `/login`; đăng nhập Keycloak; kiểm tra tên user và sách; mở trang độc giả với hai role; đăng xuất; kiểm tra hiển thị trên màn hình hẹp.

Kiểm thử tự động dùng adapter và API giả lập để kiểm tra hành vi React. Chúng không thay thế kiểm thử end-to-end với phiên Keycloak thật.

Tham khảo: [JavaScript adapter của Keycloak](https://www.keycloak.org/securing-apps/javascript-adapter).
