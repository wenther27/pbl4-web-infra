# web-server

Ứng dụng web (CineHub) dùng làm backend đặt sau load-balancer.

```
web-server/
├── BE/   Spring Boot 3.5 / Java 21 – REST API (JWT, JPA, MySQL), log JSON cho ELK
└── FE/   Frontend (chưa khởi tạo)
```

---

## BE – Chạy thử

```bash
cd BE
cp src/main/resources/application.properties.example src/main/resources/application.properties
# sửa DB user/pass, JWT secret trong file vừa copy
./mvnw spring-boot:run
```

Khởi tạo DB (chạy 1 lần):

```bash
mysql -u root -p < database/cinehub_db.sql
```

Tài khoản mẫu sau khi seed — mật khẩu **`cinehub123`** cho tất cả:

| username | role |
|---|---|
| `admin` | ADMIN |
| `quanzn`, `khanh`, `linhnt`, `hoang`, `mynguyen` | USER |

> Mật khẩu trong file seed đã là hash BCrypt. Không để mật khẩu thô ở đó —
> `BCryptPasswordEncoder` sẽ không bao giờ khớp nên login luôn thất bại.

Chạy test (dùng H2 in-memory, **không cần** bật MySQL):

```bash
./mvnw test
```

---

## BE – Cấu trúc code

```
com.example.cinehub
├── cinehub.java            @SpringBootApplication – điểm khởi động
├── config/
│   ├── AppConfig             bean ModelMapper (STRICT) + PasswordEncoder
│   └── RequestLogFilter      gắn header X-Instance-Id + ghi access log cho ELK
├── constant/                 enum MovieType, MovieStatus, RoleUser
├── controller/               nhận HTTP, không chứa logic nghiệp vụ
├── dto/
│   ├── request/              dữ liệu FE gửi lên (có @Valid)
│   └── response/             dữ liệu trả về FE (không lộ password)
├── entity/                   map 1-1 với bảng trong MySQL
├── exception/                GlobalExceptionHandler + 2 exception nghiệp vụ
├── repository/               Spring Data JPA
├── security/                 JWT, phân quyền, handler lỗi 401/403
├── service/                  interface nghiệp vụ
│   └── impl/                 phần cài đặt
└── specification/            query động cho màn tìm kiếm/lọc phim
```

Luồng một request:

```
HTTP → RequestLogFilter → CORS → JwtFilter → SecurityConfig (phân quyền)
     → Controller (@Valid) → Service (@Transactional) → Repository → MySQL
                                   ↓ lỗi
                          GlobalExceptionHandler → JSON
```

Nguyên tắc đang theo:

- Controller **không** gọi Repository, chỉ gọi Service.
- Controller **không** nhận Entity trong `@RequestBody` — luôn qua `dto/request`.
  Nhận Entity nghĩa là client tự gửi được `role: ADMIN` hay password thô.
- Trả về `dto/response`, không trả Entity (tránh lộ password và lỗi lazy-loading).
- Mã hoá password nằm trong Service, không nằm ở Controller.

---

## BE – Danh sách API

Public = gọi được khi chưa đăng nhập. Còn lại cần header
`Authorization: Bearer <token>`.

### Auth
| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| POST | `/api/auth/register` | public | Luôn tạo role `USER` |
| POST | `/api/auth/login` | public | Trả `{token, username, role}` |

### Phim
| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/movies` | public | Phân trang + tìm kiếm: `title`, `movieType`, `status`, `page`, `size`, `sortBy`, `direction` |
| GET | `/api/movies/{id}` | public | Chi tiết phim, kèm thể loại |
| GET | `/api/movies/trending` | public | |
| GET | `/api/movies/top-rated` | public | |
| GET | `/api/movies/type?movieType=anime` | public | |
| GET | `/api/movies/genre/{slug}` | public | Ví dụ `/api/movies/genre/hanh-dong` |
| GET | `/api/movies/all` | ADMIN | Không phân trang, cho trang quản trị |
| POST | `/api/movies` | ADMIN | |
| PUT | `/api/movies/{id}` | ADMIN | |
| DELETE | `/api/movies/{id}` | ADMIN | |

### Thể loại
| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/api/genres` | public |
| POST | `/api/genres` | ADMIN |

### Tập phim
| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/api/movies/{movieId}/episodes` | public |
| POST | `/api/movies/{movieId}/episodes` | ADMIN |
| PUT | `/api/episodes/{episodeId}` | ADMIN |
| DELETE | `/api/episodes/{episodeId}` | ADMIN |

### Bình luận
| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/api/movies/{movieId}/reviews` | public |
| POST | `/api/movies/{movieId}/reviews` | đã đăng nhập |
| DELETE | `/api/reviews/{reviewId}` | chủ bình luận **hoặc** ADMIN |

### Yêu thích
| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/api/favorites` | đã đăng nhập |
| POST | `/api/favorites/{movieId}` | đã đăng nhập |
| DELETE | `/api/favorites/{movieId}` | đã đăng nhập |
| GET | `/api/favorites/{movieId}/check` | đã đăng nhập |

### Lịch sử xem
| Method | Endpoint | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/history` | đã đăng nhập | |
| POST | `/api/history` | đã đăng nhập | Upsert theo user + phim. Body: `{movieId, episodeId?, lastPosition}` |

