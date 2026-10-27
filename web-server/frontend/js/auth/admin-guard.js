import { getSession } from "./session.js";

export function getAdminSession() {
  const session = getSession();
  return session?.role === "ADMIN" ? session : null;
}

export function renderAdminAccessDenied() {
  const session = getSession();
  const access = document.querySelector("#admin-access");
  const shell = document.querySelector("#admin-shell");
  shell.hidden = true;
  access.hidden = false;
  access.innerHTML = `<section class="admin-access__card">
    <span class="admin-access__icon">⌾</span>
    <p class="eyebrow">Khu vực được bảo vệ</p>
    <h1>${session ? "Bạn không có quyền quản trị" : "Vui lòng đăng nhập"}</h1>
    <p>${session ? "Tài khoản hiện tại có quyền USER. Chỉ tài khoản ADMIN mới truy cập được trang này." : "Bạn cần đăng nhập bằng tài khoản ADMIN trước khi mở trang quản trị."}</p>
    <a class="admin-button admin-button--primary" href="../../index.html">Về trang chủ</a>
  </section>`;
}
