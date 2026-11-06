# Keycloak trong dự án quản lý thư viện — tài liệu học và thực hành

Ngày tổng hợp: **07/11/2026**. Đối tượng: người mới bắt đầu học authentication, authorization và Keycloak.

Tài liệu tổng hợp quá trình đã thực hiện trong cuộc trao đổi, đối chiếu với source code tại thời điểm viết. Các thao tác trong Keycloak/Postman được ghi lại từ hướng dẫn và kết quả bạn cung cấp; không phải bản export trực tiếp của cấu hình Keycloak.

## Mục lục

1. [Trạng thái dự án sau bài thực hành](#1-trạng-thái-dự-án-sau-bài-thực-hành)
2. [Vì sao dùng Keycloak?](#2-vì-sao-dùng-keycloak)
3. [Các khái niệm nền tảng](#3-các-khái-niệm-nền-tảng)
4. [Mật khẩu, HTTPS và hashing](#4-mật-khẩu-https-và-hashing)
5. [Token và các trường trong JWT](#5-token-và-các-trường-trong-jwt)
6. [Authorization Code Flow với PKCE](#6-authorization-code-flow-với-pkce)
7. [Chạy Keycloak bằng Docker](#7-chạy-keycloak-bằng-docker)
8. [Realm, tài khoản và role](#8-realm-tài-khoản-và-role)
9. [Client cho React và Postman](#9-client-cho-react-và-postman)
10. [Audience mapper](#10-audience-mapper)
11. [Các endpoint của Keycloak](#11-các-endpoint-của-keycloak)
12. [Thay đổi trong Spring Boot](#12-thay-đổi-trong-spring-boot)
13. [Backend xử lý một request như thế nào?](#13-backend-xử-lý-một-request-như-thế-nào)
14. [Lấy token và kiểm tra bằng Postman](#14-lấy-token-và-kiểm-tra-bằng-postman)
15. [Kết quả đã kiểm chứng](#15-kết-quả-đã-kiểm-chứng)
16. [Các lỗi thực tế và cách xử lý](#16-các-lỗi-thực-tế-và-cách-xử-lý)
17. [Logging và phương pháp chẩn đoán](#17-logging-và-phương-pháp-chẩn-đoán)
18. [Những việc chưa hoàn tất trong dự án](#18-những-việc-chưa-hoàn-tất-trong-dự-án)
19. [Bài tập tự kiểm tra kiến thức](#19-bài-tập-tự-kiểm-tra-kiến-thức)
20. [Tài liệu chính thức và bảng tra nhanh](#20-tài-liệu-chính-thức-và-bảng-tra-nhanh)

## 1. Trạng thái dự án sau bài thực hành

### 1.1. Đã hoàn thành

- Chạy được Keycloak và sử dụng realm `library`.
- Đăng nhập được bằng tài khoản của realm.
- Lấy được access token trong Postman bằng Authorization Code + PKCE.
- Spring Boot được cấu hình thành OAuth2 Resource Server.
- Backend kiểm tra token và yêu cầu audience `library-api`.
- Ánh xạ realm role `READER`, `LIBRARIAN` sang quyền Spring Security.
- API `/api/auth/me` trả được danh tính lấy từ token.
- Bạn xác nhận kết quả kiểm tra phân quyền đúng: độc giả xem sách, không xem danh sách người dùng; thủ thư xem được danh sách người dùng.

### 1.2. Chưa hoàn thành

- React chưa chuyển sang `keycloak-js`, vẫn gọi API đăng nhập cũ.
- Chưa liên kết định danh Keycloak với hồ sơ độc giả trong MySQL.
- Chưa mở lại API mượn/trả và các API ghi dữ liệu trong cấu hình thực hành.
- Chưa chuyển chức năng tạo/sửa/khóa tài khoản sang quy trình có Keycloak.
- Chưa dọn toàn bộ JWT service, refresh token và cấu hình xác thực cũ.
- Chưa triển khai cấu hình production.

**Kết luận về phạm vi:** backend đã xác thực và phân quyền bằng Keycloak trong bài kiểm tra API. Toàn bộ ứng dụng chưa chuyển đổi xong sang Keycloak.

### 1.3. Các địa chỉ đang sử dụng

| Thành phần | Địa chỉ | Vai trò |
|---|---|---|
| React | `http://localhost:3000` | Giao diện ứng dụng |
| Spring Boot | `http://localhost:8080` | API nghiệp vụ |
| Keycloak | `http://localhost:8081` | Hệ thống danh tính và cấp token |
| MySQL của project | `localhost:3306/librarymanagement` | Dữ liệu nghiệp vụ |
| Realm issuer | `http://localhost:8081/realms/library` | Định danh nơi cấp token |

Không nhầm cổng `8080` bên trong container Keycloak với cổng `8080` của Spring Boot trên máy. Docker ánh xạ Keycloak ra cổng máy `8081`.

## 2. Vì sao dùng Keycloak?

### 2.1. Cơ chế cũ của project

```text
React gửi username/password
    → AuthController
    → AuthService tìm user trong MySQL
    → BCrypt kiểm tra mật khẩu
    → JwtUtil tạo access token
    → Backend tạo/lưu refresh token
    → React lưu kết quả đăng nhập
```

Backend tự quản lý cả mật khẩu, đăng nhập, token và nghiệp vụ thư viện. Cách này giúp học nền tảng nhưng khi phát triển thêm sẽ cần xử lý nhiều vấn đề như reset mật khẩu, xác minh email, nhiều ứng dụng dùng chung danh tính và quản lý phiên.

### 2.2. Vai trò của Keycloak

Keycloak là hệ thống quản lý danh tính và truy cập, thường gọi là **IAM — Identity and Access Management**. Nó cung cấp các chức năng như quản lý tài khoản, đăng nhập, quản lý phiên, role, đăng nhập một lần và tích hợp với các nhà cung cấp danh tính khác.

Trong project này, dùng nó để chuyển phần kiểm tra mật khẩu và cấp token ra khỏi backend thư viện. [Giới thiệu tích hợp ứng dụng của Keycloak](https://www.keycloak.org/securing-apps/overview)

| Nhiệm vụ | Thành phần phụ trách sau khi chuyển đổi hoàn tất |
|---|---|
| Kiểm tra mật khẩu, cấp token | Keycloak |
| Hiển thị giao diện sách và mượn/trả | React |
| Xác minh access token, kiểm tra quyền API | Spring Security trong backend |
| Kiểm tra độc giả chỉ xem phiếu mượn của mình | Code nghiệp vụ backend |
| Lưu sách, phiếu mượn, hồ sơ độc giả | MySQL của project |

Keycloak không tự hiểu các quy tắc như “sách đã hết”, “độc giả có phiếu mượn quá hạn”, hay “phiếu này thuộc người nào”. Backend vẫn phải kiểm tra các quy tắc đó.

## 3. Các khái niệm nền tảng

| Khái niệm | Định nghĩa | Ví dụ trong project |
|---|---|---|
| Authentication | Xác thực danh tính: người gọi là ai? | Xác định người gọi là `reader01` |
| Authorization | Kiểm tra quyền: người đó được làm gì? | `reader01` không được xem `/api/users` |
| IAM | Quản lý danh tính và truy cập | Keycloak quản lý người dùng và đăng nhập |
| Identity Provider / IdP | Hệ thống xác thực và cung cấp thông tin danh tính | Keycloak |
| OAuth 2.0 | Framework ủy quyền truy cập tài nguyên bằng token | Client dùng access token gọi API |
| OpenID Connect / OIDC | Lớp giao thức danh tính xây trên OAuth 2.0 | Đăng nhập và nhận ID token |
| Resource Owner | Chủ thể có quyền đối với tài nguyên | Người dùng đăng nhập |
| Authorization Server | Hệ thống cấp token sau khi xử lý quy trình cho phép/xác thực | Keycloak |
| Resource Server | API kiểm tra token để bảo vệ tài nguyên | Spring Boot |
| Client | Ứng dụng yêu cầu token và sử dụng dịch vụ đăng nhập | `library-frontend` |
| Realm | Không gian quản lý user, client, role và chính sách | `library` |
| Principal | Danh tính đang được xác thực trong ứng dụng | Danh tính từ JWT |
| Claim | Một trường thông tin trong token | `sub`, `iss`, `aud`, `exp` |
| Role | Vai trò nghiệp vụ | `READER`, `LIBRARIAN` |
| GrantedAuthority | Quyền mà Spring Security dùng để quyết định truy cập | `ROLE_READER` |
| Scope | Phạm vi được yêu cầu/cấp trong OAuth/OIDC | `openid profile email` |
| Client scope của Keycloak | Nhóm cấu hình mapper/scope gắn vào client | Scope chứa mapper đưa claim vào token |
| SSO | Single Sign-On: dùng phiên đăng nhập chung cho các client phù hợp | Đăng nhập ứng dụng khác trong cùng hệ thống mà có thể không phải nhập lại mật khẩu |

### 3.1. Client ID khác user ID

```text
library-frontend → định danh ứng dụng
reader01        → username của người dùng
sub             → định danh ổn định của người dùng trong issuer
library-api     → audience API mà chúng ta đặt trong bài thực hành
```

Một client có thể được nhiều user sử dụng. Tên `library-frontend` không phải username.

### 3.2. Public client và confidential client

- **Public client:** không thể bảo vệ một client secret như bí mật lâu dài, ví dụ JavaScript chạy trong trình duyệt.
- **Confidential client:** có môi trường backend để bảo vệ thông tin xác thực client.

React thuộc nhóm public client. Client ID không phải bí mật. Không đặt client secret trong source React hoặc biến môi trường được đóng gói vào frontend. [Tài liệu JavaScript adapter](https://www.keycloak.org/securing-apps/javascript-adapter)

### 3.3. Role và quyền sở hữu dữ liệu

Role `READER` cho biết một loại quyền chung, nhưng không cho biết user được truy cập phiếu mượn nào.

Ví dụ:

```text
Có ROLE_READER?
    → Được dùng chức năng xem phiếu mượn của mình.

Phiếu mượn 123 có thuộc user hiện tại?
    → Backend phải kiểm tra thêm.
```

Ẩn nút trong React không thay thế kiểm tra quyền ở backend.

## 4. Mật khẩu, HTTPS và hashing

### 4.1. Hai lớp bảo vệ khác nhau

| Cơ chế | Mục đích |
|---|---|
| HTTPS/TLS | Bảo vệ dữ liệu trên đường truyền |
| Password hashing | Bảo vệ mật khẩu được lưu trong cơ sở dữ liệu |

```text
Mật khẩu trong bộ nhớ trình duyệt
    → TLS mã hóa khi gửi qua mạng
    → Nơi kết thúc TLS giải mã
    → Hệ thống xác thực nhận mật khẩu để kiểm tra
```

Vì vậy “raw password trong request object” không đồng nghĩa với “password đọc được trên đường truyền HTTPS”. DevTools vẫn có thể hiển thị payload vì trình duyệt biết dữ liệu trước khi mã hóa.

Nếu dùng HTTP qua mạng không được bảo vệ, password và token có nguy cơ bị đọc. HTTPS cần cho trang đăng nhập và các API đã xác thực. [Hướng dẫn Authentication của OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

### 4.2. BCrypt trong cơ chế cũ

Khi đăng ký:

```java
passwordEncoder.encode(rawPassword)
```

Khi đăng nhập:

```java
passwordEncoder.matches(rawPassword, storedHash)
```

Không giải mã hash ra mật khẩu gốc. Không tạo một BCrypt hash mới rồi so sánh chuỗi trực tiếp, vì salt khiến các lần encode cùng mật khẩu có thể cho kết quả khác nhau.

Keycloak có chính sách lưu mật khẩu riêng; không mặc định suy ra nó dùng BCrypt chỉ vì project cũ dùng BCrypt.

### 4.3. Vì sao không chỉ hash ở frontend?

Nếu backend chấp nhận cố định `hash(password)` như bằng chứng đăng nhập, kẻ lấy được giá trị đó có thể gửi lại nó. Giá trị hash trở thành thông tin đăng nhập thay thế. Cách này không thay thế TLS.

### 4.4. Sau khi dùng Keycloak

Theo luồng chuyển hướng đang học, người dùng nhập mật khẩu tại Keycloak. Backend thư viện nhận access token. Khi triển khai thật, cả giao tiếp với Keycloak và API cần được bảo vệ phù hợp bằng TLS; không ghi password hoặc token đầy đủ vào log.

## 5. Token và các trường trong JWT

### 5.1. Ba loại token cần phân biệt

| Loại | Dùng để làm gì? | Có gửi làm Bearer token cho API thư viện không? |
|---|---|---|
| Access token | Truy cập API trong phạm vi được cấp | Có |
| ID token | Cho client biết thông tin về lần xác thực và user | Không dùng thay access token |
| Refresh token | Xin token mới theo chính sách phiên | Không; dùng với token endpoint |

Tuổi thọ token do cấu hình Keycloak quyết định. Các giá trị `app.jwt.*` của code cũ không điều khiển tuổi thọ token Keycloak.

### 5.2. JWT là gì?

JWT là một định dạng biểu diễn claims. Trong bài thực hành, access token là JWT có chữ ký, thường có ba phần:

```text
base64url(header).base64url(payload).signature
```

Ví dụ header minh họa:

```json
{
  "alg": "RS256",
  "kid": "id-cua-khoa-ky",
  "typ": "JWT"
}
```

Payload minh họa; các giá trị thời gian chỉ để học cấu trúc, không dùng token này để kiểm thử:

```json
{
  "iss": "http://localhost:8081/realms/library",
  "sub": "dinh-danh-user-keycloak",
  "aud": ["account", "library-api"],
  "azp": "library-frontend",
  "iat": 1700000000,
  "exp": 1700000300,
  "preferred_username": "reader01",
  "email": "reader01@example.com",
  "realm_access": {
    "roles": ["READER"]
  }
}
```

### 5.3. Ý nghĩa các trường

| Trường | Ý nghĩa | Ghi nhớ |
|---|---|---|
| `alg` | Thuật toán chữ ký | Server phải giới hạn thuật toán phù hợp, không tin tùy ý header |
| `kid` | Định danh khóa | Giúp chọn khóa công khai phù hợp trong JWKS |
| `iss` | Issuer, nơi cấp token | Phải khớp issuer backend tin tưởng |
| `sub` | Subject, định danh user | Dùng cùng issuer để xác định danh tính |
| `aud` | Audience, đối tượng token hướng đến | Phải chứa `library-api` trong bài này |
| `azp` | Authorized party | Thường cho biết client được cấp token; không thay thế kiểm tra audience |
| `exp` | Thời điểm hết hạn | NumericDate theo giây từ Unix epoch |
| `iat` | Thời điểm phát hành | Không phải thời hạn sống |
| `nbf` | Chưa được dùng trước thời điểm này, nếu có | Cũng liên quan kiểm tra thời gian |
| `preferred_username` | Tên đăng nhập dễ đọc | Có thể đổi, không nên là khóa liên kết lâu dài |
| `email` | Email theo claim được phát hành | Có email không đồng nghĩa đã xác minh email |
| `email_verified` | Trạng thái xác minh email, nếu có | Cần chính sách phù hợp nếu nghiệp vụ dựa vào email |
| `realm_access.roles` | Realm role trong cấu hình Keycloak đang dùng | Converter hiện lấy `READER`/`LIBRARIAN` ở đây |
| `resource_access` | Có thể chứa client roles | Converter hiện tại không đọc vùng này |
| `scope` | Scope được cấp trong token, tùy cấu hình | Khác danh sách role nghiệp vụ |

### 5.4. Chữ ký không phải mã hóa payload

Payload của signed JWT thường đọc được bằng cách decode Base64URL. Chữ ký cho phép phát hiện token bị sửa và xác minh nơi ký khi kiểm tra đúng khóa/chính sách; không che giấu nội dung payload.

Không đưa password hay bí mật ứng dụng vào JWT. Tự decode payload chỉ để quan sát dữ liệu, không chứng minh token hợp lệ. Không gửi token đang hoạt động vào website giải mã không tin cậy.

### 5.5. Bearer nghĩa là gì?

```http
Authorization: Bearer <access_token>
```

Bearer token là thông tin mà bên nắm giữ có thể dùng để truy cập trong phạm vi được cấp. Bảo vệ nó như thông tin đăng nhập. Client không gửi lại mật khẩu trong mỗi lần gọi API.

## 6. Authorization Code Flow với PKCE

Đây là luồng đã dùng trong Postman và sẽ dùng khi tích hợp React.

### 6.1. Trình tự

```text
Client / Postman         Trình duyệt          Keycloak          Spring Boot
      |                       |                   |                   |
      |-- tạo verifier ------>|                   |                   |
      |-- mở URL đăng nhập ---------------------->|                   |
      |   kèm client_id, redirect_uri, challenge  |                   |
      |                       |-- nhập tài khoản ->|                   |
      |                       |<-- redirect + code-|                   |
      |<-- nhận code ---------|                   |                   |
      |-- code + verifier đến token endpoint ---->|                   |
      |<-- access token, ID token, refresh token -|                   |
      |-- Authorization: Bearer access token ----------------------->|
      |<------------------------ kết quả API ------------------------|
```

Sơ đồ lược bỏ một số tham số và bước kiểm tra để dễ học. Thư viện OIDC cần xử lý các kiểm tra giao thức tương ứng, không chỉ thao tác chuyển hướng.

### 6.2. PKCE bảo vệ điều gì?

1. Client tạo chuỗi ngẫu nhiên `code_verifier`.
2. Với S256, tạo challenge:

   ```text
   code_challenge = BASE64URL(SHA256(code_verifier))
   ```

3. Gửi challenge trong yêu cầu authorization.
4. Khi đổi code lấy token, gửi verifier.
5. Keycloak đối chiếu verifier với challenge đã gắn vào code.

Người chỉ chặn được authorization code sẽ chưa đủ để đổi lấy token nếu không có verifier tương ứng. PKCE không phải client secret và không thay thế HTTPS. [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636)

### 6.3. Các tham số thường gặp

| Tham số | Vai trò |
|---|---|
| `client_id` | Xác định ứng dụng đang đăng nhập |
| `redirect_uri` | Địa chỉ nhận kết quả; phải được client cho phép |
| `response_type=code` | Yêu cầu authorization code |
| `scope=openid profile email` | Yêu cầu OIDC và các nhóm thông tin tương ứng |
| `state` | Liên kết response với request để chống nhầm lẫn/giả mạo luồng |
| `nonce` | Liên kết ID token với yêu cầu khi sử dụng; thư viện phải kiểm tra tương ứng |
| `code_challenge` | Giá trị được tạo từ verifier |
| `code_challenge_method=S256` | Cách tạo challenge bằng SHA-256 |
| `grant_type=authorization_code` | Dùng ở token endpoint để đổi code |
| `code_verifier` | Chứng minh client đang đổi code là bên khởi tạo PKCE |

Không tự thay quy trình này bằng cách chuyển username/password từ React sang API thư viện rồi sang Keycloak. Bài học đang dùng luồng chuyển hướng chuẩn.

## 7. Chạy Keycloak bằng Docker

### 7.1. Lệnh đã hướng dẫn

Mở Docker Desktop, chờ engine sẵn sàng:

```powershell
docker info
```

Tạo container lần đầu:

```powershell
docker run -d --name library-keycloak -p 127.0.0.1:8081:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=kcadmin -e KC_BOOTSTRAP_ADMIN_PASSWORD=kcadmin123 -v library-keycloak-data:/opt/keycloak/data quay.io/keycloak/keycloak:26.8.1 start-dev
```

Phiên bản `26.8.1` là phiên bản image được dùng trong hướng dẫn, không phải kết quả kiểm kê image đang chạy trên máy. Có thể xác nhận bằng `docker inspect library-keycloak --format '{{.Config.Image}}'`.

`kcadmin123` là mật khẩu ví dụ cho local trong bài thực hành, không dùng cho môi trường triển khai thực tế.

| Thành phần lệnh | Giải thích |
|---|---|
| `docker run` | Tạo container mới |
| `-d` | Chạy nền |
| `--name library-keycloak` | Tên để dùng với lệnh start/stop/logs |
| `127.0.0.1:8081:8080` | Cổng máy 8081 tới cổng container 8080, chỉ bind loopback IPv4 |
| `KC_BOOTSTRAP_ADMIN_USERNAME` | Username quản trị ban đầu |
| `KC_BOOTSTRAP_ADMIN_PASSWORD` | Password quản trị ban đầu |
| `-v library-keycloak-data:/opt/keycloak/data` | Giữ dữ liệu phát triển trong named volume |
| `quay.io/keycloak/keycloak:26.8.1` | Image và tag |
| `start-dev` | Chế độ phát triển |

Bootstrap credentials phục vụ tạo quản trị viên ban đầu; sửa biến môi trường không phải cách mặc định để đổi mật khẩu tài khoản đã tồn tại. Volume cũng không thay thế quy trình backup.

### 7.2. Lệnh vận hành thường dùng

```powershell
docker ps
docker logs -f library-keycloak
docker logs --since 5m library-keycloak
docker stop library-keycloak
docker start library-keycloak
```

`Ctrl+C` khi đang xem `docker logs -f` chỉ thoát theo dõi log. Không cần chạy lại `docker run` mỗi lần; nếu container đã tồn tại, dùng `docker start`.

### 7.3. Local khác production

`start-dev` và database phát triển đi kèm phục vụ học trên máy. MySQL `librarymanagement` của project không tự trở thành database Keycloak. Khi triển khai thực tế cần thiết kế riêng cấu hình hostname, TLS, database được hỗ trợ, tài nguyên, backup và quản lý bí mật.

Tham khảo: [Keycloak Docker quickstart](https://www.keycloak.org/getting-started/getting-started-docker), [chạy container](https://www.keycloak.org/server/containers).

## 8. Realm, tài khoản và role

### 8.1. Realm

Trong Admin Console tại `http://localhost:8081`, tạo realm `library`. Chọn đúng realm trước khi tạo user, client hoặc role.

Realm `master` dùng cho quản trị Keycloak. Các tài khoản ứng dụng của bài thực hành nằm trong realm `library`.

### 8.2. Tài khoản quản trị và tài khoản ứng dụng

| Tài khoản mẫu trong hướng dẫn | Vai trò |
|---|---|
| `kcadmin` | Quản trị Keycloak |
| `reader01` | Độc giả, realm role `READER` |
| `librarian01` | Thủ thư, realm role `LIBRARIAN` |

Mật khẩu tài khoản ứng dụng dùng trong bài: `reader123` và `librarian123`; chỉ là dữ liệu thực hành local. Không nhầm `admin` trong dữ liệu mẫu MySQL với quản trị viên Keycloak.

### 8.3. Tạo user

Trong realm `library`:

1. `Users → Add user`.
2. Điền username, bật Enabled.
3. Lưu user.
4. `Credentials → Set password`.
5. Đặt Temporary Off nếu muốn đăng nhập ngay mà không đổi mật khẩu ở bài thực hành.
6. `Role mapping → Assign role` để gán role tương ứng.

`Temporary On` thường dẫn tới yêu cầu đổi mật khẩu khi đăng nhập. Đây là một loại required action, không phải lỗi token.

### 8.4. Màn hình Update Account Information đã gặp

Sau đăng nhập, Keycloak yêu cầu bổ sung Email, First name, Last name vì hồ sơ thiếu các trường bắt buộc theo cấu hình hiện tại.

Ví dụ đã hướng dẫn:

```text
Email: reader01@example.com
First name: An
Last name: Nguyen
```

Điền và Submit để hoàn tất. Màn hình này chưa phải bằng chứng rằng React/Spring Boot đã tích hợp; nó chỉ nằm trong quy trình tài khoản Keycloak.

### 8.5. Role

Tạo tại `Realm roles`:

```text
READER
LIBRARIAN
```

Sau đó gán vào từng user. Role names trong converter hiện phân biệt chữ hoa/thường. `reader` không được code hiện tại coi là `READER`.

Các thay đổi role sẽ thể hiện trong token mới theo chính sách phát hành; đừng dùng token cũ để kết luận cấu hình vừa sửa không hoạt động. [Hướng dẫn quản trị Keycloak](https://www.keycloak.org/docs/latest/server_admin/)

## 9. Client cho React và Postman

### 9.1. Cấu hình client `library-frontend`

| Trường | Giá trị trong hướng dẫn | Ý nghĩa |
|---|---|---|
| Client type | OpenID Connect | Giao thức dùng để tích hợp |
| Client ID | `library-frontend` | Định danh ứng dụng |
| Client authentication | Off | Public client, không dùng secret |
| Standard flow | On | Bật Authorization Code Flow |
| Implicit flow | Off | Không dùng implicit flow trong bài |
| Direct access grants | Off | Không dùng password grant |
| Root URL | `http://localhost:3000` | URL gốc của ứng dụng |
| Home URL | `http://localhost:3000` | Trang chủ/quay về ứng dụng theo ngữ cảnh |
| Valid redirect URIs | `http://localhost:3000/*` và callback Postman cụ thể | Địa chỉ được phép nhận kết quả đăng nhập |
| Valid post logout redirect URIs | `http://localhost:3000/*` | Địa chỉ được phép về sau logout |
| Web origins | `http://localhost:3000` | Origin trình duyệt được phép gọi các endpoint hỗ trợ CORS của Keycloak |
| PKCE method | S256 | Ràng buộc code exchange theo PKCE SHA-256 |

Tên/vị trí trường có thể khác theo phiên bản Admin Console. Không đổi sang confidential client chỉ để thử sửa lỗi redirect hoặc audience.

### 9.2. Ba loại URL rất dễ nhầm

| Trường | Kiểm soát điều gì? |
|---|---|
| Valid redirect URIs | Nơi chuyển về sau quá trình authorization |
| Valid post logout redirect URIs | Nơi chuyển về sau logout |
| Web origins | Chính sách CORS cho request từ JavaScript trình duyệt |

Origin gồm scheme + host + port, không gồm đường dẫn. Vì vậy `http://localhost:3000` là origin; `http://localhost:3000/books` là URL có path.

`Root URL` hoặc `Home URL` không thay thế allowlist redirect. Liên kết “Back to Application” về React không chứng minh redirect Postman đã được cho phép.

### 9.3. Callback cho Postman

Một callback đã dùng trong hướng dẫn:

```text
https://oauth.pstmn.io/v1/browser-callback
```

Postman có thể hiển thị callback khác theo cách sử dụng. Giá trị thực tế Postman gửi phải được khai báo chính xác trong client. Không mặc định thay `/callback` bằng `/browser-callback` hoặc ngược lại.

Cho bài học, Postman dùng chung client `library-frontend`. Có thể tách client kiểm thử riêng về sau để quản lý redirect và scope rõ hơn.

Wildcard ở path React phục vụ phát triển. Không cấu hình redirect toàn cục `*` để né lỗi.

## 10. Audience mapper

### 10.1. Issuer, client và audience khác nhau

```text
iss = ai cấp token?
azp = client nào được cấp token trong ngữ cảnh này?
aud = token dành cho bên nhận nào?
```

Trong bài:

```text
iss: http://localhost:8081/realms/library
azp: library-frontend
aud: có library-api
```

`library-api` là custom audience. Trong cấu hình này không bắt buộc tạo một client có ID `library-api` chỉ để có giá trị audience đó.

### 10.2. Tạo mapper

```text
Clients
→ library-frontend
→ Client scopes
→ library-frontend-dedicated
→ Mappers
→ Configure a new mapper
→ Audience
```

| Trường | Giá trị |
|---|---|
| Name | `library-api-audience` |
| Included Client Audience | Để trống |
| Included Custom Audience | `library-api` |
| Add to access token | On |
| Add to ID token | Off |

Nếu có thêm tùy chọn liên quan loại access token theo phiên bản, kiểm tra mapper áp dụng vào loại token thực tế đang cấp. Bài học dùng access token JWT thông thường.

Mapper đưa claim vào token khi Keycloak phát hành. Nó không sửa JWT đã phát hành trước đó. [Audience trong Keycloak](https://www.keycloak.org/docs/latest/server_admin/index.html)

### 10.3. Kiểm tra kết quả

Trong `Client scopes → Evaluate`, chọn user và xem Generated access token. `aud` cần là chuỗi `library-api` hoặc danh sách chứa nó:

```json
{
  "aud": ["account", "library-api"]
}
```

Evaluate giúp kiểm tra cấu hình phát hành. Khi gọi API phải kiểm tra rằng Postman đang dùng token mới từ đúng client và đúng scope, không phải token cũ.

## 11. Các endpoint của Keycloak

| Endpoint | Mục đích |
|---|---|
| `http://localhost:8081` | Trang vào Keycloak/Admin Console |
| `http://localhost:8081/realms/library/account/` | Trang quản lý tài khoản của realm |
| `http://localhost:8081/realms/library/.well-known/openid-configuration` | Metadata OIDC, thông tin endpoint và issuer |
| `http://localhost:8081/realms/library/protocol/openid-connect/auth` | Bắt đầu authorization/đăng nhập |
| `http://localhost:8081/realms/library/protocol/openid-connect/token` | Đổi code hoặc refresh token lấy token |
| `http://localhost:8081/realms/library/protocol/openid-connect/certs` | JWKS, bộ khóa công khai |

### 11.1. Discovery document

Ví dụ các trường:

```json
{
  "issuer": "http://localhost:8081/realms/library",
  "authorization_endpoint": "http://localhost:8081/realms/library/protocol/openid-connect/auth",
  "token_endpoint": "http://localhost:8081/realms/library/protocol/openid-connect/token",
  "jwks_uri": "http://localhost:8081/realms/library/protocol/openid-connect/certs"
}
```

### 11.2. JWKS, public key và private key

Trong mô hình chữ ký bất đối xứng của bài:

- Keycloak giữ private key để ký.
- Backend lấy public key để xác minh chữ ký.
- Public key được công khai không cho phép tạo chữ ký hợp lệ như private key.
- `kid` trong JWT giúp tìm khóa tương ứng trong JWKS.

Backend dùng cơ chế quản lý/cache khóa của thư viện; không nhất thiết tải JWKS trong mọi request. Tuy nhiên lần đầu cần khóa, hoặc khi cache/khóa thay đổi, có thể cần kết nối Keycloak. Vì vậy Keycloak mất kết nối vẫn có thể làm xác thực thất bại ở những thời điểm đó.

Không nhầm cache khóa JWKS của thư viện với Redis. Bài này chưa tích hợp Redis.

## 12. Thay đổi trong Spring Boot

### 12.1. Danh sách file liên quan

| File | Thay đổi |
|---|---|
| `spring-backend/pom.xml` | Thêm starter OAuth2 Resource Server |
| `src/main/resources/application.properties` | Thêm issuer, audience và log chẩn đoán |
| `security/JwtAuthFilter.java` | Bỏ `@Component` để filter JWT cũ ngừng tự đăng ký |
| `config/SecurityConfig.java` | Dùng Resource Server, ánh xạ role, quy tắc truy cập thực hành |
| `controller/AuthController.java` | Thay API auth cũ bằng GET `/me` đọc JWT |

Đường dẫn Java được tính từ `spring-backend/src/main/java/com/example/demo/`.

### 12.2. Dependency

Project dùng Spring Boot `4.0.5`, cấu hình `java.version=17`. Thêm vào `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
</dependency>
```

Đây là tên starter phù hợp trong dòng Spring Boot 4. Không tự gán version riêng cho Spring Security để tránh làm lệch bộ dependency được Boot quản lý. [Hướng dẫn chuyển đổi Spring Boot 4](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

Các dependency `jjwt` cũ vẫn còn vì source cũ còn tham chiếu. Việc giữ dependency không có nghĩa nó đang xác minh request Keycloak.

### 12.3. Cấu hình Resource Server

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081/realms/library
spring.security.oauth2.resourceserver.jwt.audiences=library-api
```

`issuer-uri` xác định nơi cấp token được tin tưởng. `audiences` yêu cầu token hướng tới API thư viện. `localhost` và `127.0.0.1` không tự được coi là cùng chuỗi issuer; không đổi issuer tùy tiện để thử sửa lỗi mạng.

Không cần đưa mật khẩu quản trị Keycloak hoặc frontend client secret vào cấu hình Resource Server này. [JWT Resource Server trong Spring Security](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html)

### 12.4. Tắt filter JWT cũ

Trong `JwtAuthFilter.java`, bỏ annotation `@Component` và bỏ việc chèn filter bằng `.addFilterBefore(...)` trong SecurityConfig.

Filter cũ xác minh JWT bằng secret `app.jwt.secret`; token Keycloak dùng cơ chế mới. Để cả hai chạy dễ gây xung đột. Một Filter được khai báo thành Spring bean có thể còn được Boot đăng ký ở tầng servlet, nên chỉ bỏ `.addFilterBefore(...)` chưa chắc đủ.

File filter cũ vẫn tồn tại trong repo. Import `Component` còn sót không làm nó thành bean; annotation mới là phần quyết định ở đây. Có thể dọn import khi dọn code sau.

### 12.5. SecurityConfig hiện tại

```java
package com.example.demo.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/auth/me")
                    .authenticated()
                .requestMatchers(HttpMethod.GET, "/api/books", "/api/books/**")
                    .hasAnyRole("READER", "LIBRARIAN")
                .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/**")
                    .hasRole("LIBRARIAN")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");

            if (realmAccess != null
                    && realmAccess.get("roles") instanceof Collection<?> roles) {
                for (Object value : roles) {
                    if (value instanceof String role
                            && List.of("READER", "LIBRARIAN").contains(role)) {
                        authorities.add(
                            new SimpleGrantedAuthority("ROLE_" + role));
                    }
                }
            }

            return authorities;
        });

        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 12.6. Giải thích từng nhóm cấu hình

| Cấu hình | Ý nghĩa |
|---|---|
| `SecurityFilterChain` | Chuỗi bộ lọc bảo vệ request trước Controller |
| `csrf(...disable())` | Phù hợp với phạm vi API dùng Bearer header đang học; phải đánh giá lại nếu xác thực bằng cookie/BFF |
| `cors(...)` | Tích hợp chính sách CORS của ứng dụng |
| `STATELESS` | Không dùng HTTP session của backend để lưu trạng thái xác thực cho luồng này |
| `authenticated()` | Chỉ cần đã xác thực |
| `hasAnyRole(...)` | Phải có một trong các role được liệt kê |
| `hasRole("LIBRARIAN")` | Kiểm tra authority `ROLE_LIBRARIAN` theo tiền tố mặc định |
| `anyRequest().denyAll()` | Chặn các request ngoài những quy tắc được mở |
| `oauth2ResourceServer(...jwt...)` | Xử lý Bearer JWT bằng Resource Server |
| `httpBasic(...disable())` | Không dùng Basic Authentication |
| `formLogin(...disable())` | Không dùng form đăng nhập do Spring Security cung cấp |

Stateless ở backend không có nghĩa Keycloak không có session. Keycloak vẫn quản lý phiên đăng nhập/SSO theo cấu hình của nó.

`PasswordEncoder` vẫn tồn tại để các bean cũ như `AdminSeeder`, `AuthService` có thể khởi tạo. Nó không kiểm tra access token Keycloak.

### 12.7. Converter role

Token có:

```json
{
  "realm_access": {
    "roles": ["READER"]
  }
}
```

Code ánh xạ thành:

```text
READER → ROLE_READER → hasRole("READER")
```

Converter chỉ nhận hai role đã biết. Nếu tạo role dưới `resource_access` (client roles) thay vì realm roles, code hiện tại sẽ không đọc được. Nếu muốn dùng client roles cần thay converter và thiết kế quyền tương ứng.

### 12.8. AuthController hiện tại

```java
package com.example.demo.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> me(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subject", jwt.getSubject());
        result.put("username", jwt.getClaimAsString("preferred_username"));
        result.put("email", jwt.getClaimAsString("email"));
        result.put("authorities", authentication.getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .toList());

        return result;
    }
}
```

`@AuthenticationPrincipal Jwt` nhận JWT đã được tầng bảo mật xác thực. Controller không tự decode chuỗi token rồi tin nội dung. `Authentication` chứa danh tính và quyền đã được dựng cho request.

Endpoint này không gọi `AuthService` hay tìm user trong MySQL. Vì vậy gọi `/me` thành công chưa chứng minh đã có hồ sơ độc giả tương ứng trong database nghiệp vụ.

### 12.9. CORS hiện có của backend

`CorsConfig.java` đang cho phép origin `http://localhost:3000`, các method GET/POST/PUT/PATCH/DELETE/OPTIONS và cấu hình header/credentials.

Đây là cấu hình của **Spring Boot**, độc lập với Web origins của **Keycloak**. Postman desktop không chịu cơ chế CORS giống JavaScript trong trình duyệt. Sửa CORS không giải quyết lỗi `aud` sai hoặc tải JWKS timeout.

## 13. Backend xử lý một request như thế nào?

Ví dụ:

```http
GET /api/books
Authorization: Bearer <access_token>
```

Trình tự khái quát:

1. Request đi vào security filter chain.
2. Bộ lọc Bearer đọc token từ Authorization header.
3. JWT decoder xử lý cấu trúc JWT, tìm khóa xác minh và kiểm tra chữ ký.
4. Các validator kiểm tra issuer, thời gian và audience đã cấu hình.
5. Converter chuyển claims role sang authorities.
6. Spring tạo đối tượng Authentication và gắn vào SecurityContext của request.
7. Quy tắc URL/method kiểm tra authorities.
8. Nếu được phép, request đến Controller rồi Service/Repository.
9. Backend trả response.

Không có bước so sánh mật khẩu trong luồng request này. Mật khẩu đã được Keycloak kiểm tra ở luồng đăng nhập.

### 13.1. 401 và 403

| Mã | Ý nghĩa thường gặp trong bài |
|---|---|
| 401 Unauthorized | Chưa xác thực: thiếu token, token không hợp lệ/hết hạn hoặc không thể xác minh |
| 403 Forbidden | Đã xác thực nhưng không đủ quyền, hoặc request bị quy tắc denyAll chặn |

Không chỉ nhìn status; cần xem response header và server log để xác định tầng lỗi. Lỗi bên trong có thể bị error handling làm thay đổi response cuối cùng.

### 13.2. Các loại lỗi khác nhau

```text
Invalid redirect_uri
    → Authorization request bị Keycloak từ chối.

Read timed out tại /certs
    → Backend không lấy được khóa xác minh.

The aud claim is not valid
    → Token không thỏa audience backend yêu cầu.

403 khi READER gọi /api/users
    → Phân quyền hoạt động đúng.
```

Không nên sửa đồng thời role, issuer, audience và CORS khi chưa xác định lỗi thuộc bước nào.

## 14. Lấy token và kiểm tra bằng Postman

### 14.1. Cấu hình OAuth 2.0

Tạo request:

```http
GET http://localhost:8080/api/auth/me
```

Trong `Authorization → OAuth 2.0 → Configure New Token`:

| Trường | Giá trị |
|---|---|
| Grant Type | Authorization Code (With PKCE) |
| Callback URL | Callback thực tế Postman hiển thị, đã được cho phép trong client |
| Authorize using browser | Bật |
| Auth URL | `http://localhost:8081/realms/library/protocol/openid-connect/auth` |
| Access Token URL | `http://localhost:8081/realms/library/protocol/openid-connect/token` |
| Client ID | `library-frontend` |
| Client Secret | Trống |
| Scope | `openid profile email` |
| Code Challenge Method | SHA-256 / S256 |
| Client Authentication | Send client credentials in body; không nhập secret |
| Add authorization data to | Request Headers, nếu có trường lựa chọn |

Tên mục có thể đổi theo phiên bản Postman. Điểm bắt buộc là PKCE, callback đúng, public client không secret và gửi access token trong header.

### 14.2. Thao tác

1. Chọn Get New Access Token.
2. Đăng nhập trên Keycloak.
3. Hoàn tất bổ sung hồ sơ/required action nếu có.
4. Quay về Postman.
5. Chọn Use Token với token vừa nhận.
6. Send request.

Có token trong cửa sổ quản lý token chưa có nghĩa request đã dùng token đó. Cần chọn đúng token và nơi gửi header.

### 14.3. Cách kiểm tra tối giản bằng Bearer Token

Nếu cần loại trừ cấu hình helper:

1. Tạo request mới.
2. Chọn `Authorization → Bearer Token`.
3. Dán riêng giá trị access token vào ô Token.
4. Không thêm chữ `Bearer` trong ô này.

Postman tự tạo:

```http
Authorization: Bearer eyJ...
```

Không tạo hai Authorization headers; không gửi token trong query string. Nếu dùng biến như `{{access_token}}`, kiểm tra nó đã có giá trị đúng environment.

### 14.4. Chuyển tài khoản

Khi cần lấy token thủ thư sau token độc giả, phiên SSO trình duyệt có thể vẫn đăng nhập độc giả. Đăng xuất/đổi tài khoản phù hợp rồi kiểm tra `username` tại `/me`.

Hướng dẫn chính thức: [Postman OAuth 2.0](https://learning.postman.com/docs/use/send-requests/authorization/oauth-20/).

## 15. Kết quả đã kiểm chứng

### 15.1. Response thực tế bạn cung cấp

```json
{
  "subject": "8d73fdf9-3016-49e9-b7c7-704a42da3e85",
  "username": "reader01",
  "email": "reader01@example.com",
  "authorities": [
    "FACTOR_BEARER",
    "ROLE_READER"
  ]
}
```

| Trường | Ý nghĩa |
|---|---|
| `subject` | Giá trị `sub` của user Keycloak, không phải ID số trong bảng users |
| `username` | Tên đăng nhập từ claim `preferred_username` |
| `email` | Email từ claim |
| `ROLE_READER` | Role đã được converter ánh xạ |
| `FACTOR_BEARER` | Authority do Spring Security thêm để đánh dấu yếu tố xác thực Bearer trong phiên bản đang dùng |

Không cần tạo role Keycloak tên `FACTOR_BEARER`. Nó cũng không có nghĩa user đã bật MFA.

### 15.2. Ma trận kiểm tra

| Request | Không token | Token READER | Token LIBRARIAN |
|---|---:|---:|---:|
| GET `/api/auth/me` | 401 | 200 | 200 |
| GET `/api/books` | 401 | 200 | 200 |
| GET `/api/users` | 401 | 403 | 200 |

Bạn đã xác nhận các bước phân quyền trong hướng dẫn hoạt động đúng. Đây là bằng chứng xác thực và quy tắc đọc API đã hoạt động; không suy ra các chức năng đang bị chặn cũng đã được kiểm thử.

## 16. Các lỗi thực tế và cách xử lý

### 16.1. Update Account Information

**Hiện tượng:** yêu cầu Email, First name, Last name.

**Giải thích:** hồ sơ thiếu thông tin bắt buộc theo cấu hình user profile/luồng hiện tại.

**Xử lý:** điền và Submit. Không sửa backend vì đây là bước Keycloak hoàn tất tài khoản.

### 16.2. Invalid parameter: redirect_uri

**Nguyên nhân của loại lỗi:** `redirect_uri` trong yêu cầu authorization không khớp địa chỉ được phép cho client.

**Kiểm tra:**

1. Xem `client_id` thực tế trong URL lỗi.
2. Xem `redirect_uri` thực tế.
3. Giải mã URL encoding khi đọc; `%3A` là `:`, `%2F` là `/`.
4. Thêm đúng callback vào Valid redirect URIs của đúng realm/client.
5. Save rồi bắt đầu Get New Access Token lại.

Ví dụ hai địa chỉ khác nhau:

```text
https://oauth.pstmn.io/v1/callback
https://oauth.pstmn.io/v1/browser-callback
```

Đổi Web origins hoặc post logout redirect không thay thế cấu hình login redirect. Không cần khởi động lại backend khi sửa allowlist Keycloak.

### 16.3. 401 nhưng chỉ có log Securing GET

```text
FilterChainProxy : Securing GET /api/auth/me
```

Log chỉ xác nhận request đi vào security chain. Nó chưa chứng minh có token, token hợp lệ hay không hợp lệ.

**Cách chẩn đoán:** kiểm tra Authorization header, response `WWW-Authenticate` và bật log đúng bộ lọc Bearer. Không kết luận ngay là thiếu role.

### 16.4. Bearer token is malformed kèm Read timed out

Log bạn cung cấp chứa:

```text
OAuth2AuthenticationException: Bearer token is malformed
Caused by: SocketTimeoutException: Read timed out
RemoteKeySourceException:
I/O error on GET request for
http://localhost:8081/realms/library/protocol/openid-connect/certs
```

**Kết luận từ bằng chứng:** request xác thực thất bại vì tải JWKS bị read timeout. Dòng thông báo ngoài không đủ để kết luận chuỗi JWT bị hỏng.

Trong quá trình kiểm tra, endpoint `/certs` được truy cập thành công từ PowerShell qua cả `localhost` và `127.0.0.1`, khoảng 0,4 giây và trả 2 khóa. Điều đó chỉ chứng minh endpoint phản hồi tại thời điểm kiểm tra; không xác định nguyên nhân ban đầu và không chứng minh kết nối của tiến trình Java ở thời điểm lỗi cũng tốt.

Sau đó bạn cung cấp lỗi audience, cho thấy request mới đã đi xa hơn bước tải khóa. Không có bằng chứng để khẳng định một bản sửa mạng cụ thể đã giải quyết timeout; nguyên nhân gián đoạn ban đầu chưa được xác nhận.

**Nếu tái diễn:**

```powershell
docker ps
docker logs --since 5m library-keycloak
Invoke-RestMethod 'http://localhost:8081/realms/library/protocol/openid-connect/certs'
```

Đối chiếu thời điểm backend lỗi với log Keycloak, tài nguyên Docker và môi trường mạng/proxy của tiến trình Java. Tăng timeout chỉ nên cân nhắc sau khi xác định nhu cầu, không thay thế việc tìm lỗi kết nối.

Nếu Spring Boot chạy trong container, `localhost` trỏ vào container đó. Đây là tình huống triển khai khác cần thiết kế URL/network và issuer nhất quán, không áp dụng máy móc cấu hình local hiện tại.

### 16.5. The aud claim is not valid

Lỗi thực tế tiếp theo:

```text
InvalidBearerTokenException:
An error occurred while attempting to decode the Jwt:
The aud claim is not valid
```

**Kết luận:** token không đáp ứng audience backend đang yêu cầu `library-api`.

**Cách xử lý đã hướng dẫn:** kiểm tra/tạo Audience mapper đúng client, bật Add to access token, đặt Included Custom Audience là `library-api`, sau đó lấy token mới và Use Token lại.

Sau bước này bạn trả về response `/me` thành công. Giữ kiểm tra audience trong backend; không xóa để né 401.

### 16.6. 403 với độc giả khi gọi /api/users

Đây là kết quả mong đợi. Kiểm tra phân quyền bằng tài khoản thủ thư để xác nhận nhánh được phép.

Nếu `/api/books` trả 403 với token hợp lệ, kiểm tra role đã gán vào user, role nằm ở realm_access hay resource_access, tên role và token mới hay cũ.

## 17. Logging và phương pháp chẩn đoán

### 17.1. Cấu hình đang có trong project

```properties
# Temporary diagnostics for the Keycloak integration.
logging.level.org.springframework.security.web.FilterChainProxy=DEBUG
logging.level.org.springframework.security.oauth2.server.resource=DEBUG
logging.level.org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter=TRACE
logging.level.org.springframework.security.oauth2.jwt=DEBUG
```

Các dòng trên vẫn tồn tại tại thời điểm viết tài liệu. Sau khi hoàn tất giai đoạn chẩn đoán, nên bỏ cấu hình tạm hoặc điều chỉnh mức log theo môi trường. Tài liệu này không tự thay đổi mức log.

### 17.2. Cách đọc

| Thông báo | Diễn giải |
|---|---|
| `Securing GET ...` | Request đã vào security chain |
| `Did not process request since did not find bearer token` | Bộ lọc không tìm được Bearer token |
| `Failed to process authentication request` | Xác thực thất bại; xem exception/caused by |
| `Set SecurityContextHolder to ...` | Bộ lọc đã dựng Authentication thành công |
| `The aud claim is not valid` | Audience không đáp ứng validator |
| `Read timed out` tại `/certs` | Lỗi mạng/đọc response khi lấy khóa |

Thông báo cụ thể phụ thuộc phiên bản thư viện. Có thể đối chiếu [mã nguồn BearerTokenAuthenticationFilter](https://raw.githubusercontent.com/spring-projects/spring-security/7.0.x/oauth2/oauth2-resource-server/src/main/java/org/springframework/security/oauth2/server/resource/web/authentication/BearerTokenAuthenticationFilter.java).

### 17.3. Quy trình chẩn đoán dùng lại

1. Ghi nhận URL, method, status và thời điểm lỗi.
2. Kiểm tra request có đúng một Authorization header không.
3. Kiểm tra đang dùng access token mới của đúng client.
4. Đọc response `WWW-Authenticate`.
5. Đọc exception sâu nhất có liên quan; không chỉ dòng đầu.
6. Xác định tầng: redirect, lấy token, nhận token, lấy khóa, validator hay phân quyền.
7. Chỉ sửa một giả thuyết có bằng chứng.
8. Kiểm tra lại cả trường hợp được phép và bị chặn.

Khi chia sẻ log, không chia sẻ password, full access token, refresh token hoặc client secret. Chỉ cần thông báo lỗi và các giá trị claim cần đối chiếu đã lược bỏ thông tin không cần thiết.

## 18. Những việc chưa hoàn tất trong dự án

### 18.1. React vẫn dùng auth cũ

Trong `react-frontend/src/auth/AuthContext.jsx`, hàm login vẫn gọi:

```javascript
apiClient('/api/auth/login', {
  method: 'POST',
  body: JSON.stringify({ username, password })
})
```

Nhưng AuthController mới không còn endpoint login đó. Vì vậy frontend hiện tại không phải bằng chứng tích hợp Keycloak và cần được chuyển đổi.

Việc dự kiến tiếp theo:

- Khởi tạo `keycloak-js` cho realm/client.
- Chuyển nút đăng nhập sang luồng redirect.
- Dùng access token Keycloak khi gọi API.
- Xử lý refresh/logout và trạng thái khởi tạo.
- Đánh giá lại việc lưu token: code cũ lưu localStorage; hướng tích hợp adapter cần theo chính sách bảo vệ token phù hợp, không sao chép máy móc cơ chế cũ.

Đây là việc tương lai, chưa thực hiện trong tài liệu này.

### 18.2. Liên kết user Keycloak với user MySQL

Hai hệ thống đang có danh tính độc lập:

```text
Keycloak user: sub là UUID
MySQL user: id là Long, có username/password/role riêng
```

`AdminSeeder` tạo tài khoản MySQL không tự tạo user Keycloak. Cùng username không làm hai bản ghi tự liên kết.

Hướng thiết kế cần xem xét là lưu định danh ngoài, ví dụ issuer + subject có ràng buộc duy nhất, trong hồ sơ nghiệp vụ. Dùng `sub` trong issuer tin cậy thay cho username/email có thể thay đổi.

**Không tự động gắn tài khoản cũ chỉ vì trùng email hoặc username mà không có quy trình xác minh quyền sở hữu.** Với dữ liệu thực hành có thể thiết lập liên kết chủ động; dữ liệu thực tế cần quy trình migration/account linking phù hợp.

### 18.3. Vấn đề authentication.getName()

`LoanController` hiện có các lời gọi:

```java
loanService.getLoansByUsername(authentication.getName());
```

Resource Server mặc định dùng `sub` làm tên principal. Trong khi service cũ tìm theo username.

```text
authentication.getName() có thể là UUID
service cũ mong reader01
→ không tìm thấy user hoặc sai luồng liên kết
```

Cần chuyển lớp ánh xạ danh tính và nghiệp vụ sang liên kết đúng trước khi mở lại API. Đổi principal claim sang preferred_username có thể che bớt chênh lệch tạm thời, nhưng không giải quyết đầy đủ việc liên kết danh tính lâu dài.

### 18.4. API hiện đang bị chặn

SecurityConfig chỉ mở GET `/api/auth/me`, `/api/books...`, `/api/users...` theo điều kiện đã nêu. Các API khác bị `denyAll`, gồm mượn/trả và ghi dữ liệu.

Đây là giới hạn có chủ ý của giai đoạn thực hành. Không mở tất cả bằng `permitAll()` để làm giao diện chạy trở lại.

### 18.5. Nguồn sự thật cho tài khoản và quyền

MySQL vẫn có role và enabled của code cũ, Keycloak cũng có role/trạng thái user. Cần quyết định thông tin nào do hệ thống nào quản lý để tránh lệch dữ liệu.

- Xác thực và role API của bài hiện lấy từ Keycloak/token.
- Hồ sơ/nghiệp vụ vẫn ở MySQL.
- Khi chuyển đổi đầy đủ, không giữ hai quy trình đổi mật khẩu hoặc cấp quyền độc lập mà không có thiết kế rõ.
- Đổi/thu hồi role hoặc logout không bảo đảm JWT đã cấp bị vô hiệu ngay trên mọi Resource Server xác minh offline. Chính sách thời hạn token, phiên, refresh và yêu cầu thu hồi phải được thiết kế phù hợp.

### 18.6. Code cũ còn tồn tại

Các phần như `AuthService`, `JwtUtil`, `CustomUserDetailsService`, bảng refresh token, `app.jwt.*`, BCrypt và AdminSeeder còn trong repo. Chúng không tự biến thành cấu hình Keycloak. Chỉ xóa khi đã kiểm tra các bean/phụ thuộc và hoàn thành chuyển đổi tương ứng.

## 19. Bài tập tự kiểm tra kiến thức

### 19.1. Câu hỏi

1. Vì sao React dùng public client và không chứa client secret?
2. Issuer khác audience ở đâu?
3. Vì sao `library-frontend` và `library-api` có giá trị khác nhau?
4. JWT đọc được payload thì còn an toàn ở điểm nào?
5. Vì sao phải lấy token mới sau khi sửa mapper/role?
6. Tại sao user đăng nhập được trên Keycloak nhưng API vẫn có thể trả 401?
7. Tại sao READER gọi `/api/users` trả 403 là đúng?
8. Endpoint `/me` thành công có chứng minh MySQL đã có hồ sơ độc giả không?
9. Vì sao không dùng ID token thay access token?
10. Vì sao `Read timed out` tại `/certs` không nên xử lý bằng việc sửa role?
11. Vì sao `FACTOR_BEARER` không phải role nghiệp vụ hay bằng chứng MFA?
12. Vì sao backend stateless vẫn có thể tồn tại phiên đăng nhập Keycloak?

### 19.2. Đáp án ngắn

1. Code trình duyệt không giữ được bí mật lâu dài; dùng Authorization Code + PKCE.
2. Issuer là nơi cấp; audience là bên nhận token hướng tới.
3. Một giá trị định danh client, một giá trị định danh API đích trong cấu hình audience.
4. Chữ ký và validator ngăn tin token bị sửa/không đúng chính sách; HTTPS bảo vệ đường truyền.
5. JWT đã phát hành không tự viết lại claims.
6. Có thể chưa gửi token, sai loại token, sai issuer/audience, hết hạn hoặc không lấy được khóa.
7. User đã xác thực nhưng không có quyền thủ thư.
8. Không; `/me` hiện đọc JWT, không tra hồ sơ MySQL.
9. Hai loại token có mục đích, bên nhận và quy tắc xác minh khác nhau.
10. Đó là lỗi lấy khóa xác minh, xảy ra trước bước quyết định quyền nghiệp vụ.
11. Nó đánh dấu yếu tố xác thực Bearer do Spring thêm trong phiên bản đang dùng.
12. Backend không dùng HTTP session cho API này, nhưng Keycloak quản lý phiên riêng.

### 19.3. Bài thực hành lặp lại

- Lấy token reader, gọi `/me`, `/books`, `/users` và ghi status.
- Lấy token thủ thư, xác nhận đúng username tại `/me`, gọi `/users`.
- Tắt Authorization của request và quan sát 401.
- Kiểm tra audience bằng Evaluate mà không thay đổi token thật.
- Đọc lại SecurityConfig và giải thích vì sao API mượn/trả hiện bị chặn.

Không cần thay đổi cấu hình đang hoạt động chỉ để tạo lỗi trong bài này.

## 20. Tài liệu chính thức và bảng tra nhanh

### 20.1. Nguồn tham khảo

- [Keycloak — tổng quan bảo vệ ứng dụng](https://www.keycloak.org/securing-apps/overview).
- [Keycloak — Docker quickstart](https://www.keycloak.org/getting-started/getting-started-docker).
- [Keycloak — chạy container](https://www.keycloak.org/server/containers).
- [Keycloak — quản trị realm, user, client và mapper](https://www.keycloak.org/docs/latest/server_admin/).
- [Keycloak — JavaScript adapter](https://www.keycloak.org/securing-apps/javascript-adapter).
- [Spring Boot 4 migration guide — tên dependency](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- [Spring Security 7.0 — JWT Resource Server](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html).
- [Spring Security — Bearer token và phản hồi lỗi](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/bearer-tokens.html).
- [Postman — OAuth 2.0](https://learning.postman.com/docs/use/send-requests/authorization/oauth-20/).
- [RFC 7636 — PKCE](https://www.rfc-editor.org/rfc/rfc7636).
- [OWASP — Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).
- [OWASP — Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Tài liệu online có thể thay đổi theo phiên bản. Code, log và kết quả của project trong tài liệu này được ghi nhận tại ngày tổng hợp.

### 20.2. Bảng tra nhanh

| Cần nhớ | Giá trị/kết luận |
|---|---|
| Realm | `library` |
| Client | `library-frontend` |
| Audience API | `library-api` |
| Issuer | `http://localhost:8081/realms/library` |
| Role nghiệp vụ | `READER`, `LIBRARIAN` |
| Authority tương ứng | `ROLE_READER`, `ROLE_LIBRARIAN` |
| Flow | Authorization Code + PKCE S256 |
| Client secret trong frontend | Không |
| Token gọi API | Access token |
| Header | `Authorization: Bearer <access_token>` |
| API kiểm tra danh tính | GET `http://localhost:8080/api/auth/me` |
| Sau khi sửa mapper/role | Lấy và sử dụng token mới |
| Lỗi redirect | Kiểm tra callback của đúng client |
| Lỗi `/certs` timeout | Kiểm tra kết nối/khóa, không sửa quyền để né lỗi |
| Lỗi `aud` | Kiểm tra custom audience và token mới |
| Việc tiếp theo | React Keycloak, liên kết user, mở lại nghiệp vụ |

