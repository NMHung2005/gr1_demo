# Hoàn thiện Keycloak và hồ sơ nghiệp vụ

## Yêu cầu đã thống nhất

Giữ dữ liệu hiện có: 6 hồ sơ người dùng, 10 phiếu mượn và danh mục sách. Không xóa user Keycloak hoặc tài khoản quản trị. Liên kết xác thực bằng `sub` của realm `library`; không tự nhận lịch sử chỉ vì trùng username/email.

## Thiết kế

- `users.id` vẫn là khóa chính nghiệp vụ để giữ nguyên khóa ngoại và lịch sử.
- Thêm `users.keycloak_id` duy nhất. Hồ sơ cũ có thể chưa được liên kết; hồ sơ mới luôn có subject đã xác minh.
- Username trở thành thông tin hiển thị, không còn khóa định danh duy nhất. Hai nguồn cũ/mới trùng username vẫn không bị gắn nhầm lịch sử.
- Keycloak quản lý mật khẩu và quyền đăng nhập. Trạng thái `enabled` trong MySQL chỉ cho biết độc giả có được mượn thêm sách không; không bị login ghi đè.
- `CurrentUserService` tạo hồ sơ trong transaction riêng và xử lý cạnh tranh bằng unique key + đọc lại sau rollback. Không dùng khóa JVM để giả định chỉ có một backend.
- Các API mượn/trả dùng ID hồ sơ đã được backend xác định. Reader không được chọn userId khác; quyền sở hữu được kiểm tra bằng ID.
- Mở CRUD sách cho thủ thư; mở lại thống kê/mượn/trả theo role. Quản lý hồ sơ không còn nhận mật khẩu hoặc tạo user đăng nhập trong MySQL.
- Migration có bản sao lưu và không xóa phiếu mượn. Dữ liệu xác thực cũ được loại bỏ khỏi code; bảng/cột legacy không tự xóa nếu chưa cần cho migration.

## Kế hoạch thực hiện

1. Viết kiểm thử cho tạo hồ sơ, cạnh tranh, username trùng, giữ đình chỉ mượn, phân quyền và chống truy cập chéo.
2. Thêm model/repository/service định danh; đổi `/me`; gỡ auth/JWT tự viết và seeder user.
3. Chuyển controller/service mượn sang ID; kiểm tra người nhận phiếu hợp lệ; khóa bản ghi khi cập nhật tồn kho và trả sách.
4. Đồng bộ React với `/me`, mở lại trang nghiệp vụ, quản lý trạng thái mượn của hồ sơ mà không quản lý mật khẩu.
5. Backup MySQL; áp dụng migration tương thích; chỉ liên kết hồ sơ cũ khi người dùng chọn mapping cụ thể.
6. Chạy kiểm thử backend trên database H2 riêng, kiểm thử frontend và production build; kiểm tra runtime local nếu có thể; rà soát bảo mật độc lập.

## Điều kiện kiểm chứng

- Cùng subject không tạo trùng hồ sơ, kể cả request đồng thời.
- Trùng username không chiếm hồ sơ cũ.
- Đăng nhập không bỏ trạng thái đình chỉ mượn.
- Reader chỉ xem/trả phiếu của mình và không mượn hộ bằng cách sửa request.
- Thủ thư quản lý được sách và phiếu; reader không gọi được CRUD sách hoặc quản lý user.
- Migration giữ nguyên số phiếu, user cũ, ID và khóa ngoại.
- React chờ hồ sơ sẵn sàng trước khi mở nghiệp vụ, có thể thử lại khi đồng bộ lỗi.

## Nhật ký

- Đã kiểm kê database trước thay đổi: 6 users, 10 loans, 11 refresh tokens cũ. Người dùng chọn giữ dữ liệu.
- Làm trực tiếp trên workspace hiện tại theo yêu cầu để thay đổi có thể dùng ngay; không đổi nhánh hay commit hộ người dùng.
- Người dùng duyệt mapping: Keycloak reader01 → MySQL ID 5 (4 phiếu); Keycloak librarian01 → MySQL admin ID 3. Không liên kết các hồ sơ khác.
- Giữ cột password và bảng refresh_tokens legacy trong MySQL để không xóa dữ liệu lịch sử; source mới không dùng hoặc lưu thêm mật khẩu/token vào chúng.

## Giải thích từng nhóm thay đổi

### 1. User và UserRepository — nối danh tính với hồ sơ

`User` thêm `keycloakId`, bỏ thuộc tính mật khẩu trong Java. `users.id` vẫn giữ nguyên; tất cả khóa ngoại của phiếu mượn tiếp tục dùng ID cũ. `UserRepository.findByKeycloakId()` tìm hồ sơ theo subject, không dùng username để quyết định quyền sở hữu.

