# CineHub Web Infrastructure

Kho mã nguồn kết hợp backend CineHub với hạ tầng chạy nhiều instance.

## Thành phần hiện có

- `web-server/backend`: Spring Boot 3 / Java 21, REST API, JWT, Spring Data JPA.
- `web-server/frontend`: giao diện độc lập viết bằng HTML, CSS và JavaScript thuần.
- `database`: schema MySQL và dữ liệu mẫu.
- `load-balancer`: bộ khung load balancer Java (chưa triển khai).
- `elk-stack`: bộ khung ELK/Filebeat (chưa triển khai).
- `anomaly-detection`: bộ khung phát hiện bất thường (chưa triển khai).
- `load-testing`: vị trí dành cho kịch bản Apache Bench/JMeter.

## Chạy bằng Docker

1. Sao chép `.env.example` thành `.env` và thay mật khẩu/JWT secret.
2. Chạy `docker compose up --build`.
3. Ba instance API được mở tại `http://localhost:8081`, `:8082`, `:8083`.
4. MySQL được mở cho máy host tại `localhost:3307`.

Tài khoản mẫu: `admin` / `123456` (role `ADMIN`).

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

Lưu ý: load balancer, ELK và anomaly detection vẫn là các module khung và chưa được đưa vào Compose.

## Chạy frontend

```bash
cd web-server/frontend
npm install
npm run dev
```

Frontend chạy tại `http://localhost:5173` và gọi REST API tại `http://localhost:8080/api`.
Có thể đổi địa chỉ API trong `web-server/frontend/js/config.js` mà không cần sửa backend.
