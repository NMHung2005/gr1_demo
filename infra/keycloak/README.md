# Giao diện đăng nhập thư viện

Theme `library` kế thừa form của Keycloak, chỉ thêm CSS: nền sáng, card nhỏ, màu xanh của ứng dụng. Không thay luồng xác thực, validation, PKCE hoặc xử lý mật khẩu.

## Áp dụng lên container đang có

Từ thư mục gốc project:

```powershell
docker cp ./infra/keycloak/themes/library library-keycloak:/opt/keycloak/themes/library
```

Trong Admin Console, chọn realm `library`:

1. Realm settings → Themes → Login theme: `library`.
2. Realm settings → Login → User registration: On.
3. Kiểm tra role mặc định (User registration → Default roles, hoặc realm role `default-roles-library` theo phiên bản): thêm `READER`, không thêm `LIBRARIAN`.

Client vẫn dùng Standard flow, PKCE S256, Client authentication Off, Direct access grants Off. Giữ redirect/post logout URI của React và audience `library-api`.

Với container development hiện tại, có thể copy theme mà không tạo lại container. Khi tạo container mới, cần copy lại, mount thư mục themes hoặc đóng gói theme vào image; theme không được lưu trong volume dữ liệu `/opt/keycloak/data`.

## Sử dụng

- Mở `http://localhost:3000/login`: tự mở form username/password của Keycloak.
- Mở `http://localhost:3000/register`: tự mở form tự đăng ký của Keycloak.
- React chỉ hiện một card chuyển hướng ngắn, có nút thử lại nếu lỗi; không thu mật khẩu hay dùng password grant.
- Khi đã có phiên, Keycloak có thể đăng nhập luôn mà không yêu cầu nhập lại mật khẩu.
- Tài khoản tự đăng ký có role `READER`; liên kết hồ sơ MySQL/mượn trả là bước riêng chưa hoàn thành.

Đăng ký và đăng nhập không bắt buộc thủ thư cấp tài khoản sau khi bật tự đăng ký. Nếu cần quay về giao diện mặc định, chọn lại login theme trước đó trong Realm settings → Themes.

Tham khảo: [Keycloak themes](https://www.keycloak.org/ui-customization/themes), [JavaScript adapter](https://www.keycloak.org/securing-apps/javascript-adapter).
