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
- `js/admin`: các module quản trị tách theo phim, thể loại, tập phim, người dùng và đánh giá.
- `assets`: hình ảnh và tài nguyên tĩnh cục bộ.

Đổi địa chỉ backend tại `js/config.js` hoặc đặt khóa `cinehubApiBaseUrl` trong Local Storage của trình duyệt.

Hộp thoại đăng nhập/đăng ký được dùng chung trên mọi trang qua `js/components/auth-modal.js` và không điều hướng sang trang xác thực riêng.

## Trang quản trị

Đăng nhập bằng tài khoản có role `ADMIN`, sau đó mở `http://localhost:5173/pages/admin/`. Trang quản trị gọi API bằng JWT và gồm các khu vực tổng quan, phim, thể loại, tập phim, người dùng và đánh giá. Giao diện chỉ hỗ trợ đúng các API backend đang có; hiện chưa có thao tác đổi role và chưa có sửa/xóa thể loại.

Form thêm/sửa phim và tập phim cho phép chọn ảnh trực tiếp từ máy, xem trước ảnh và tải lên backend. Các định dạng hỗ trợ gồm JPG, PNG, WebP và GIF, dung lượng tối đa 5 MB.
