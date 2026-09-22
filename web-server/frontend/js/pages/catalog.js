import { APP_CONFIG } from '../config.js';
import { movieApi } from '../api/client.js';
import { mountLayout } from '../components/layout.js';
import { movieCard, normalizeMovie } from '../components/movie-card.js';
import { fallbackGenres, fallbackMovies } from '../data/fallback-movies.js';

mountLayout();

const elements = {
  filters: document.querySelector('.filters'), type: document.querySelector('#type-filter'), status: document.querySelector('#status-filter'),
  genre: document.querySelector('#genre-filter'), sort: document.querySelector('#sort-filter'), grid: document.querySelector('#catalog-grid'),
  count: document.querySelector('#result-count'), tags: document.querySelector('#active-filters'), pagination: document.querySelector('#pagination')
};
const url = new URL(window.location.href);
let currentPage = 0;
let usingFallback = false;

function initialValues() {
  elements.type.value = url.searchParams.get('type') || '';
}

async function loadGenres() {
  let genres = fallbackGenres;
  try { genres = await movieApi.getGenres(); } catch { /* giao diện vẫn hoạt động bằng dữ liệu mẫu */ }
  elements.genre.innerHTML += genres.map((genre) => `<option value="${genre.slug}">${genre.name}</option>`).join('');
  elements.genre.value = url.searchParams.get('genre') || '';
}

function getFilters() {
  const [sortBy, direction] = elements.sort.value.split(',');
  return { title: url.searchParams.get('q') || '', movieType: elements.type.value, status: elements.status.value, page: elements.genre.value ? 0 : currentPage, size: elements.genre.value ? 100 : APP_CONFIG.pageSize, sortBy, direction };
}

function renderTags() {
  const labels = [elements.type.selectedOptions[0]?.text, elements.status.selectedOptions[0]?.text, elements.genre.selectedOptions[0]?.text].filter((label) => label && !label.startsWith('Tất cả'));
  const query = url.searchParams.get('q');
  if (query) labels.unshift(`Tìm: “${query}”`);
  elements.tags.innerHTML = labels.map((label) => `<span class="filter-tag">${label}</span>`).join('');
}

function filterFallback(filters) {
  return fallbackMovies.filter((movie) => (!filters.title || movie.title.toLowerCase().includes(filters.title.toLowerCase())) && (!filters.movieType || movie.movieType === filters.movieType) && (!filters.status || movie.status === filters.status) && (!elements.genre.value || movie.genres.some((genre) => genre.slug === elements.genre.value)));
}

function renderPagination(totalPages) {
  if (totalPages <= 1) { elements.pagination.innerHTML = ''; return; }
  elements.pagination.innerHTML = Array.from({ length: totalPages }, (_, index) => `<button class="${index === currentPage ? 'is-active' : ''}" data-page="${index}" type="button">${index + 1}</button>`).join('');
  elements.pagination.querySelectorAll('button').forEach((button) => button.addEventListener('click', () => { currentPage = Number(button.dataset.page); loadMovies(); window.scrollTo({ top: 200, behavior: 'smooth' }); }));
}

async function loadMovies() {
  elements.grid.innerHTML = '<div class="loading-state">Đang tải danh sách phim…</div>';
  const filters = getFilters();
  let content;
  let totalElements;
  let totalPages;
  usingFallback = false;

  try {
    const response = await movieApi.getMovies(filters);
    content = response.content || [];
    totalElements = response.totalElements || 0;
    totalPages = response.totalPages || 0;
    if (elements.genre.value) {
      content = content.filter((movie) => movie.genres?.some((genre) => genre.slug === elements.genre.value));
      totalElements = content.length;
      totalPages = 1;
    }
  } catch (error) {
    usingFallback = true;
    content = filterFallback(filters);
    totalElements = content.length;
    totalPages = 1;
    console.info('Backend chưa sẵn sàng, đang dùng dữ liệu giao diện mẫu.', error);
  }

  elements.count.textContent = totalElements;
  elements.grid.innerHTML = `${usingFallback ? '<p class="error-banner">Backend chưa chạy — bộ lọc đang dùng dữ liệu mẫu.</p>' : ''}${content.length ? content.map((movie) => movieCard(normalizeMovie(movie), '..')).join('') : '<div class="empty-state">Không tìm thấy phim phù hợp.</div>'}`;
  renderTags();
  renderPagination(totalPages);
}

document.querySelector('#apply-filters').addEventListener('click', () => { currentPage = 0; loadMovies(); });
document.querySelector('#reset-filters').addEventListener('click', () => { elements.type.value = ''; elements.status.value = ''; elements.genre.value = ''; elements.sort.value = 'rating,desc'; currentPage = 0; url.searchParams.delete('q'); loadMovies(); });
document.querySelector('#filter-toggle').addEventListener('click', (event) => { const open = elements.filters.classList.toggle('is-open'); event.currentTarget.setAttribute('aria-expanded', String(open)); });

initialValues();
await loadGenres();
await loadMovies();
