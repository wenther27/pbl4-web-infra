# CineHub BE — Tài liệu Backend

Spring Boot 3.5.6 / Java 21 · REST API · JWT · Spring Data JPA · MySQL 8
Đóng vai trò web-server đặt sau load-balancer trong đồ án PBL4.

---

## Mục lục

1. [Chạy dự án](#1-chạy-dự-án)
2. [Kiến trúc & luồng request](#2-kiến-trúc--luồng-request)
3. [Cấu trúc thư mục](#3-cấu-trúc-thư-mục)
4. [Cơ sở dữ liệu](#4-cơ-sở-dữ-liệu)
5. [Xác thực & phân quyền](#5-xác-thực--phân-quyền)
6. [Danh sách API](#6-danh-sách-api)
7. [Khuôn dữ liệu (DTO)](#7-khuôn-dữ-liệu-dto)
8. [Xử lý lỗi](#8-xử-lý-lỗi)
9. [Cấu hình & biến môi trường](#9-cấu-hình--biến-môi-trường)
10. [Phục vụ load-balancer và ELK](#10-phục-vụ-load-balancer-và-elk)
11. [Test](#11-test)
12. [Quy ước khi viết thêm code](#12-quy-ước-khi-viết-thêm-code)
13. [Những chỗ dễ sập — đọc trước khi sửa](#13-những-chỗ-dễ-sập--đọc-trước-khi-sửa)
14. [Việc còn lại](#14-việc-còn-lại)

---

## 1. Chạy dự án

### Yêu cầu

| Thành phần | Phiên bản |
|---|---|
| JDK | 21 |
| MySQL | 8.x |
| Maven | dùng `mvnw` kèm theo, không cần cài |

### Bước 1 — Tạo database

```bash
mysql -u root -p < database/cinehub_db.sql
```

File này `DROP DATABASE IF EXISTS cinehub_db` ở đầu, nên **chạy lại là mất hết dữ liệu**.
Nó tạo 8 bảng + nạp dữ liệu mẫu (24 phim, 12 thể loại, 19 tập, 6 user).

### Bước 2 — Tạo file cấu hình

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Sửa trong file vừa copy:

- `spring.datasource.password` → mật khẩu MySQL của bạn
- `app.jwt.secret` → chuỗi bí mật riêng, **tối thiểu 64 ký tự**

> `application.properties` đã nằm trong `.gitignore`, chỉ file `.example` được commit.
> Đừng commit mật khẩu thật.

### Bước 3 — Chạy

```bash
./mvnw spring-boot:run          # Linux / macOS / Git Bash
.\mvnw.cmd spring-boot:run      # PowerShell
```

API lên ở `http://localhost:8080`.

### Bước 4 — Kiểm tra nhanh

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}

curl http://localhost:8080/api/movies/trending

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"cinehub123"}'
```

### Tài khoản mẫu

Mật khẩu của **tất cả** tài khoản mẫu: `cinehub123`

| username | email | role |
|---|---|---|
| `admin` | admin@cinehub.com | ADMIN |
| `quanzn` | quan@cinehub.com | USER |
| `khanh` | khanh@cinehub.com | USER |
| `linhnt` | linh@cinehub.com | USER |
| `hoang` | hoang@cinehub.com | USER |
| `mynguyen` | my@cinehub.com | USER |

Trong `cinehub_db.sql`, mật khẩu được lưu dưới dạng hash BCrypt qua biến `@pw`.
**Không được** thay bằng mật khẩu thô — `BCryptPasswordEncoder` sẽ không bao giờ
khớp nên đăng nhập luôn thất bại. Muốn đổi mật khẩu mẫu thì sinh hash mới:

```java
new BCryptPasswordEncoder().encode("mật-khẩu-mới");
```

### Các lệnh khác

```bash
./mvnw test              # chạy test (dùng H2, KHÔNG cần MySQL)
./mvnw clean package     # đóng gói ra target/cinehub-0.0.1-SNAPSHOT.jar
java -jar target/cinehub-0.0.1-SNAPSHOT.jar
```

---

## 2. Kiến trúc & luồng request

Kiến trúc phân lớp (layered), mỗi lớp chỉ gọi xuống lớp ngay dưới nó:

```
┌──────────────────────────────────────────────────────────┐
│  Controller       nhận HTTP, validate, KHÔNG chứa logic  │
├──────────────────────────────────────────────────────────┤
│  Service          logic nghiệp vụ, @Transactional        │
├──────────────────────────────────────────────────────────┤
│  Repository       Spring Data JPA                        │
├──────────────────────────────────────────────────────────┤
│  Entity           map 1-1 với bảng MySQL                 │
└──────────────────────────────────────────────────────────┘
```

Một request đi qua:

```
HTTP request
   │
   ▼
RequestLogFilter ──── gắn header X-Instance-Id, đo thời gian xử lý
   │
   ▼
CorsFilter ────────── kiểm tra Origin (cấu hình trong SecurityConfig)
   │
   ▼
JwtFilter ─────────── có "Authorization: Bearer <token>"?
   │                  token hợp lệ → nạp UserDetails vào SecurityContext
   ▼
SecurityConfig ────── đối chiếu bảng phân quyền
   │                  chưa đăng nhập → RestAuthenticationEntryPoint → 401 JSON
   │                  thiếu quyền    → RestAccessDeniedHandler     → 403 JSON
   ▼
Controller ────────── @Valid kiểm tra request DTO
   │                  lấy user hiện tại qua CurrentUser
   ▼
Service ───────────── logic + @Transactional, convert Entity → DTO
   │
   ▼
Repository → MySQL
   │
   ▼
response DTO (JSON)

Bất kỳ exception nào ném ra từ Controller/Service
   → GlobalExceptionHandler → JSON lỗi đúng HTTP status
```

`RequestLogFilter` ghi một dòng log cho request **sau khi** response xong, nên
status code và thời gian xử lý trong log luôn là giá trị thật.

---

## 3. Cấu trúc thư mục

```
BE/
├── database/
│   └── cinehub_db.sql              schema + dữ liệu mẫu (nguồn sự thật của DB)
├── src/main/java/com/example/cinehub/
│   ├── cinehub.java                @SpringBootApplication
│   │
│   ├── config/
│   │   ├── AppConfig.java          bean ModelMapper (STRICT) + PasswordEncoder
│   │   └── RequestLogFilter.java   header X-Instance-Id + access log cho ELK
│   │
│   ├── constant/
│   │   ├── Movie/MovieStatus.java  released | upcoming | ongoing
│   │   ├── Movie/MovieType.java    movie | series | anime
│   │   └── User/RoleUser.java      USER | ADMIN
│   │
│   ├── controller/                 7 controller, xem mục 6
│   │
│   ├── dto/
│   │   ├── request/                dữ liệu FE gửi lên — có annotation @Valid
│   │   └── response/               dữ liệu trả về FE — không chứa password
│   │
│   ├── entity/                     8 entity ứng với 8 bảng
│   │   ├── User, Movie, Genre, Episode, Review
│   │   ├── Favorite + FavoriteId   khoá chính kép (user_id, movie_id)
│   │   └── WatchHistory
│   │
│   ├── exception/
│   │   ├── BadRequestException     → 400
│   │   ├── ResourceNotFoundException → 404
│   │   └── GlobalExceptionHandler  gom mọi lỗi về một khuôn JSON
│   │
│   ├── repository/                 7 interface Spring Data JPA
│   │
│   ├── security/
│   │   ├── SecurityConfig          bảng phân quyền + CORS
│   │   ├── JwtUtil                 sinh / đọc / kiểm tra token
│   │   ├── JwtFilter               đọc header Authorization mỗi request
│   │   ├── CurrentUser             lấy user đang đăng nhập
│   │   ├── RestAuthenticationEntryPoint → 401 JSON
│   │   └── RestAccessDeniedHandler      → 403 JSON
│   │
│   ├── service/                    interface nghiệp vụ
│   │   └── impl/                   phần cài đặt
│   │
│   └── specification/
│       └── MovieSpecification      query động cho màn tìm kiếm/lọc
│
├── src/main/resources/
│   ├── application.properties          KHÔNG commit (trong .gitignore)
│   ├── application.properties.example  bản mẫu, có commit
│   └── logback-spring.xml              log text (local) / JSON (docker)
│
└── src/test/
    ├── java/com/example/cinehub/
    │   ├── cinehubTests.java       context load được không
    │   ├── SecurityRulesTest.java  kiểm tra bảng phân quyền
    │   └── ApiFlowTest.java        đi hết một luồng nghiệp vụ thật
    └── resources/
        └── application-test.properties   H2 in-memory
```

---

## 4. Cơ sở dữ liệu

### Sơ đồ quan hệ

```
users ─────┬──< reviews >──────────┐
           │                       │
           ├──< favorites >────────┤
           │   (PK kép)            ├──── movies ──< episodes
           │                       │        │          │
           └──< watch_history >────┘        │          │
                     │                      │          │
                     └──────────────────────┴──────────┘
                            (episode_id có thể NULL)

movies >──< movie_genres >──< genres      (n-n)
```

### 8 bảng

| Bảng | Khoá chính | Ghi chú |
|---|---|---|
| `users` | `id` | `username`, `email` UNIQUE. `role` ENUM(USER, ADMIN) |
| `movies` | `id` | `movie_type`, `status` là ENUM. Có index cho `is_trending`, `is_top_rated`, `movie_type`, `rating` |
| `genres` | `id` | `name`, `slug` UNIQUE |
| `movie_genres` | (`movie_id`, `genre_id`) | bảng trung gian n-n |
| `episodes` | `id` | UNIQUE (`movie_id`, `episode_number`) |
| `reviews` | `id` | CHECK `rating` từ 1 đến 10 |
| `favorites` | (`user_id`, `movie_id`) | khoá chính kép + cột `added_at` |
| `watch_history` | `id` | `episode_id` NULL được (phim lẻ). `last_position` tính bằng giây |

Mọi khoá ngoại đều `ON DELETE CASCADE`.

### Entity cần lưu ý

**`Favorite` + `FavoriteId`** — bảng `favorites` có khoá chính kép **và** cột phụ
`added_at`, nên không dùng `@ManyToMany` được, phải là entity riêng với
`@EmbeddedId`:

```java
@Entity
public class Favorite {
    @EmbeddedId
    private FavoriteId id;                 // (userId, movieId)

    @ManyToOne @MapsId("userId")  private User user;
    @ManyToOne @MapsId("movieId") private Movie movie;

    @Column(name = "added_at") private LocalDateTime addedAt;
}
```

`@MapsId("userId")` nghĩa là: cột khoá ngoại `user_id` dùng chung với field
`userId` trong `FavoriteId`, không sinh ra cột thứ hai.

**`User`** — 2 cột thời gian được set ở tầng Java bằng `@PrePersist`/`@PreUpdate`.
Bảng có `DEFAULT CURRENT_TIMESTAMP` nhưng Hibernate vẫn INSERT giá trị `NULL`
tường minh vào đó, nên không thể trông chờ vào default của MySQL.

**`Movie.createdAt`** — `insertable = false, updatable = false`, để MySQL tự điền.

**`WatchHistory`** — `episode` là `@ManyToOne` nullable, vì phim lẻ không có tập.

---

## 5. Xác thực & phân quyền

### Cơ chế

JWT **stateless** — server không lưu session. Thuật toán HS256 (jjwt 0.12.6),
token chỉ chứa `subject = username`, thời hạn mặc định 1 ngày.

```
POST /api/auth/login  {username, password}
        │
        │  AuthenticationManager kiểm tra mật khẩu (BCrypt)
        ▼
   {token, username, role}
        │
        │  FE lưu token, gửi kèm mọi request sau đó:
        ▼
   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Mật khẩu mã hoá bằng **BCrypt**, và việc mã hoá nằm trong `UserServiceImpl`
(không nằm ở Controller) để không có đường nào lỡ lưu mật khẩu thô.

### Bảng phân quyền

Định nghĩa trong `SecurityConfig.filterChain()`. **Thứ tự các dòng là quan trọng** —
dòng khớp trước sẽ thắng, nên các rule ADMIN phải đặt **trên** các rule `permitAll`
dùng ký tự `*`:

| Nhóm | Đường dẫn | Quyền |
|---|---|---|
| Hạ tầng | `/actuator/**`, `/error` | public |
| Auth | `/api/auth/**` | public |
| Quản trị phim | GET `/api/movies/all`<br>POST `/api/movies`<br>PUT, DELETE `/api/movies/**` | ADMIN |
| Quản trị thể loại | POST `/api/genres` | ADMIN |
| Quản trị tập phim | POST `/api/movies/*/episodes`<br>PUT, DELETE `/api/episodes/**` | ADMIN |
| Xem phim | GET `/api/movies`, `/api/movies/*`, `/api/movies/trending`, `/top-rated`, `/type`, `/genre/**`, `/api/movies/*/episodes`, `/api/movies/*/reviews`, `/api/genres/**` | public |
| Hồ sơ cá nhân | `/api/users/me` | đã đăng nhập |
| Quản trị user | `/api/users/**` | ADMIN |
| Còn lại | `anyRequest()` | đã đăng nhập |

Ba rule dễ quên nhất:

- `/actuator/**` phải public, **nếu không load-balancer sẽ coi cả 3 instance là chết.**
- `requestMatchers(GET, "/api/movies")` chỉ khớp **đúng** `/api/movies`.
  `/api/movies/trending` là đường dẫn khác, phải liệt kê riêng.
- `/api/movies/all` (ADMIN) phải đặt **trước** `/api/movies/*` (public),
  nếu không nó bị rule public che mất.

### CORS

Khai báo **duy nhất một chỗ**: `SecurityConfig.corsConfigurationSource()`,
đọc từ property `app.cors.allowed-origins`.

Không dùng `@CrossOrigin` trên controller. Annotation đó không xử lý được request
preflight `OPTIONS`, vì Spring Security chặn request trước khi nó tới controller —
FE sẽ thấy lỗi CORS dù backend "có" cấu hình.

### Kiểm tra quyền ở tầng nghiệp vụ

Có những quyền không diễn tả được bằng URL. Ví dụ "chỉ chủ bình luận mới xoá được
bình luận của mình" — phải kiểm tra trong service:

```java
// ReviewServiceImpl.deleteReview
if (!isAdmin && !review.getUser().getId().equals(requesterId))
    throw new AccessDeniedException("Bạn chỉ có thể xóa bình luận của chính mình");
```

Controller lấy `userId` và `isAdmin` qua bean `CurrentUser`.

---

## 6. Danh sách API

- **public** = gọi được khi chưa đăng nhập
- **auth** = cần header `Authorization: Bearer <token>`
- **ADMIN** = cần token của tài khoản role ADMIN

### 6.1 Auth — `AuthController`

| Method | Endpoint | Quyền | Body | Trả về |
|---|---|---|---|---|
| POST | `/api/auth/register` | public | `RegisterRequest` | 201 `UserDTO` |
| POST | `/api/auth/login` | public | `LoginRequest` | 200 `AuthResponse` |

Đăng ký **luôn** tạo role `USER`. Không có đường nào tự nâng lên ADMIN — muốn tạo
ADMIN thì dùng `POST /api/users` (cần token ADMIN sẵn) hoặc sửa trực tiếp DB.

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"quan2","email":"quan2@cinehub.com","password":"matkhau123"}'
```

### 6.2 Phim — `MovieController`

| Method | Endpoint | Quyền | Trả về |
|---|---|---|---|
| GET | `/api/movies` | public | `PageResponse<MovieDTO>` |
| GET | `/api/movies/{id}` | public | `MovieDTO` |
| GET | `/api/movies/trending` | public | `List<MovieDTO>` |
| GET | `/api/movies/top-rated` | public | `List<MovieDTO>` |
| GET | `/api/movies/type?movieType=anime` | public | `List<MovieDTO>` |
| GET | `/api/movies/genre/{slug}` | public | `List<MovieDTO>` |
| GET | `/api/movies/all` | ADMIN | `List<MovieDTO>` không phân trang |
| POST | `/api/movies` | ADMIN | 201 `MovieDTO` |
| PUT | `/api/movies/{id}` | ADMIN | 200 `MovieDTO` |
| DELETE | `/api/movies/{id}` | ADMIN | 204 (không body) |

**Tham số của `GET /api/movies`** — đây là API chính cho màn tìm kiếm / lọc:

| Param | Mặc định | Ghi chú |
|---|---|---|
| `title` | — | tìm gần đúng, không phân biệt hoa thường |
| `movieType` | — | `movie` \| `series` \| `anime` |
| `status` | — | `released` \| `upcoming` \| `ongoing` |
| `page` | `0` | số âm bị ép về 0 |
| `size` | `10` | **giới hạn tối đa 100** |
| `sortBy` | `id` | chỉ nhận: `id`, `title`, `rating`, `releaseDate`, `duration`, `createdAt`. Giá trị khác bị ép về `id` |
| `direction` | `asc` | `asc` \| `desc` |

Ba param cuối bị chặn có lý do: `?sortBy=abcxyz` trước đây làm Hibernate ném lỗi
thành 500, còn `?size=999999` thì kéo cả bảng vào RAM.

```bash
curl "http://localhost:8080/api/movies?title=incep&movieType=movie&page=0&size=10&sortBy=rating&direction=desc"
```

### 6.3 Thể loại — `GenreController`

| Method | Endpoint | Quyền | Trả về |
|---|---|---|---|
| GET | `/api/genres` | public | `List<GenreDTO>` |
| POST | `/api/genres` | ADMIN | 201 `GenreDTO` |

`slug` phải theo định dạng chữ thường có gạch ngang (`hanh-dong`), và trùng
`name`/`slug` trả 400 kèm thông báo rõ ràng thay vì để vỡ ràng buộc UNIQUE thành 500.

### 6.4 Tập phim — `EpisodeController`

| Method | Endpoint | Quyền | Trả về |
|---|---|---|---|
| GET | `/api/movies/{movieId}/episodes` | public | `List<EpisodeDTO>` sắp xếp tăng theo số tập |
| POST | `/api/movies/{movieId}/episodes` | ADMIN | 201 `EpisodeDTO` |
| PUT | `/api/episodes/{episodeId}` | ADMIN | 200 `EpisodeDTO` |
| DELETE | `/api/episodes/{episodeId}` | ADMIN | 204 |

Thêm trùng `episodeNumber` trong cùng một phim → 400. Khi sửa, chỉ kiểm tra trùng
nếu bạn thực sự đổi sang số tập khác.

### 6.5 Bình luận — `ReviewController`

| Method | Endpoint | Quyền | Trả về |
|---|---|---|---|
| GET | `/api/movies/{movieId}/reviews` | public | `List<ReviewDTO>` mới nhất trước |
| POST | `/api/movies/{movieId}/reviews` | auth | 201 `ReviewDTO` |
| DELETE | `/api/reviews/{reviewId}` | chủ bình luận **hoặc** ADMIN | 204 |

`userId` lấy từ token, FE không gửi lên. Xoá bình luận của người khác → 403.

### 6.6 Yêu thích — `FavoriteController`

| Method | Endpoint | Quyền | Trả về |
|---|---|---|---|
| GET | `/api/favorites` | auth | `List<FavoriteDTO>` |
| POST | `/api/favorites/{movieId}` | auth | 201 `FavoriteDTO` |
| DELETE | `/api/favorites/{movieId}` | auth | 204 |
| GET | `/api/favorites/{movieId}/check` | auth | `true` / `false` |

`/check` dùng để FE vẽ icon tim đỏ hay trắng. Thêm phim đã yêu thích → 400.
Xoá phim chưa từng yêu thích → 404 (trước đây trả 200 dù không xoá gì).

### 6.7 Lịch sử xem — `WatchHistoryController`

| Method | Endpoint | Quyền | Trả về |
|---|---|---|---|
| GET | `/api/history` | auth | `List<WatchHistoryDTO>` xem gần nhất trước |
| POST | `/api/history` | auth | 200 `WatchHistoryDTO` |

POST là **upsert** theo (user, phim): gọi nhiều lần chỉ cập nhật một dòng, không
sinh thêm bản ghi.

```bash
curl -X POST http://localhost:8080/api/history \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"movieId":7,"episodeId":2,"lastPosition":820}'
```

`episodeId` bỏ trống với phim lẻ. Gửi `episodeId` của tập thuộc phim khác → 400.

### 6.8 User — `UserController`

| Method | Endpoint | Quyền | Body | Trả về |
|---|---|---|---|---|
| GET | `/api/users/me` | auth | — | `UserDTO` |
| PUT | `/api/users/me` | auth | `UpdateProfileRequest` | `UserDTO` |
| GET | `/api/users` | ADMIN | — | `List<UserDTO>` |
| GET | `/api/users/{id}` | ADMIN | — | `UserDTO` |
| POST | `/api/users` | ADMIN | `CreateUserRequest` | 201 `UserDTO` |
| PUT | `/api/users/{id}` | ADMIN | `UpdateProfileRequest` | `UserDTO` |
| DELETE | `/api/users/{id}` | ADMIN | — | 204 |

`POST /api/users` là đường duy nhất tạo được tài khoản ADMIN qua API, và nó nhận
`CreateUserRequest` chứ không nhận entity `User`.

### 6.9 Hạ tầng

| Method | Endpoint | Quyền |
|---|---|---|
| GET | `/actuator/health` | public — load-balancer health check |
| GET | `/actuator/info` | public |

---

## 7. Khuôn dữ liệu (DTO)

### Nguyên tắc

- `dto/request/*` — thứ FE gửi lên, có annotation validation.
- `dto/response/*` — thứ trả về FE, **không bao giờ** chứa `password`.
- Controller không nhận và không trả `entity/*`.

Nhận entity trong `@RequestBody` nghĩa là client tự gửi được `"role": "ADMIN"` hay
`"id": 1`. Trả entity ra thì lộ `password` và dễ vỡ vì lazy-loading.

### Request

**`RegisterRequest`**
| Field | Ràng buộc |
|---|---|
| `username` | không rỗng |
| `email` | không rỗng, đúng định dạng email |
| `password` | không rỗng, **tối thiểu 8 ký tự** |

**`LoginRequest`** — `username`, `password` không rỗng.

**`MovieRequest`**
| Field | Ràng buộc |
|---|---|
| `title` | không rỗng |
| `description`, `posterUrl`, `backdropUrl`, `releaseDate` | tuỳ chọn |
| `duration` | bắt buộc, > 0 |
| `rating` | bắt buộc, 0 đến 10 |
| `isTrending`, `isTopRated` | mặc định `false` |
| `movieType` | bắt buộc — `movie` \| `series` \| `anime` |
| `status` | bắt buộc — `released` \| `upcoming` \| `ongoing` |
| `genreIds` | `Set<Integer>`, ID không tồn tại → 404 |

**`EpisodeRequest`** — `episodeNumber` bắt buộc > 0, `videoUrl` không rỗng,
`title`/`duration`/`thumbnailUrl` tuỳ chọn.

**`GenreRequest`** — `name` ≤ 50 ký tự; `slug` ≤ 50 ký tự theo regex
`^[a-z0-9]+(-[a-z0-9]+)*$`.

**`ReviewRequest`** — `content` không rỗng, `rating` bắt buộc từ 1 đến 10.

**`WatchHistoryRequest`** — `movieId` bắt buộc, `lastPosition` bắt buộc ≥ 0,
`episodeId` tuỳ chọn.

**`UpdateProfileRequest`** — `avatarUrl` ≤ 500 ký tự.

**`CreateUserRequest`** — như `RegisterRequest`, thêm `role` (bắt buộc) và
`avatarUrl`. Chỉ ADMIN dùng.

### Response

**`AuthResponse`** — `token`, `username`, `role`

**`UserDTO`** — `id`, `username`, `email`, `avatarUrl`, `role`, `createAt`
(không có `password`)

**`MovieDTO`** — `id`, `title`, `description`, `posterUrl`, `backdropUrl`,
`releaseDate`, `duration`, `rating`, `isTrending`, `isTopRated`, `movieType`,
`status`, `genres: Set<GenreDTO>`

**`GenreDTO`** — `id`, `name`, `slug`

**`EpisodeDTO`** — `id`, `title`, `episodeNumber`, `videoUrl`, `thumbnailUrl`, `duration`

**`ReviewDTO`** — `id`, `rating`, `content`, `createAt`, `userId`, `username`
(chỉ lấy tên và ID của người bình luận, không lộ email)

**`FavoriteDTO`** — `movieId`, `movieTitle`, `posterUrl`, `addedAt`

**`WatchHistoryDTO`** — `id`, `movieId`, `movieTitle`, `posterUrl`, `episodeId`,
`episodeNumber`, `lastPosition`, `updatedAt` (2 field `episode*` có thể `null`)

**`PageResponse<T>`** — khuôn phân trang:

```json
{
  "content": [ /* ... */ ],
  "currentPage": 0,
  "totalPages": 3,
  "totalElements": 24,
  "last": false
}
```

> Field trong Java tên là `isLast`, nhưng Jackson đọc qua getter `isLast()` nên
> **tên trong JSON là `last`**. FE phải đọc `last`. Có test khoá lại điều này
> (`ApiFlowTest.pagedResponseShape`) để không ai đổi vô tình.

### Chuyển Entity → DTO

Dùng **ModelMapper** với `MatchingStrategies.STRICT` (cấu hình trong `AppConfig`).
STRICT = chỉ map khi tên field trùng khớp hoàn toàn.

Lý do không dùng chế độ mặc định: nó tự suy diễn (`movie.id` → `movieId`), và
việc suy diễn đó chạm vào LAZY proxy hoặc map sai field mà không báo lỗi gì —
chỉ ra `null` lúc runtime.

Hệ quả: field trong DTO mà tên không trùng entity thì **phải set tay** trong
`convertToDTO`. Xem `FavoriteServiceImpl` (map tay toàn bộ),
`WatchHistoryServiceImpl` và `ReviewServiceImpl` (map xong rồi set thêm).

---

## 8. Xử lý lỗi

`GlobalExceptionHandler` (`@RestControllerAdvice`) gom mọi exception về một khuôn:

```json
{
  "error": "Dữ liệu đầu vào không hợp lệ",
  "details": { "title": "Tên phim không được để trống" },
  "timestamp": "2026-09-26T21:40:00.123"
}
```

`details` là `String` với lỗi thường, là object `{field: message}` với lỗi validation.

| HTTP | Exception | Khi nào |
|---|---|---|
| 400 | `BadRequestException` | vi phạm nghiệp vụ (trùng dữ liệu, tập trùng số...) |
| 400 | `MethodArgumentNotValidException` | `@Valid` không qua — `details` là map theo field |
| 400 | `MethodArgumentTypeMismatchException` | sai kiểu param, hay gặp nhất là enum sai. Thông báo **liệt kê luôn các giá trị cho phép** |
| 400 | `MissingServletRequestParameterException` | thiếu query param bắt buộc |
| 400 | `HttpMessageNotReadableException` | JSON sai cú pháp |
| 401 | `BadCredentialsException`, `UsernameNotFoundException` | sai tài khoản / mật khẩu |
| 403 | `AccessDeniedException` | đã đăng nhập nhưng không đủ quyền |
| 404 | `ResourceNotFoundException` | không tìm thấy bản ghi |
| 404 | `NoHandlerFoundException` | URL không tồn tại |
| 409 | `DataIntegrityViolationException` | vỡ ràng buộc DB (UNIQUE, khoá ngoại) |
| 500 | `Exception` | mọi lỗi còn lại |

Riêng 401/403 phát sinh **trong filter chain** (chưa tới controller) thì
`@RestControllerAdvice` không bắt được. Hai class riêng lo việc đó:
`RestAuthenticationEntryPoint` → 401 JSON, `RestAccessDeniedHandler` → 403 JSON.
Không có chúng thì FE nhận response rỗng, không biết lỗi gì.

**Handler 500 có `log.error(..., ex)` kèm đường dẫn request.** Đây là bắt buộc:
khi chạy 3 container sau load-balancer, không log stacktrace thì không còn cách
nào debug.

---

## 9. Cấu hình & biến môi trường

Mọi giá trị trong `application.properties` đều override được bằng biến môi trường,
phục vụ chạy nhiều instance trong Docker:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3307/cinehub_db?...` | chuỗi kết nối |
| `DB_USER` | `root` | |
| `DB_PASSWORD` | — | **phải đặt** |
| `DB_POOL_SIZE` | `10` | HikariCP max pool, giới hạn để 3 instance không làm cạn kết nối MySQL |
| `JWT_SECRET` | — | **phải đổi**, tối thiểu 64 ký tự |
| `JWT_EXPIRATION` | `86400000` | thời hạn token (ms) = 1 ngày |
| `SERVER_PORT` | `8080` | |
| `INSTANCE_ID` | `local` | tên instance, hiện trong header và log |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:3000` | domain FE, ngăn cách bởi dấu phẩy |
| `SHOW_SQL` | `true` | in câu SQL ra log |
| `LOG_LEVEL` | `INFO` | mức log của `com.example.cinehub` |
| `SPRING_PROFILES_ACTIVE` | — | đặt `docker` để bật log JSON |

Vài cấu hình cố định, không nên đổi:

| Property | Giá trị | Lý do |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `none` | schema do `cinehub_db.sql` quản lý. Xem mục 13 |
| `spring.jpa.open-in-view` | `false` | tránh giữ connection suốt vòng đời request |
| `spring.jpa.properties.hibernate.default_batch_fetch_size` | `30` | nạp `genres` của nhiều phim trong một câu `IN (...)` thay vì một query mỗi phim (N+1) |
| `server.servlet.encoding.force` | `true` | tiếng Việt không bị lỗi font |
| `management.endpoints.web.exposure.include` | `health,info` | chỉ mở 2 endpoint actuator cần thiết |

---

## 10. Phục vụ load-balancer và ELK

### Health check

`GET /actuator/health` để `permitAll` sẵn, load-balancer gọi được mà không cần token.

### Nhận biết instance

Mọi response đều có header `X-Instance-Id`, lấy từ biến `INSTANCE_ID`.
Chạy 3 container với `INSTANCE_ID=web-1`, `web-2`, `web-3` là thấy ngay
round-robin có hoạt động không:

```bash
for i in 1 2 3 4 5 6; do
  curl -s -D - -o /dev/null http://localhost/api/movies | grep -i X-Instance-Id
done
```

Header này cũng được khai báo trong `exposedHeaders` của CORS, nên JavaScript
phía FE đọc được.

### Log JSON cho ELK

Bật bằng `SPRING_PROFILES_ACTIVE=docker`. Khi đó `logback-spring.xml` xuất log
JSON một dòng ra stdout để Filebeat đọc từ container. Không đặt profile thì log
ra dạng text cho dễ đọc khi chạy local.

`RequestLogFilter` đặt các field vào **MDC**, nên chúng thành cột riêng trong
Elasticsearch — không phải parse lại từ chuỗi `message`:

```json
{
  "@timestamp": "2026-09-26T14:40:00.123Z",
  "level": "INFO",
  "app": "CineHub-BE",
  "instance": "web-1",
  "httpMethod": "GET",
  "path": "/api/movies",
  "status": "200",
  "durationMs": "14",
  "clientIp": "172.18.0.1",
  "message": "GET /api/movies -> 200 (14ms)"
}
```

Hai chi tiết quan trọng:

- **`clientIp` đọc `X-Forwarded-For` trước.** Sau load-balancer thì
  `request.getRemoteAddr()` chỉ ra IP của LB, không phải IP client — dữ liệu sẽ
  vô nghĩa với phần anomaly detection.
- **`/actuator/**` không được ghi log.** Load-balancer health-check liên tục,
  ghi hết sẽ làm nhiễu dữ liệu traffic.

Các field này đủ để dựng dashboard Kibana (traffic theo instance, phân bố status
code, thời gian phản hồi) và làm đầu vào cho module `anomaly-detection`.

---

## 11. Test

```bash
./mvnw test
```

Dùng **H2 in-memory** (`application-test.properties`, profile `test`), nên chạy
được **không cần MySQL hay Docker**. 14 test, chia 3 nhóm:

**`cinehubTests`** (1) — Spring context dựng được: thiếu bean, sai `@Value`,
circular dependency đều lộ ra ở đây.

**`SecurityRulesTest`** (7) — kiểm tra từng dòng trong bảng phân quyền. Đây là chỗ
dễ sai nhất: thiếu một dòng `requestMatchers` là trang chủ FE trả 401, hoặc
ngược lại USER xoá được phim.

- `/actuator/health` public
- response có header `X-Instance-Id`
- 6 endpoint trang chủ gọi được khi chưa đăng nhập
- chưa đăng nhập → 401 **kèm JSON** (không phải trang trắng)
- USER gọi `POST /api/movies` → 403 kèm JSON
- USER gọi `GET /api/users`, `GET /api/movies/all` → 403
- enum sai trong query param → 400 (không phải 500)

**`ApiFlowTest`** (6) — đi hết luồng nghiệp vụ thật qua cả `JwtFilter`:

- đăng ký luôn ra role USER, password được hash, response không chứa password,
  `createAt`/`updateAt` được set
- đăng ký trùng username → 400 có thông báo
- sai mật khẩu → 401 có JSON
- khuôn JSON của `PageResponse` (khoá hợp đồng với FE)
- token rác → 401
- **luồng đầy đủ**: ADMIN tạo thể loại (+ kiểm tra trùng slug) → tạo phim gắn
  thể loại → xem chi tiết → trending/top-rated → thêm tập (+ kiểm tra trùng số
  tập) → USER đăng ký, đăng nhập, xem `/me` → yêu thích (+ trùng, + `/check`) →
  lưu lịch sử 2 lần chỉ ra 1 bản ghi → bình luận (+ rating sai) → **người khác
  không xoá được bình luận, chính chủ thì được** → USER không xoá được phim,
  ADMIN thì được

### Chưa cover

- Chạy thật với MySQL (mình chưa verify được vì máy chưa bật MySQL/Docker).
  Cụ thể là hành vi `ON DELETE CASCADE` — xem mục 13.
- `PUT /api/movies/{id}`, `PUT /api/episodes/{id}`, các API quản trị user.
- Test đơn vị cho từng service (hiện chỉ có test tích hợp qua HTTP).

---

## 12. Quy ước khi viết thêm code

1. **Controller không gọi Repository.** Chỉ gọi Service.
2. **Không nhận `entity/*` trong `@RequestBody`.** Luôn tạo class trong `dto/request`.
3. **Không trả `entity/*` ra response.** Luôn qua `dto/response`.
4. **Logic nằm ở Service**, kèm `@Transactional` (đọc thì `readOnly = true`).
5. **`userId` lấy từ token qua `CurrentUser`**, không nhận từ request body —
   nếu không thì client tự khai mình là người khác.
6. **Mã hoá password trong Service**, không ở Controller.
7. **Thêm endpoint mới thì cập nhật `SecurityConfig`** — mặc định
   `anyRequest().authenticated()`, nên endpoint công khai mà quên khai báo sẽ trả 401.
8. **Thêm field vào DTO mà tên không trùng entity thì set tay** trong `convertToDTO`
   (ModelMapper đang chạy STRICT).
9. **Kiểm tra trùng dữ liệu trước khi `save`** và ném `BadRequestException`,
   thay vì để vỡ ràng buộc UNIQUE thành 409/500.
10. **Sửa phân quyền thì thêm test vào `SecurityRulesTest`.**

### Thêm một API mới — trình tự

```
1. entity/            (nếu là bảng mới)
2. repository/        interface + query method cần thiết
3. dto/request/       + annotation validation
4. dto/response/
5. service/           thêm method vào interface
6. service/impl/      cài đặt + @Transactional + convertToDTO
7. controller/        + @Valid
8. security/SecurityConfig   khai báo quyền  ← dễ quên nhất
9. src/test/          thêm test
10. README này        cập nhật mục 6
```

---

## 13. Những chỗ dễ sập — đọc trước khi sửa

**Xoá phim dựa vào `ON DELETE CASCADE` của MySQL.**
Entity không khai báo cascade ở tầng JPA. `movieRepository.delete(movie)` chỉ
chạy đúng khi schema được tạo từ `database/cinehub_db.sql`. Vì vậy:

- **Đừng** đặt `spring.jpa.hibernate.ddl-auto=update` hay `create` cho production.
  Schema Hibernate sinh ra không có cascade, và xoá phim sẽ lỗi khoá ngoại.
- Test dùng H2 với `ddl-auto=create-drop` nên **không** có cascade. Đó là lý do
  `ApiFlowTest` xoá một phim riêng chưa có ai yêu thích/xem dở, chứ không xoá phim
  chính. Nói cách khác: **test không chứng minh được cascade chạy đúng trên MySQL** —
  cần verify tay một lần khi có DB thật.

**Bảng `favorites` chỉ được entity `Favorite` quản lý.**
Đừng thêm `@ManyToMany` trỏ vào bảng đó ở `User`. Hai mapping cùng một bảng thì
Hibernate ghi trùng và xung đột (bản `User` cũ có lỗi này, đã bỏ). Muốn lấy danh
sách yêu thích thì gọi `FavoriteService`.

**ModelMapper đang chạy `MatchingStrategies.STRICT`.**
Xem lại mục 7 — field tên không trùng thì phải map tay.

**CORS chỉ khai ở `SecurityConfig`.**
Đừng thêm lại `@CrossOrigin` trên controller: không xử lý được preflight `OPTIONS`
và gây nhầm lẫn vì có hai nơi cấu hình.

**Thứ tự `requestMatchers` trong `SecurityConfig` là quan trọng.**
Rule khớp trước thắng. Rule ADMIN phải đặt trên rule `permitAll` có ký tự `*`.

**`app.jwt.secret` phải đủ dài.**
`Keys.hmacShaKeyFor()` ném exception khi khởi động nếu secret quá ngắn —
app sẽ không lên được.

---

## 14. Việc còn lại

### Cần bạn quyết

**`movies.rating` hiện là số ADMIN tự nhập**, chưa tính trung bình từ bảng
`reviews`. Mình để nguyên vì không rõ đó là ý định hay thiếu sót. Nếu muốn tính
tự động thì thêm vào `ReviewServiceImpl.addReview()` / `deleteReview()`: tính
`AVG(rating)` theo `movieId` rồi cập nhật lại `movies.rating`.

**Class khởi động tên `cinehub`** (chữ thường) — sai quy ước Java, nên là
`CineHubApplication`. Mình không đổi để tránh churn không cần thiết.

### Chưa làm

- **`FE/` chưa khởi tạo.** Danh sách API ở mục 6 là hợp đồng để bắt đầu.
- **Refresh token** — hiện chỉ có access token 1 ngày, hết hạn thì đăng nhập lại.
- **Đổi mật khẩu / quên mật khẩu.**
- **Upload ảnh** — `posterUrl`, `avatarUrl` đang là URL do client tự nhập.
- **Phân trang cho reviews và history** — hiện trả về toàn bộ danh sách.
- **`Dockerfile`** cho BE (`load-balancer/` đã có, BE chưa).
- **Rate limiting** — sẽ cần khi làm phần anomaly detection.
