import { movieApi } from '../api/client.js';
import { mountLayout } from '../components/layout.js';
import { escapeHtml, normalizeMovie } from '../components/movie-card.js';
import { fallbackMovies } from '../data/fallback-movies.js';

mountLayout();

const id = new URLSearchParams(window.location.search).get('id');
const content = document.querySelector('#detail-content');

function render(movie) {
  const item = normalizeMovie(movie);
  const genres = item.genres.map((genre) => genre.name).join(' · ') || 'Đang cập nhật';
  document.title = `${item.title} | CineHub`;
  content.innerHTML = `<section class="detail-hero" style="background-image:url('${item.backdropUrl}')"><div class="container detail-hero__content"><div class="detail-hero__meta"><span>★ ${item.rating.toFixed(1)}</span><span>${item.releaseDate?.slice(0,4) || 'Mới'}</span><span>${item.duration || '—'} phút</span><span>${escapeHtml(genres)}</span></div><h1>${escapeHtml(item.title)}</h1><p>${escapeHtml(item.description)}</p><a class="button button--primary" href="#information">▶ Xem thông tin</a></div></section>
    <section class="container detail-body" id="information"><article class="detail-panel"><p class="eyebrow">Tóm tắt nội dung</p><h2>${escapeHtml(item.title)}</h2><p>${escapeHtml(item.description)}</p></article><aside class="detail-panel"><h2>Thông tin phim</h2><div class="detail-facts"><div><span>Đánh giá</span><b>${item.rating.toFixed(1)}/10</b></div><div><span>Năm phát hành</span><b>${item.releaseDate?.slice(0,4) || '—'}</b></div><div><span>Thời lượng</span><b>${item.duration || '—'} phút</b></div><div><span>Thể loại</span><b>${escapeHtml(genres)}</b></div></div></aside></section>`;
}

async function loadDetail() {
  let movie;
  try {
    const response = await movieApi.getMovies({ page: 0, size: 100 });
    movie = response.content?.find((item) => String(item.id) === String(id));
  } catch { /* dùng dữ liệu mẫu ở dưới */ }
  movie ||= fallbackMovies.find((item) => String(item.id) === String(id)) || fallbackMovies[0];
  render(movie);
}

loadDetail();
