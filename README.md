# CineHub Web Infrastructure - PBL4

Hạ tầng phân tán hoàn chỉnh cho nền tảng xem phim trực tuyến CineHub, kết hợp cân bằng tải thông minh, cơ sở dữ liệu phân tán, bộ đệm RAM phân tán, hệ thống giám sát log tập trung và phát hiện tấn công mạng tự động.

---

## 1. Cấu trúc tổng thể các thành phần

Dự án gồm các module chuyên biệt:

- **`load-balancer`**: Bộ cân bằng tải tự viết bằng Java HTTP Server:
  - Hỗ trợ 2 thuật toán: **Round-Robin** và **DWLC (Dynamic Weighted Least Connection)** kết hợp Smooth WRR, tự động hạ tải node chậm.
  - Kiểm tra sức khỏe tự động (**HealthChecker** định kỳ 5s) với **Grace Period 180s** tránh báo lỗi giả khi khởi động.
  - Cơ chế **Streaming Video / Range Request (206 Partial Content)** với buffer 64KB, chống tràn RAM (OOM) khi xem hoặc tải lên video lớn (đến 500MB).
  - Cổng quản trị bảo mật riêng biệt (`:8090`) bảo vệ bằng token, hỗ trợ chặn và tự động mở khóa IP theo thời gian.
- **`web-server/backend`**: Cụm backend chạy Spring Boot 3 / Java 21:
  - Chạy mô hình cụm 3 instance không trạng thái (`web-1`, `web-2`, `web-3`) dùng chung JWT.
  - Header phản hồi `X-Instance-Id` xác định rõ instance phục vụ từng request.
  - Tích hợp API Quản trị Bảo mật (`SecurityAdminController`) cho phép tra cứu cảnh báo và chặn IP.
- **`redis`**: Bộ đệm In-Memory (Redis 7.4 Alpine) dùng chung cho cả 3 instance backend:
  - Mô hình **Cache-Aside** cho các truy vấn đọc nhiều: danh sách phim, top thịnh hành, phân loại phim và thể loại.
  - Giảm thời gian phản hồi từ ~50ms (MySQL) xuống **< 1ms** trên RAM.
  - Cơ chế tự động làm sạch cache (`@CacheEvict`) khi Admin thêm, sửa, xóa phim.
  - Khả năng chịu lỗi cao (*Fault-Tolerant*): Nếu Redis gặp sự cố, backend tự động đọc trực tiếp từ MySQL mà không gây lỗi 500.
- **`database`**: Cụm MySQL 8.4 phân tán sao chép theo chuẩn **GTID**:
  - `mysql-primary` (Cổng `3307`): Nhận toàn bộ thao tác ghi (`INSERT`, `UPDATE`, `DELETE`).
  - `mysql-replica` (Cổng `3308`): Bật chế độ `super_read_only`, nhận toàn bộ thao tác đọc (`@Transactional(readOnly = true)`).
  - Tự động hóa thiết lập người dùng nhân bản và bật Replication ngay lần đầu khởi động container.
- **`anomaly-detection`**: Ứng dụng Java độc lập quét log từ Elasticsearch để bảo vệ hệ thống:
  - **8 luật phát hiện tấn công tự động**: DoS (`HIGH_RATE_IP`), quét lỗ hổng (`SCAN_PATHS`), Brute Force đăng nhập (`BRUTE_FORCE_LOGIN`), lỗi hệ thống (`ERROR_RATE_5XX`), quá tải độ trễ (`HIGH_LATENCY`), backend ngừng hoạt động (`BACKEND_DOWN`), lưu lượng bất thường (`TRAFFIC_SPIKE` theo thuật toán Z-Score), và chữ ký tấn công WAF (`ATTACK_SIGNATURE`: SQL Injection, XSS, Path Traversal, Scanners).
  - **AutoBlocker**: Tự động phát lệnh khóa IP nguy hiểm qua cổng 8090 của Load Balancer, có chốt an toàn và danh sách IP tin cậy (whitelist).
- **`elk-stack`**: Hệ thống thu thập và phân tích log tập trung:
  - Filebeat thu thập log JSON có cấu trúc từ stdout của container backend và load balancer.
  - Elasticsearch lưu trữ và đánh index `weblogs-*` và `alerts-*`.
  - Kibana hiển thị trực quan thông qua Dashboard mẫu (`cinehub-traffic`).
- **`web-server/frontend`**: Giao diện người dùng độc lập viết bằng HTML, CSS và JavaScript thuần (Vite).
  - Tích hợp thêm trang **Admin > Bảo mật** hiển thị IP bị cảnh báo, bộ đếm ngược thời gian chặn và nút gỡ chặn thủ công.
- **`load-testing`**: Kịch bản kiểm thử tải bằng Apache JMeter (`traffic-test-plan.jmx`) đo đạc hiệu năng giữa Round-Robin và DWLC.

---

## 2. Hướng dẫn chạy hệ thống

