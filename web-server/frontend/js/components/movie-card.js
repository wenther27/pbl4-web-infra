const fallbackPoster = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 600"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="#eadedb"/><stop offset="1" stop-color="#b1212d"/></linearGradient></defs><rect width="400" height="600" fill="url(#g)"/><text x="200" y="290" fill="white" font-family="sans-serif" font-size="36" text-anchor="middle">CineHub</text><text x="200" y="330" fill="white" opacity=".75" font-family="sans-serif" font-size="16" text-anchor="middle">Movie poster</text></svg>')}`;

export function escapeHtml(value = '') {
  return String(value).replace(/[&<>'"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]);
}

export function normalizeMovie(movie) {
  return {
    ...movie,
    title: movie.title || 'Chưa có tiêu đề',
    description: movie.description || 'Thông tin phim đang được cập nhật.',
    posterUrl: movie.posterUrl || fallbackPoster,
    backdropUrl: movie.backdropUrl || movie.posterUrl || fallbackPoster,
    rating: Number(movie.rating || 0),
    genres: Array.isArray(movie.genres) ? movie.genres : []
  };
}

export function movieCard(movie, root = '..') {
  const item = normalizeMovie(movie);
  const year = item.releaseDate?.slice(0, 4) || 'Mới';
  const genres = item.genres.map((genre) => genre.name).join(' · ') || 'Điện ảnh';
  const typeLabel = { movie: 'Phim lẻ', series: 'Phim bộ', anime: 'Anime' }[item.movieType] || 'Tuyển chọn';
  return `<article class="movie-card">
    <a class="movie-card__poster" href="${root}/pages/movie-detail.html?id=${encodeURIComponent(item.id)}" aria-label="Xem ${escapeHtml(item.title)}">
      <span class="movie-card__badge">${typeLabel}</span><span class="movie-card__rating">★ ${item.rating.toFixed(1)}</span>
      <img src="${escapeHtml(item.posterUrl)}" alt="Poster ${escapeHtml(item.title)}" loading="lazy" onerror="this.onerror=null;this.src='${fallbackPoster}'">
    </a>
    <div class="movie-card__body"><div class="movie-card__meta"><span>${year}</span><span>${item.duration || '—'} phút</span></div><h3><a href="${root}/pages/movie-detail.html?id=${encodeURIComponent(item.id)}">${escapeHtml(item.title)}</a></h3><div class="movie-card__genres">${escapeHtml(genres)}</div></div>
  </article>`;
}
