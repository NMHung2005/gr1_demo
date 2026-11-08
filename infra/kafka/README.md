# Kafka cơ bản trong Library App

## 1. Bản này làm gì?

Khi mượn hoặc trả sách thành công, backend gửi một JSON sự kiện vào Kafka. Consumer đọc JSON và ghi log để bạn nhìn thấy luồng gửi–nhận. Chưa gửi email, tạo thông báo giao diện, ghi thêm bảng nghiệp vụ hoặc dùng outbox.

```text
Frontend gọi API mượn/trả
           ↓
LoanService kiểm tra quyền/điều kiện và lưu MySQL
           ↓
publishEvent(LoanEvent): tạo sự kiện nội bộ trong transaction
           ↓
MySQL COMMIT thành công
           ↓
LoanEventPublisher.onCommitted nhận sự kiện sau commit
           ↓
Luồng nền loan-kafka-... gọi KafkaTemplate.send
           ↓
Kafka broker lưu message trong topic library.loan-events.v1
           ↓
LoanEventConsumer.receive nhận message và ghi log
```

Nếu MySQL rollback, listener sau commit không chạy nên không gửi Kafka. Producer và consumer vẫn nằm trong cùng ứng dụng Spring Boot, nhưng chạy trên các luồng khác nhau; không cần tách microservice.

## 2. Khái niệm gắn với project

| Tên | Hiểu đơn giản | Trong project |
|---|---|---|
| Event/message | Thông điệp về việc đã xảy ra | LOAN_BORROWED, LOAN_RETURNED |
| Producer | Bên gửi thông điệp | LoanEventPublisher |
| Broker | Máy chủ lưu/chuyển thông điệp | Container library-kafka |
| Topic | Luồng thông điệp có tên | library.loan-events.v1 |
| Consumer | Code đọc/xử lý thông điệp | LoanEventConsumer |
| Group | Nhóm consumer chia nhau công việc đọc | library-loan-logger |
| Partition | Một phần của topic | Local dùng 1 partition |
| Offset | Vị trí bản ghi trong partition | Được in trong log |
| Message key | Khóa phân phối bản ghi | loanId dạng chuỗi |

Kafka giữ dữ liệu sau khi consumer đọc, theo thời gian retention (topic mới này đặt 24 giờ). Kafka không phải Redis cache: Redis giữ bản sao để đọc nhanh, Kafka giữ sự kiện để code khác xử lý. Kafka cũng không tự gửi email.

