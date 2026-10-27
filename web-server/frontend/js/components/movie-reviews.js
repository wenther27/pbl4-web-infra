import { reviewApi } from '../api/reviews.js';
import { getSession } from '../auth/session.js';
import { escapeHtml } from './movie-card.js';

function reviewForm(session) {
  if (!session) {
    return `<div class="review-login"><div><h3>Chia sẻ cảm nhận của bạn</h3><p>Đăng nhập để chấm điểm và viết bình luận cho phim.</p></div><button class="button button--primary" type="button" data-review-login>Đăng nhập để đánh giá</button></div>`;
  }

  const scores = Array.from({ length: 10 }, (_, index) => index + 1)
    .map((score) => `<label title="${score}/10 sao"><input type="radio" name="rating" value="${score}" required aria-label="${score} trên 10 sao"><span aria-hidden="true">★</span></label>`)
    .join('');

  return `<form class="review-form" id="review-form">
    <div class="review-form__heading"><div><p class="eyebrow">Đánh giá của bạn</p><h3>Xin chào, ${escapeHtml(session.username)}</h3></div><span>Chọn điểm từ 1 đến 10</span></div>
    <div class="score-picker-row"><fieldset class="score-picker"><legend class="sr-only">Điểm đánh giá từ 1 đến 10 sao</legend>${scores}</fieldset><output class="score-output" data-score-output>Chưa chọn</output></div>
    <label class="review-content"><span>Nội dung đánh giá</span><textarea name="content" required maxlength="1000" rows="4" placeholder="Bạn cảm thấy thế nào về bộ phim này?"></textarea><small>Tối đa 1.000 ký tự</small></label>
    <div class="review-form__footer"><p id="review-form-message" role="status" aria-live="polite"></p><button class="button button--primary" type="submit">Gửi đánh giá</button></div>
  </form>`;
}

function formatReviewDate(value) {
  if (!value) return 'Vừa xong';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function reviewList(reviews) {
  if (!reviews.length) {
    return '<div class="reviews-empty"><span>☆</span><h3>Chưa có đánh giá</h3><p>Hãy là người đầu tiên chia sẻ cảm nhận về phim.</p></div>';
  }

  return reviews.map((review) => `<article class="review-card">
    <div class="review-avatar" aria-hidden="true">${escapeHtml((review.username || 'U').slice(0, 1).toUpperCase())}</div>
    <div class="review-card__body"><header><div><strong>${escapeHtml(review.username || 'Ẩn danh')}</strong><time>${escapeHtml(formatReviewDate(review.createAt))}</time></div><span class="review-score">★ ${Number(review.rating || 0)}/10</span></header><p>${escapeHtml(review.content || '')}</p></div>
  </article>`).join('');
}

function updateSummary(root, reviews) {
  const average = reviews.length
    ? reviews.reduce((sum, review) => sum + Number(review.rating || 0), 0) / reviews.length
    : 0;
  root.querySelector('[data-review-average]').textContent = reviews.length ? average.toFixed(1) : '—';
  root.querySelector('[data-review-count]').textContent = `${reviews.length} đánh giá`;
  root.querySelector('[data-review-list]').innerHTML = reviewList(reviews);
}

function setFormMessage(form, message, type = '') {
  const element = form.querySelector('#review-form-message');
  element.textContent = message;
  element.className = type ? `is-${type}` : '';
}

function bindScorePicker(form) {
  const labels = [...form.querySelectorAll('.score-picker label')];
  const output = form.querySelector('[data-score-output]');

  function paint(score) {
    labels.forEach((label, index) => label.classList.toggle('is-filled', index < score));
  }

  function selectedScore() {
    return Number(form.querySelector('input[name="rating"]:checked')?.value || 0);
  }

  labels.forEach((label, index) => {
    label.addEventListener('mouseenter', () => paint(index + 1));
    label.querySelector('input').addEventListener('change', () => {
      const score = selectedScore();
      paint(score);
      output.textContent = `${score}/10 sao`;
    });
  });
  form.querySelector('.score-picker').addEventListener('mouseleave', () => paint(selectedScore()));
}

export async function mountMovieReviews({ root, movieId }) {
  const session = getSession();
  root.innerHTML = `<div class="container reviews-section">
    <div class="reviews-heading"><div><p class="eyebrow">Cộng đồng CineHub</p><h2>Đánh giá phim</h2><p>Những cảm nhận mới nhất từ người xem.</p></div><div class="reviews-summary"><strong data-review-average>—</strong><span>★ / 10</span><small data-review-count>Đang tải…</small></div></div>
    <div class="reviews-layout"><div><div data-review-form>${reviewForm(session)}</div><div class="reviews-list" data-review-list><div class="loading-state">Đang tải đánh giá…</div></div></div><aside class="review-guidelines"><h3>Gửi đánh giá hữu ích</h3><ul><li>Chia sẻ cảm nhận thật của bạn.</li><li>Không tiết lộ nội dung quan trọng.</li><li>Tôn trọng ý kiến của người xem khác.</li></ul></aside></div>
  </div>`;

  root.querySelector('[data-review-login]')?.addEventListener('click', () => {
    document.querySelector('[data-auth-open="login"]')?.click();
  });

  const form = root.querySelector('#review-form');
  if (form) bindScorePicker(form);
  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const submitButton = form.querySelector('button[type="submit"]');
    const data = new FormData(form);
    submitButton.disabled = true;
    submitButton.textContent = 'Đang gửi…';
    setFormMessage(form, '');
    try {
      await reviewApi.create(movieId, {
        rating: Number(data.get('rating')),
        content: String(data.get('content')).trim()
      });
      form.reset();
      form.querySelectorAll('.score-picker label').forEach((label) => label.classList.remove('is-filled'));
      form.querySelector('[data-score-output]').textContent = 'Chưa chọn';
      setFormMessage(form, 'Đánh giá của bạn đã được đăng.', 'success');
      updateSummary(root, await reviewApi.getByMovie(movieId));
    } catch (error) {
      const message = error.status === 401 || error.status === 403
        ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
        : error.message;
      setFormMessage(form, message, 'error');
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = 'Gửi đánh giá';
    }
  });

  try {
    updateSummary(root, await reviewApi.getByMovie(movieId));
  } catch (error) {
    root.querySelector('[data-review-count]').textContent = 'Chưa tải được';
    root.querySelector('[data-review-list]').innerHTML = `<div class="reviews-empty"><span>!</span><h3>Không tải được đánh giá</h3><p>${escapeHtml(error.message)}</p></div>`;
  }
}
