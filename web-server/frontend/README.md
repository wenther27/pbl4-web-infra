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

## Trang xem phim

Từ trang chi tiết, chọn **Xem phim** để mở `pages/watch.html?id=<movieId>`. Trang này lấy danh sách tập từ backend, phát `videoUrl`, hỗ trợ chuyển tập trước/sau và tự chuyển tập khi phát xong. Khi người dùng đã đăng nhập, vị trí xem được gửi tới API lịch sử; khách vẫn có thể xem nhưng không lưu tiến độ.

Nguồn video phải là URL HTTP/HTTPS truy cập được từ trình duyệt và máy chủ chứa video cần cho phép phát trực tuyến. Các URL `cdn.cinehub.com` trong dữ liệu mẫu chỉ là dữ liệu minh họa.

Trang chi tiết phim hiển thị danh sách đánh giá công khai. Thành viên đã đăng nhập có thể chọn điểm từ 1–10 và gửi bình luận; khách được yêu cầu đăng nhập bằng hộp thoại chung trước khi đánh giá.

## Trang quản trị

Đăng nhập bằng tài khoản có role `ADMIN`, sau đó mở `http://localhost:5173/pages/admin/`. Trang quản trị gọi API bằng JWT và gồm các khu vực tổng quan, phim, thể loại, tập phim, người dùng và đánh giá. Giao diện chỉ hỗ trợ đúng các API backend đang có; hiện chưa có thao tác đổi role và chưa có sửa/xóa thể loại.

Form thêm/sửa phim và tập phim cho phép chọn ảnh trực tiếp từ máy, xem trước ảnh và tải lên backend. Các định dạng hỗ trợ gồm JPG, PNG, WebP và GIF, dung lượng tối đa 5 MB.

Form tập phim cũng cho phép chọn video trực tiếp từ máy, xem trước và tải lên trước khi lưu tập. Hỗ trợ MP4, WebM và OGG, dung lượng tối đa mặc định 500 MB. File video được backend lưu trong `uploads/videos`; production cần dùng volume bền vững hoặc object storage dùng chung.