### User
| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/api/users/me` | đã đăng nhập |
| PUT | `/api/users/me` | đã đăng nhập |
| GET | `/api/users` | ADMIN |
| GET | `/api/users/{id}` | ADMIN |
| POST | `/api/users` | ADMIN |
| PUT | `/api/users/{id}` | ADMIN |
| DELETE | `/api/users/{id}` | ADMIN |

### Hạ tầng
| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/actuator/health` | public — load balancer health check |

---

## BE – Định dạng lỗi

Mọi lỗi trả về cùng một khuôn:

```json
{
  "error": "Dữ liệu đầu vào không hợp lệ",
  "details": { "title": "Tên phim không được để trống" },
  "timestamp": "2026-09-26T21:40:00.123"
}
```

`details` là `String` với lỗi thường, là object `{field: message}` với lỗi validation.

| Mã | Khi nào |
|---|---|
| 400 | Sai validation, sai enum, thiếu param, JSON sai cú pháp, vi phạm nghiệp vụ |
| 401 | Chưa đăng nhập, token sai/hết hạn, sai mật khẩu |
| 403 | Đã đăng nhập nhưng không đủ quyền |
| 404 | Không tìm thấy tài nguyên |
| 409 | Vi phạm ràng buộc DB (trùng UNIQUE, sai khoá ngoại) |
| 500 | Lỗi không lường trước — có log stacktrace kèm đường dẫn request |

---

## BE – Phục vụ phần hạ tầng (tuần 3-4)

**Health check** — `GET /actuator/health` để public sẵn, load balancer gọi được
mà không cần token.

**Nhận biết instance** — mọi response có header `X-Instance-Id`, lấy từ biến môi
trường `INSTANCE_ID`. Chạy 3 container với `INSTANCE_ID=web-1/web-2/web-3` là
thấy ngay round-robin có hoạt động không.

**Log JSON cho ELK** — bật bằng `SPRING_PROFILES_ACTIVE=docker`. Khi đó
`logback-spring.xml` xuất log JSON một dòng ra stdout để Filebeat đọc. Mỗi
request có một dòng với các field riêng (không phải parse từ chuỗi):

```json
{"@timestamp":"...","level":"INFO","app":"CineHub-BE","instance":"web-1",
 "httpMethod":"GET","path":"/api/movies","status":"200",
 "durationMs":"14","clientIp":"172.18.0.1","message":"GET /api/movies -> 200 (14ms)"}
```

`clientIp` đọc từ `X-Forwarded-For` trước, vì sau load balancer thì
`getRemoteAddr()` chỉ ra IP của LB. Request `/actuator/**` không ghi log để
health check không làm nhiễu dữ liệu.

**Biến môi trường** — mọi giá trị trong `application.properties` đều override
được, tiện cho `docker-compose`:

| Biến | Mặc định |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3307/cinehub_db?...` |
| `DB_USER` / `DB_PASSWORD` | `root` / — |
| `DB_POOL_SIZE` | `10` |
| `JWT_SECRET` | (phải đổi) |
| `JWT_EXPIRATION` | `86400000` (1 ngày) |
| `SERVER_PORT` | `8080` |
| `INSTANCE_ID` | `local` |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:3000` |
| `LOG_LEVEL` | `INFO` |
| `SPRING_PROFILES_ACTIVE` | — (đặt `docker` để log JSON) |

---

## BE – Những chỗ cần biết trước khi sửa tiếp

- **Xoá phim dựa vào `ON DELETE CASCADE` của MySQL.** Entity không khai báo
  cascade ở tầng JPA, nên schema phải được tạo từ `database/cinehub_db.sql`.
  Đừng bật `spring.jpa.hibernate.ddl-auto=update` cho production — schema Hibernate
  sinh ra sẽ thiếu cascade và xoá phim sẽ lỗi khoá ngoại.
- **Bảng `favorites` chỉ được entity `Favorite` quản lý.** Đừng thêm
  `@ManyToMany` trỏ vào bảng đó ở `User` — hai mapping cùng một bảng sẽ xung đột.
- **ModelMapper đang chạy `MatchingStrategies.STRICT`.** Thêm field vào DTO mà tên
  không trùng entity thì phải set tay trong `convertToDTO`, đừng trông chờ nó tự đoán.
- **`app.cors.allowed-origins`** là nơi duy nhất khai báo CORS
  (`SecurityConfig.corsConfigurationSource`). Không dùng `@CrossOrigin` trên
  controller nữa: annotation đó không xử lý được request preflight `OPTIONS`
  vì Spring Security chặn trước khi tới controller.
- **`rating` của phim đang là giá trị admin tự nhập**, chưa tính trung bình từ
  bảng `reviews`. Nếu cần thì thêm vào `ReviewService`.
- Class khởi động tên `cinehub` (chữ thường) — sai quy ước Java, nên đổi thành
  `CineHubApplication` nếu có dịp refactor.