Username không còn unique trong database vì là thuộc tính có thể đổi, và hồ sơ cũ chưa liên kết có thể trùng tên với user mới. Unique key quan trọng là `keycloak_id`. Các bản ghi legacy có keycloak_id NULL vẫn giữ được lịch sử; code không cho mượn thêm dưới một hồ sơ chưa liên kết.

`@DynamicUpdate` chỉ ghi các trường thực sự thay đổi. Điều này tránh việc đồng bộ tên/email từ token vô tình ghi lại trạng thái enabled cũ khi thủ thư đang cập nhật quyền mượn.

### 2. CurrentUserService và /me — tạo hồ sơ một lần

Service chỉ nhận Authentication chứa JWT đã xác thực, đọc sub và authorities. Nó tìm hồ sơ theo sub; nếu chưa có thì tạo, nếu có thì cập nhật thông tin danh tính và role từ token. Không tự thay enabled về true.

Mỗi lần đồng bộ chạy trong transaction riêng. Hai request đăng nhập lần đầu đồng thời có thể cùng thử insert; unique key chỉ cho một bản ghi. Request còn lại rollback transaction lỗi rồi đọc lại hồ sơ đã được tạo trong transaction mới.

`GET /api/auth/me` trả thêm `id` (MySQL), `fullName`, `role`, `enabled` bên cạnh subject/username/email/authorities. User mới không cần được thủ thư tạo một bản ghi MySQL thủ công.

### 3. LoanController và LoanService — quyền sở hữu theo ID

Luồng mới:

```text
JWT đã xác minh → sub → CurrentUserService → users.id → loans.user_id
```

Độc giả gửi bookId; backend tự xác định userId. Nếu request cố chèn userId khác thì backend không dùng nó cho luồng độc giả. API xem và trả phiếu kiểm tra ID chủ sở hữu; vi phạm trả 403.

Thủ thư được xem toàn bộ phiếu và thống kê. API lập phiếu cho độc giả yêu cầu một userId của hồ sơ READER đã liên kết; không coi thủ thư là người tự mượn chỉ vì kế thừa default role READER.

Khóa ghi bản ghi sách khi mượn để hai request không cùng lấy bản cuối. Khóa phiếu khi trả để không cộng tồn kho hai lần. Cập nhật quá hạn dùng UPDATE có điều kiện status=BORROWED; không ghi đè phiếu đã RETURNED nếu có request đồng thời.

### 4. SecurityConfig — mở lại đúng API

