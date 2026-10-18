import { movieApi } from '../api/client.js';
import { watchApi } from '../api/watch.js';
import { mountLayout } from '../components/layout.js';
import { escapeHtml, normalizeMovie } from '../components/movie-card.js';
import { createVideoPlayer } from '../components/video-player.js';

mountLayout();

const params = new URLSearchParams(window.location.search);
const movieId = params.get('id');
const requestedEpisodeId = params.get('episode');
const content = document.querySelector('#watch-content');

let movie;
let episodes = [];
let currentIndex = 0;
let player;
let history = [];

function renderError(message) {
  content.innerHTML = `<div class="container watch-state"><span class="watch-state__icon">!</span><h1>Chưa thể mở phim</h1><p>${escapeHtml(message)}</p><a class="button button--primary" href="./catalog.html">Quay lại danh mục</a></div>`;
}

function episodeLabel(episode) {
  return episode.title ? `Tập ${episode.episodeNumber}: ${episode.title}` : `Tập ${episode.episodeNumber}`;
}

function renderShell() {
  const item = normalizeMovie(movie);
  content.innerHTML = `<section class="container watch-layout">
    <div class="watch-main">
      <nav class="watch-breadcrumb" aria-label="Đường dẫn"><a href="./catalog.html">Danh mục</a><span>/</span><a href="./movie-detail.html?id=${encodeURIComponent(item.id)}">${escapeHtml(item.title)}</a><span>/</span><b id="current-episode-breadcrumb">Xem phim</b></nav>
      <div class="video-shell">
        <video id="movie-player" controls playsinline preload="metadata" poster="${escapeHtml(item.backdropUrl)}"></video>
        <p id="player-message" class="player-message" hidden></p>
      </div>
      <div class="watch-toolbar">
        <div><p class="eyebrow">Đang xem</p><h1 id="current-episode-title">${escapeHtml(item.title)}</h1><p id="current-episode-meta" class="watch-meta"></p></div>
        <div class="watch-controls"><button id="previous-episode" class="button button--secondary" type="button">← Tập trước</button><button id="next-episode" class="button button--primary" type="button">Tập tiếp →</button></div>
      </div>
      <article class="watch-description"><h2>${escapeHtml(item.title)}</h2><p>${escapeHtml(item.description)}</p></article>
    </div>
    <aside class="episode-panel"><div class="episode-panel__header"><div><p class="eyebrow">Danh sách phát</p><h2>Các tập phim</h2></div><span>${episodes.length} tập</span></div><div id="episode-list" class="episode-list"></div></aside>
  </section>`;

  player = createVideoPlayer({
    video: document.querySelector('#movie-player'),
    message: document.querySelector('#player-message'),
    onProgress: saveCurrentProgress,
    onEnded: () => {
      if (currentIndex < episodes.length - 1) selectEpisode(currentIndex + 1, true);
    }
  });

  document.querySelector('#previous-episode').addEventListener('click', () => selectEpisode(currentIndex - 1, true));
  document.querySelector('#next-episode').addEventListener('click', () => selectEpisode(currentIndex + 1, true));
  document.querySelector('#episode-list').addEventListener('click', (event) => {
    const button = event.target.closest('[data-episode-index]');
    if (button) selectEpisode(Number(button.dataset.episodeIndex), true);
  });
}

function renderEpisodeList() {
  const list = document.querySelector('#episode-list');
  list.innerHTML = episodes.map((episode, index) => `<button class="episode-item ${index === currentIndex ? 'is-active' : ''}" data-episode-index="${index}" type="button" aria-current="${index === currentIndex ? 'true' : 'false'}"><span class="episode-item__number">${episode.episodeNumber}</span><span><b>${escapeHtml(episode.title || `Tập ${episode.episodeNumber}`)}</b><small>${episode.duration ? `${episode.duration} phút` : 'Thời lượng đang cập nhật'}</small></span><span class="episode-item__play">▶</span></button>`).join('');
}

function historyPosition(episode) {
  const entry = history.find((item) => String(item.movieId) === String(movieId) && String(item.episodeId) === String(episode.id));
  return Number(entry?.lastPosition || 0);
}

function selectEpisode(index, updateUrl = false) {
  if (index < 0 || index >= episodes.length) return;
  currentIndex = index;
  const episode = episodes[index];
  document.querySelector('#current-episode-title').textContent = `${movie.title} — ${episodeLabel(episode)}`;
  document.querySelector('#current-episode-meta').textContent = episode.duration ? `${episode.duration} phút` : 'Thời lượng đang cập nhật';
  document.querySelector('#current-episode-breadcrumb').textContent = episodeLabel(episode);
  document.querySelector('#previous-episode').disabled = index === 0;
  document.querySelector('#next-episode').disabled = index === episodes.length - 1;
  renderEpisodeList();
  player.load(episode.videoUrl, historyPosition(episode));

  if (updateUrl) {
    const url = new URL(window.location.href);
    url.searchParams.set('episode', episode.id);
    window.history.replaceState({}, '', url);
  }
}

function saveCurrentProgress(lastPosition) {
  if (!watchApi.isAuthenticated()) return;
  const episode = episodes[currentIndex];
  watchApi.saveProgress({ movieId: Number(movieId), episodeId: episode.id, lastPosition }).catch(() => {});
}

async function loadWatchPage() {
  if (!movieId) {
    renderError('Đường dẫn thiếu mã phim.');
    return;
  }

  try {
    const [movieResponse, episodeResponse] = await Promise.all([
      movieApi.getMovies({ page: 0, size: 100 }),
      movieApi.getEpisodes(movieId)
    ]);
    movie = movieResponse.content?.find((item) => String(item.id) === String(movieId));
    episodes = Array.isArray(episodeResponse) ? episodeResponse : [];
  } catch (error) {
    renderError(error.message || 'Không kết nối được với máy chủ.');
    return;
  }

  if (!movie) {
    renderError('Không tìm thấy thông tin phim.');
    return;
  }
  if (!episodes.length) {
    renderError('Phim này chưa có tập hoặc nguồn video. Quản trị viên cần thêm tập phim trước.');
    return;
  }

  if (watchApi.isAuthenticated()) {
    try { history = await watchApi.getHistory(); } catch { history = []; }
  }

  currentIndex = Math.max(0, episodes.findIndex((episode) => String(episode.id) === String(requestedEpisodeId)));
  renderShell();
  selectEpisode(currentIndex);
  document.title = `${movie.title} | Xem phim | CineHub`;
}

window.addEventListener('beforeunload', () => player?.destroy());
loadWatchPage();
