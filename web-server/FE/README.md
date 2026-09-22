# CineHub Frontend

Placeholder cho frontend (React/Vite, Vue, ...). Chưa chọn framework.

Khi khởi tạo, giữ cấu trúc:
- `src/`        – source code
- `Dockerfile`  – build static rồi serve bằng nginx
- gọi API BE qua biến môi trường `VITE_API_URL` (hoặc tương đương), mặc định trỏ tới load-balancer.