| API | Quyền |
|---|---|
| GET /api/auth/me, GET /api/books | READER hoặc LIBRARIAN |
| POST/PUT/DELETE /api/books | LIBRARIAN |
| GET /api/users và các API đọc user | LIBRARIAN |
| PUT /api/users/{id}/borrowing-status | LIBRARIAN |
| /api/loans/stats, /api/loans/overdue | LIBRARIAN |
| /api/loans, /api/loans/** | READER hoặc LIBRARIAN; kiểm tra chủ sở hữu trong service |
| Các API khác | Chặn mặc định |

Issuer và audience Keycloak giữ nguyên. Không mở permitAll cho nghiệp vụ, không bật password grant cho client React.

### 5. Quản lý độc giả và bỏ auth cũ

UserService/UserController không còn tạo tài khoản đăng nhập, nhận mật khẩu, đổi role hoặc xóa user trong MySQL. Tài khoản và quyền đăng nhập do Keycloak quản lý. Thủ thư vẫn có thể tạm dừng/cho phép mượn thông qua endpoint borrowing-status.

`enabled=false` có nghĩa tạm dừng mượn mới trong thư viện. Độc giả vẫn được đăng nhập, xem và trả sách đang mượn. Khóa đăng nhập toàn bộ tài khoản là thao tác khác trong Keycloak; không nhầm hai trạng thái này.

Đã gỡ AuthService, JwtUtil, JwtAuthFilter, CustomUserDetailsService, model/repository refresh token, DTO login/register/token cũ, dependency jjwt và cấu hình app.jwt. Seeder mới chỉ giữ sách mẫu, không tạo lại admin/reader mẫu mỗi lần backend khởi động.

Cột password và bảng refresh_tokens cũ vẫn giữ trong MySQL để không xóa dữ liệu đã có; password được cho phép NULL. Source mới không đọc hoặc ghi chúng. Có thể dọn dữ liệu legacy sau khi bạn không còn cần phục hồi cơ chế cũ, nhưng không cần làm việc đó để luồng mới hoạt động.

### 6. React — chờ hồ sơ trước khi mở nghiệp vụ

AuthContext vẫn lấy danh tính/role từ adapter, đồng thời gọi /api/auth/me để lấy ID hồ sơ. ProtectedRoute chờ hồ sơ sẵn sàng; khi lỗi có nút thử lại thay vì tạo vòng đăng nhập. Không lưu password hoặc token vào localStorage.

App mở lại BookPage đầy đủ, DashboardPage, LoanPage và MyLoansPage. ReaderPage hiển thị hồ sơ cũ/mới, trạng thái liên kết và quyền mượn; không còn form nhập mật khẩu. Tài khoản có cả READER và LIBRARIAN được hiển thị theo vai trò thủ thư, không hiện nút tự mượn của độc giả.

### 7. Migration đã thực hiện trên MySQL local

- Backup trước thay đổi: `.local-backups/librarymanagement-before-keycloak-20261107-222537.sql` (thư mục đã được ignore khỏi Git; chứa dữ liệu riêng, không chia sẻ công khai).
- Áp dụng `spring-backend/scripts/migrate-keycloak.sql` để thêm subject, unique key và điều chỉnh username/password legacy.
- Áp dụng mapping do bạn duyệt trong `spring-backend/scripts/link-approved-keycloak-users.sql`.
- Giữ đủ 6 hồ sơ, 10 phiếu, 15 sách; giữ nguyên ID và khóa ngoại.
- reader01 liên kết ID 5, tiếp tục có 4 phiếu mượn.
- librarian01 liên kết ID 3; tên/email còn thiếu trên Keycloak được bổ sung từ hồ sơ admin ID 3 đã được duyệt. Khi /me đồng bộ, username hiển thị của hồ sơ này trở thành librarian01.
- Bốn hồ sơ cũ khác vẫn tồn tại, chưa được liên kết; lịch sử của họ không chuyển cho reader01.

SQL mapping là dữ liệu riêng của lần chuyển đổi này. Không chạy nó trên database khác mà chưa kiểm tra ID và subject thực tế. Trên database mới, Hibernate tạo schema từ model; không cần áp dụng mapping của database cũ.

## Cách chạy sau thay đổi

Khởi động lại backend để gỡ class/bean auth cũ khỏi tiến trình đang chạy:

```powershell
cd spring-backend
.\mvnw.cmd spring-boot:run
```

Mở terminal khác:

```powershell
cd react-frontend
npm.cmd start
```

Đăng xuất/đăng nhập lại nếu trình duyệt đang giữ trạng thái cũ. Thử reader01 tại /books và /my-loans; thử librarian01 tại /dashboard, /readers và /loans. Tài khoản mới tự đăng ký sẽ có hồ sơ MySQL khi gọi /me lần đầu.

## Kiểm chứng

Kết quả cuối: **11 kiểm thử backend đạt**, **9 kiểm thử frontend đạt**, `mvnw.cmd clean verify` và frontend production build thành công. JAR đóng gói đã được kiểm tra không chứa class xác thực cũ hoặc thư viện jjwt.

- Backend dùng H2 riêng cho test, không reset hoặc tạo dữ liệu thử trong MySQL thật. Chạy `mvnw.cmd clean verify`.
- Frontend chạy `npm.cmd test -- --watchAll=false --runInBand` và `npm.cmd run build`.
- Đã chạy luồng Authorization Code + PKCE thật qua Keycloak và bản backend đóng gói trên cổng kiểm tra 8082: reader01 → ID 5, 4 phiếu, /api/users trả 403; librarian01 → ID 3, xem 6 hồ sơ, 10 phiếu, thống kê tổng 10.
- Tiến trình kiểm tra cổng 8082 được dừng sau khi hoàn tất; không thay backend cổng 8080 của bạn.
- Script kiểm tra local nằm ở `spring-backend/scripts/smoke-keycloak.py`, lấy tài khoản từ biến môi trường KC_SMOKE_USERNAME/KC_SMOKE_PASSWORD; không in hoặc lưu token. Chỉ dùng cho môi trường loopback local, không phải client production.
- Rà soát độc lập đã tìm và xác nhận sửa lỗi nút Borrow cho tài khoản hai role và race cập nhật quá hạn/trả sách.

Đây là hoàn thiện luồng danh tính/nghiệp vụ trên môi trường local. Cấu hình SMTP/quên mật khẩu, chính sách phiên/thu hồi, HTTPS và triển khai production vẫn cần làm riêng khi đưa ứng dụng ra ngoài máy.