Tài liệu chính thức: [Kafka introduction](https://kafka.apache.org/41/getting-started/introduction/).

## 3. Chạy lần đầu

Cần bật Docker Desktop. Từ thư mục gốc project:

```powershell
docker compose -f infra/kafka/compose.yaml up -d
docker compose -f infra/kafka/compose.yaml ps
```

Đợi Kafka chuyển sang healthy, rồi khởi động lại backend bằng IDE hoặc:

```powershell
cd spring-backend
.\mvnw.cmd spring-boot:run
```

Sau đó mở frontend và đăng nhập bằng tài khoản thư viện như trước. MySQL, Redis, Keycloak vẫn cần chạy theo thiết lập hiện có.

Backend chạy trên Windows kết nối Kafka qua `127.0.0.1:9092`. Topic được backend khai báo lúc khởi động. Compose chỉ mở cổng trên loopback; không cài UI Kafka và không sửa container Redis/Keycloak. Broker local dùng KRaft: controller quản lý metadata đi cùng broker trong một container, không cần ZooKeeper.

Broker lưu dữ liệu trong named volume. Không chạy `down -v` nếu muốn giữ dữ liệu Kafka. Compose này dành cho backend chạy trực tiếp trên máy; nếu sau này Docker hóa backend phải đổi advertised listeners phù hợp.

## 4. Xem kết quả ngay trên ứng dụng

1. Chọn một sách còn số lượng để mượn.
2. Mượn thành công: quan sát console backend.
3. Trả chính phiếu đó: quan sát thêm sự kiện trả.

Ví dụ log:

```text
[Kafka producer] SENT type=LOAN_BORROWED eventId=... loanId=25 topic=library.loan-events.v1 partition=0 offset=0
[Kafka consumer] RECEIVED type=LOAN_BORROWED eventId=... loanId=25 bookId=8 userId=5 topic=library.loan-events.v1 partition=0 offset=0

[Kafka producer] SENT type=LOAN_RETURNED eventId=... loanId=25 topic=library.loan-events.v1 partition=0 offset=1
[Kafka consumer] RECEIVED type=LOAN_RETURNED eventId=... loanId=25 bookId=8 userId=5 topic=library.loan-events.v1 partition=0 offset=1
```

Đối chiếu eventId/loanId/partition/offset để thấy producer và consumer nói về cùng bản ghi. Producer và consumer chạy đồng thời nên thứ tự hai dòng SENT/RECEIVED trên console có thể khác ví dụ. Chưa có thay đổi giao diện; kết quả Kafka ở log backend.

## 5. Xem message trực tiếp trong Kafka

Từ terminal khác, chạy:

```powershell
docker exec -it library-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server 127.0.0.1:9092 --topic library.loan-events.v1 --from-beginning --property print.key=true
```

Đây là consumer quan sát riêng, không dùng group library-loan-logger nên không giành message với consumer ứng dụng. Nhấn Ctrl+C để kết thúc. `--from-beginning` cho phép công cụ này xem dữ liệu còn được giữ từ đầu. Backend dùng auto-offset-reset=earliest khi group chưa có offset hợp lệ; group đã commit sẽ tiếp tục từ tiến độ đã lưu.

Kiểm tra topic và group:

```powershell
docker exec library-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --describe --topic library.loan-events.v1
docker exec library-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9092 --describe --group library-loan-logger
```

CURRENT-OFFSET là tiến độ đã commit; LOG-END-OFFSET là vị trí cuối; LAG là số bản ghi nhóm còn chưa theo kịp. LAG bằng 0 cho biết nhóm đã theo kịp tại thời điểm kiểm tra.

## 6. Ý nghĩa từng file Java

### messaging/LoanEvent.java

Record là gói dữ liệu bất biến chụp lại lúc mượn/trả: eventId, eventType, schemaVersion, loanId, bookId, userId, occurredAt, dueDate, returnDate. Ngày/giờ là chuỗi ISO để JSON dễ đọc. Không gửi entity JPA, token, mật khẩu hoặc email lên topic.

### service/LoanService.java

Sau khi lưu phiếu và cập nhật sách, gọi `events.publishEvent(LoanEvent.from(...))`. Đây mới là sự kiện nội bộ Spring, chưa phải gửi Kafka. Chỉ đặt ở hai hàm lõi borrow/returnBook; wrapper reader gọi vào hai hàm đó nên không phát hai lần.

### messaging/LoanEventPublisher.java

`@TransactionalEventListener(AFTER_COMMIT)` chỉ nhận sau khi transaction thành công. Nó đưa công việc vào executor nền, rồi worker chuyển event thành JSON và gọi KafkaTemplate.send(topic, loanId, json). Future callback in SENT khi broker xác nhận, hoặc FAILED khi gửi lỗi. Lỗi hàng đợi/gửi không đổi kết quả MySQL đã commit.

Tài liệu: [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html), [KafkaTemplate](https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html).

### messaging/LoanEventConsumer.java

`@KafkaListener` đăng ký nhận topic, thuộc group library-loan-logger. Message dùng StringDeserializer, rồi đọc JSON vào đúng LoanEvent, kiểm tra phiên bản/trường bắt buộc và ghi log. Không dùng header tên class Java hoặc mở trusted packages cho mọi class. Với ack-mode=record và auto-commit=false, container commit tiến độ sau khi listener xử lý thành công.

### config/LoanKafkaConfig.java

Khai báo topic: 1 partition, 1 replica, retention 24 giờ; tạo executor 1 worker với hàng đợi tối đa 100. Cấu hình được bật bằng app.kafka.enabled. Chạy local 1 broker không có tính sẵn sàng cao; replicas=1 không phải có bản sao dự phòng.

## 7. Các cấu hình cần hiểu

Trong application.properties:

| Cấu hình | Ý nghĩa |
|---|---|
| app.kafka.enabled | Bật ba thành phần Kafka của ứng dụng; mặc định true |
| app.kafka.loan-topic | Tên topic chung cho mượn/trả |
| spring.kafka.bootstrap-servers | Địa chỉ broker để kết nối ban đầu |
| spring.kafka.producer.acks=all | Chờ xác nhận từ các replica đồng bộ; local chỉ có 1 |
| enable.idempotence=true | Giảm trùng do producer retry; không phải dedup nghiệp vụ toàn hệ thống |
| max.block.ms=2000 | Giới hạn chờ metadata/buffer trong send, chạy ở worker |
| delivery.timeout.ms=10000 | Giới hạn thời gian giao message theo cấu hình producer |
| spring.kafka.consumer.group-id | Định danh nhóm đọc, có tiến độ riêng |
| auto-offset-reset=earliest | Khi chưa có offset hợp lệ, bắt đầu ở dữ liệu sớm nhất còn giữ |
| ack-mode=record | Container ghi nhận tiến độ sau khi xử lý từng record |

Producer/consumer cùng dùng String serializer/deserializer; JSON do code chuyển đổi, dễ quan sát qua CLI.

Tạm tắt Kafka khi chỉ muốn chạy phần thư viện (PowerShell):

```powershell
$env:APP_KAFKA_ENABLED="false"
.\mvnw.cmd spring-boot:run
```

Bật lại trong terminal đó:

```powershell
$env:APP_KAFKA_ENABLED="true"
.\mvnw.cmd spring-boot:run
```

Khi tắt, không có listener gửi/nhận của ứng dụng. Muốn đổi broker dùng biến KAFKA_BOOTSTRAP_SERVERS. Khởi động lại backend sau đổi biến/cấu hình.

## 8. Giới hạn chủ ý của bản cơ bản

- MySQL đã commit nhưng ứng dụng chết trước khi gửi Kafka: có thể mất sự kiện.
- Broker ngừng quá thời gian giao hoặc executor đầy: ghi FAILED, không có bản ghi bền vững để tự gửi lại sau khi khởi động.
- Producer có retry trong giới hạn cấu hình; đây không phải outbox hay cơ chế resend bền vững.
- Consumer có thể xử lý/log lại bản ghi, ví dụ xử lý xong nhưng chưa commit offset. Chưa có bảng chống trùng nghiệp vụ.
- JSON hỏng/phiên bản không hỗ trợ làm listener báo lỗi; cơ chế xử lý lỗi mặc định của Spring Kafka ghi log/recover theo cấu hình framework. Chưa có DLT hay công cụ phát lại dữ liệu lỗi.
- Một worker và một partition giúp học dễ hơn, không thay thế việc thiết kế thứ tự nghiệp vụ khi triển khai nhiều instance.
- Nếu sau này cần email/thông báo chắc chắn, mới thêm outbox, consumer idempotent và retry/DLT rõ ràng.

## 9. Kiểm thử

Kiểm thử mặc định không cần Kafka (application-test.properties đặt app.kafka.enabled=false):

```powershell
cd spring-backend
.\mvnw.cmd verify
```

LoanEventIntegrationTests dùng H2 + producer mock để kiểm tra thời điểm gửi và rollback; LoanMessagingTests kiểm tra JSON, executor, lỗi đồng bộ/bất đồng bộ và queue đầy.

Sau khi broker đã healthy, kiểm tra luồng thực từ service → commit → producer → Kafka → consumer:

```powershell
.\mvnw.cmd verify "-Dlibrary.kafka.smoke=true"
```

KafkaLoanSmokeTests dùng dữ liệu H2, topic và group ngẫu nhiên riêng, không sửa MySQL đang dùng. Kết thúc test dừng listener thử nghiệm và xóa đúng topic do test tạo. Các kiểm thử cache hiện có vẫn chạy trong toàn bộ bộ kiểm thử backend.

Kết quả ngày 08/11/2026: build sạch và toàn bộ 34 kiểm thử trong checkout hiện tại đã đạt; không lỗi, không bỏ qua. Smoke test thực xác nhận consumer nhận LOAN_BORROWED tại offset 0 và LOAN_RETURNED tại offset 1 trên topic kiểm thử riêng. Container library-kafka đã được khởi động; khởi động lại backend để ứng dụng chính khai báo topic và bật consumer của nó.
