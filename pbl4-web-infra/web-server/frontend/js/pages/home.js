import { movieApi } from '../api/client.js';
import { mountLayout } from '../components/layout.js';
import { escapeHtml, movieCard, normalizeMovie } from '../components/movie-card.js';
import { fallbackGenres, fallbackMovies } from '../data/fallback-movies.js';

mountLayout();

async function loadHome() {
  const grid = document.querySelector('#trending-grid');
  let movies = fallbackMovies;
  let genres = fallbackGenres;
  let apiUnavailable = false;

  try {
    const [movieResponse, genreResponse] = await Promise.all([
      movieApi.getMovies({ page: 0, size: 10, sortBy: 'rating', direction: 'desc' }),
      movieApi.getGenres()
    ]);
    if (movieResponse.content?.length) movies = movieResponse.content.map(normalizeMovie);
    if (genreResponse?.length) genres = genreResponse;
  } catch (error) {
    apiUnavailable = true;
    console.info('Backend chưa sẵn sàng, đang dùng dữ liệu giao diện mẫu.', error);
  }

  const heroMovie = movies[0];
  document.querySelector('#hero-title').textContent = heroMovie.title;
  document.querySelector('#hero-description').textContent = heroMovie.description;
  document.querySelector('#hero-watch').href = `./pages/movie-detail.html?id=${encodeURIComponent(heroMovie.id)}`;
  document.querySelector('#hero').style.setProperty('--hero-image', `url("${heroMovie.backdropUrl}")`);

  grid.innerHTML = `${apiUnavailable ? '<p class="error-banner">Backend chưa chạy — đang hiển thị dữ liệu mẫu của giao diện.</p>' : ''}${movies.slice(0, 10).map((movie) => movieCard(movie, '.')).join('')}`;
  document.querySelector('#genre-list').innerHTML = genres.slice(0, 8).map((genre, index) => `<a class="genre-chip ${index === 0 ? 'is-active' : ''}" href="./pages/catalog.html?genre=${encodeURIComponent(genre.slug || '')}">${escapeHtml(genre.name)}</a>`).join('');
  document.querySelector('#genre-feature').innerHTML = movies.slice(0, 2).map((movie) => `<a class="feature-tile" href="./pages/movie-detail.html?id=${encodeURIComponent(movie.id)}" style="background-image:url('${movie.backdropUrl}')"><div><p class="eyebrow">Tuyển tập đặc biệt</p><h3>${escapeHtml(movie.title)}</h3><p>${escapeHtml(movie.description)}</p></div></a>`).join('');
}

loadHome();
