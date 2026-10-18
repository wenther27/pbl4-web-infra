# CineHub Web Infrastructure

Kho mã nguồn kết hợp backend CineHub với hạ tầng chạy nhiều instance.

## Thành phần hiện có

- `web-server/backend`: Spring Boot 3 / Java 21, REST API, JWT, Spring Data JPA.
- `web-server/frontend`: giao diện độc lập viết bằng HTML, CSS và JavaScript thuần.
- `database`: schema MySQL và dữ liệu mẫu.
- `load-balancer`: load balancer Java round-robin, health check, failover và streaming upload/video.
- `elk-stack`: Filebeat → Elasticsearch → Kibana, có dashboard giám sát traffic (chưa dùng Logstash).
- `anomaly-detection`: bộ khung phát hiện bất thường (chưa triển khai).
- `load-testing`: vị trí dành cho kịch bản Apache Bench/JMeter.

## Chạy bằng Docker

1. Sao chép `.env.example` thành `.env` và thay mật khẩu/JWT secret.
2. Chạy `docker compose up --build`.
3. Điểm vào qua load balancer là `http://localhost:8080`; ba instance API trực tiếp được mở
   tại `http://localhost:8081`, `:8082`, `:8083` để kiểm tra.
4. MySQL primary (ghi) mở tại `localhost:3307`, replica (chỉ đọc) tại `localhost:3308`.

Tài khoản mẫu: `admin` / `123456` (role `ADMIN`).

Mỗi response có header `X-Instance-Id` (web-1/2/3) để biết request rơi vào instance nào.
Trong Docker, log backend là JSON một dòng (`instance`, `path`, `status`, `durationMs`, `clientIp`)
để Filebeat/Logstash đẩy sang Elasticsearch.

### Demo tự kiểm tra toàn hệ thống (Windows)

```powershell
powershell -ExecutionPolicy Bypass -File .\demo.ps1          # dừng sau mỗi bước
powershell -ExecutionPolicy Bypass -File .\demo.ps1 -NoPause # chạy liền
```

Script tự bật web + MySQL + ELK + frontend, rồi kiểm tra: 3 instance, token dùng chéo instance,
replication, replica chỉ đọc, phân quyền, log JSON vào Elasticsearch/Kibana, phát hiện instance chết.

### ELK: xem log trên Kibana

```powershell
docker compose up -d                                              # web-1/2/3 + MySQL (chạy trước)
docker compose -f elk-stack/docker-compose.elk.yml up -d          # Elasticsearch + Kibana + Filebeat
powershell -ExecutionPolicy Bypass -File .\elk-stack\setup-kibana.ps1   # nạp data view + dashboard (1 lần)
```

- Kibana: http://localhost:5601/app/dashboards#/view/cinehub-traffic (Elasticsearch: http://localhost:9200).
- Luồng log: web-1/2/3 in JSON ra stdout → Docker lưu file log → **Filebeat** (`elk-stack/filebeat/filebeat.yml`)
  tự tìm container `cinehub-infrastructure-web-*`, tách JSON thành field → **Elasticsearch** index `weblogs-YYYY.MM.DD`.
- Kiểu dữ liệu khai báo trong `elk-stack/filebeat/weblogs-template.json`: `status` integer, `durationMs` long,
  `clientIp` ip, `instance`/`path` keyword.
- Không cần `vm.max_map_count`: Elasticsearch đặt `node.store.allow_mmap: false`.
- RAM: Elasticsearch giới hạn heap 1GB; cả ELK dùng thêm khoảng 2–2,5GB.
- Tắt ELK: `docker compose -f elk-stack/docker-compose.elk.yml down` (thêm `-v` để xoá sạch log đã lưu).

### Database primary / replica

- Replication theo GTID, **tự cấu hình lần đầu** `docker compose up`: primary tạo user `repl`
  (`database/primary/init/`), replica copy dữ liệu từ primary rồi bật replication
  (`database/replica/init/`). Không cần gõ lệnh tay.
- Replica chỉ báo `healthy` khi cả hai luồng replication đang chạy; các instance `web-*` chờ cả hai DB healthy.
- Replica bật `super_read_only`: mọi lệnh ghi vào replica đều bị từ chối, kể cả root.
- Backend: transaction `@Transactional(readOnly = true)` đọc từ replica, còn lại ghi vào primary
  (`DataSourceConfig`, `RoutingDataSource`).
- Chỉ chạy DB (khi dev backend bằng IDE): `docker compose up -d mysql-primary mysql-replica`.
- Kiểm tra replication:
  `docker compose exec mysql-replica bash /usr/local/bin/replica-healthcheck.sh && echo OK`
- Script init chỉ chạy khi volume còn trống. Muốn khởi tạo lại từ đầu: `docker compose down -v`
  (**xóa toàn bộ dữ liệu**).
- Đổi cổng host bằng `MYSQL_PRIMARY_PORT` / `MYSQL_REPLICA_PORT` trong `.env`.

Kiểm tra sức khỏe một instance:

```bash
curl http://localhost:8081/actuator/health
```

## Swagger / OpenAPI

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
- OpenAPI YAML: `http://localhost:8081/v3/api-docs.yaml`

Để thử API cần đăng nhập, gọi `POST /api/auth/login`, sao chép token trả về, chọn **Authorize** và nhập token JWT. Swagger tự thêm tiền tố `Bearer`.

Swagger được bật mặc định để phát triển. Khi triển khai production, đặt `SWAGGER_ENABLED=false` trong `.env` nếu không muốn công khai tài liệu API.

## Phân quyền API

- `PUBLIC`: đăng ký, đăng nhập, xem phim, thể loại, tập phim và bình luận.
- `USER`: quản lý yêu thích, lịch sử xem, thêm bình luận và đánh giá.
- `ADMIN`: quản lý phim, thể loại, tập phim, người dùng, tải ảnh và xóa bình luận.

Các endpoint không được khai báo trong ma trận phân quyền sẽ bị từ chối mặc định.

## Tệp tải lên

Admin có thể chọn ảnh JPG, PNG, WebP hoặc GIF (tối đa 5 MB) khi thêm/sửa phim và tập phim. Backend lưu file trong `web-server/backend/uploads` khi chạy local; database chỉ lưu URL. Thư mục này không được commit lên Git. Khi chạy Docker, volume `cinehub-uploads` giữ ảnh dùng chung cho các instance backend.

Video MP4, WebM hoặc OGG được giới hạn mặc định 500 MB. Load balancer truyền upload và response
video theo stream thay vì giữ toàn bộ file trong RAM; request `Range`/response `206 Partial Content`
được giữ nguyên để trình duyệt tua và tải từng phần. Timeout mặc định cấu hình bằng
`LB_REQUEST_TIMEOUT_SECONDS` (API thường) và `LB_UPLOAD_TIMEOUT_SECONDS` (upload video).

## Chạy frontend

```bash
cd web-server/frontend
npm install
npm run dev
```

Frontend chạy tại `http://localhost:5173` và gọi REST API tại `http://localhost:8080/api`.
Có thể đổi địa chỉ API trong `web-server/frontend/js/config.js` mà không cần sửa backend.