### 2.1. Yêu cầu môi trường
- Đã cài đặt **Docker Desktop** (bật WSL2 trên Windows).
- Đã cài đặt **Node.js** (để chạy frontend).

### 2.2. Khởi chạy cụm hạ tầng chính (Backend + Load Balancer + Database + Redis + Anomaly Detector)

1. Tạo file biến môi trường từ mẫu:
   ```powershell
   Copy-Item .env.example .env
   ```

2. Khởi chạy toàn bộ container:
   ```powershell
   docker compose up --build -d
   ```

3. Các cổng dịch vụ mở trên máy:
   - **Load Balancer (Điểm vào chính)**: `http://localhost:8080` (API tại `http://localhost:8080/api`).
   - **Cổng quản trị chặn IP của Load Balancer**: `http://localhost:8090`.
   - **Các Backend trực tiếp (để kiểm tra/debug)**:
     - `web-1`: `http://localhost:8081`
     - `web-2`: `http://localhost:8082`
     - `web-3`: `http://localhost:8083`
   - **MySQL Primary (Ghi)**: `localhost:3307`
   - **MySQL Replica (Chỉ đọc)**: `localhost:3308`
   - **Redis Cache**: `localhost:6379`

Tài khoản quản trị mặc định: `admin` / `123456` (Role `ADMIN`).

---

### 2.3. Khởi chạy ELK Stack (Giám sát log & Kibana)

Khởi chạy cụm Elasticsearch, Kibana và Filebeat trong một cửa sổ terminal riêng:

```powershell
# 1. Bật cụm ELK
docker compose -f elk-stack/docker-compose.elk.yml up -d

# 2. Cài đặt tự động dashboard và index pattern (chỉ cần chạy 1 lần)
powershell -ExecutionPolicy Bypass -File .\elk-stack\setup-kibana.ps1
```

- **Kibana Web UI**: `http://localhost:5601`
- **Dashboard trực quan**: `http://localhost:5601/app/dashboards#/view/cinehub-traffic`
- **Elasticsearch API**: `http://localhost:9200`

---

### 2.4. Khởi chạy Frontend

Mở một cửa sổ terminal tại thư mục frontend:

```powershell
cd web-server/frontend
npm install
npm run dev
```

- Truy cập web tại: `http://localhost:5173`
- Frontend gọi API thông qua cổng của Load Balancer: `http://localhost:8080/api`.

---

## 3. Demo kiểm thử toàn diện tự động (Windows)

Dự án cung cấp 2 kịch bản PowerShell tự động kiểm tra hệ thống:

1. **`demo.ps1`**: Kiểm tra toàn bộ luồng tích hợp:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\demo.ps1          # Dừng sau mỗi bước để quan sát
   powershell -ExecutionPolicy Bypass -File .\demo.ps1 -NoPause # Chạy liên tục
   ```
   Kịch bản kiểm tra:
   - Cân bằng tải đều qua 3 instance (`web-1`, `web-2`, `web-3`).
   - Token JWT xác thực hoán đổi trơn tru giữa các instance.
   - Dữ liệu thêm ở Primary tự động đồng bộ sang Replica.
   - Replica chặn tuyệt đối các lệnh ghi (`super_read_only`).
   - Đánh sập thử 1 node để kiểm tra Load Balancer tự động loại bỏ node lỗi (Failover).
   - Log được đánh index thành công sang Elasticsearch.

2. **`demo-lb.ps1`**: Kiểm tra chuyên sâu các tính năng của Load Balancer và Bảo mật:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\demo-lb.ps1
   ```
   Kiểm tra: Thuật toán DWLC, API chặn/gỡ chặn IP cổng 8090, tự động chặn khi phát hiện tấn công SQLi/Brute-force.

---

## 4. Tài liệu API (Swagger / OpenAPI)

Khi cụm backend đã khởi chạy, có thể truy cập tài liệu API trực tiếp qua instance:
- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8081/v3/api-docs`

---

## 5. Phân quyền và Quy định tải file

- **Phân quyền người dùng**:
  - `PUBLIC`: Đăng ký, đăng nhập, xem danh sách phim, chi tiết phim, thể loại và bình luận.
  - `USER`: Đánh giá phim, bình luận, quản lý danh sách yêu thích và lịch sử xem.
  - `ADMIN`: Quản lý toàn bộ phim, tập phim, thể loại, tài khoản người dùng, xem cảnh báo an ninh và chặn/gỡ chặn IP.
- **Tệp tải lên (Upload)**:
  - Hình ảnh (Poster, Backdrop): Hỗ trợ JPG, PNG, WebP, GIF (tối đa 5MB).
  - Video tập phim: Hỗ trợ MP4, WebM (tối đa 500MB). Dữ liệu được truyền phát theo dạng stream đệm 64KB qua Load Balancer (timeout cấu hình 10 phút `LB_UPLOAD_TIMEOUT_SECONDS=600`).
