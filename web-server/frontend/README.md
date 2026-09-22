# CineHub Frontend

Frontend độc lập viết bằng HTML, CSS và JavaScript thuần. Vite chỉ được dùng làm máy chủ phát triển và công cụ build, không phải framework giao diện.

## Chạy dự án

```bash
npm install
npm run dev
```

Mở `http://localhost:5173`. Backend mặc định được gọi tại `http://localhost:8080/api`.

## Cấu trúc

- `pages`: các trang HTML ngoài trang chủ.
- `css`: biến thiết kế, bố cục, component và style riêng từng trang.
- `js/api`: lớp giao tiếp REST API.
- `js/components`: thành phần giao diện dùng lại.
- `js/pages`: logic riêng cho từng trang.
- `js/data`: dữ liệu mẫu dùng khi backend chưa chạy.
- `js/auth`: lưu và đọc phiên đăng nhập JWT ở phía trình duyệt.
- `assets`: hình ảnh và tài nguyên tĩnh cục bộ.

Đổi địa chỉ backend tại `js/config.js` hoặc đặt khóa `cinehubApiBaseUrl` trong Local Storage của trình duyệt.

Hộp thoại đăng nhập/đăng ký được dùng chung trên mọi trang qua `js/components/auth-modal.js` và không điều hướng sang trang xác thực riêng.
