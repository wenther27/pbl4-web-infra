# web-server

Ứng dụng web mẫu (CineHub) dùng làm backend đặt sau load-balancer.

```
web-server/
├── BE/   Spring Boot 3.5 / Java 21 – REST API (JWT, JPA, MySQL), log JSON cho ELK
└── FE/   Frontend (chưa khởi tạo)
```

## BE
```bash
cd BE
cp src/main/resources/application.properties.example src/main/resources/application.properties
# sửa DB user/pass, JWT secret
./mvnw spring-boot:run
```
