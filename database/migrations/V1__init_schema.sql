-- ============================================================
-- CineHub - Khởi tạo database
-- Dùng cho PBL4. File này tự chạy khi mount vào
-- /docker-entrypoint-initdb.d/ của container MySQL.
-- ============================================================

CREATE DATABASE IF NOT EXISTS cinehub_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE cinehub_db;

-- ============================================================
-- 1. users
-- ============================================================
CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    avatar_url  VARCHAR(500),
    role        ENUM('USER', 'ADMIN') DEFAULT 'USER',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. movies
-- ============================================================
CREATE TABLE movies (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    poster_url    VARCHAR(500),
    backdrop_url  VARCHAR(500),
    release_date  DATE,
    duration      INT COMMENT 'Độ dài phim tính bằng phút',
    rating        DECIMAL(3,1) DEFAULT 0.0,
    is_trending   BOOLEAN DEFAULT FALSE,
    is_top_rated  BOOLEAN DEFAULT FALSE,
    movie_type    ENUM('movie', 'series', 'anime') DEFAULT 'movie',
    status        ENUM('released', 'upcoming', 'ongoing') DEFAULT 'released',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_movies_trending   (is_trending),
    INDEX idx_movies_top_rated  (is_top_rated),
    INDEX idx_movies_type       (movie_type),
    INDEX idx_movies_rating     (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. genres
-- ============================================================
CREATE TABLE genres (
    id    INT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(50) NOT NULL UNIQUE,
    slug  VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. movie_genres (n-n giữa movies và genres)
-- ============================================================
CREATE TABLE movie_genres (
    movie_id  BIGINT,
    genre_id  INT,
    PRIMARY KEY (movie_id, genre_id),
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 5. episodes
-- ============================================================
CREATE TABLE episodes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id        BIGINT,
    title           VARCHAR(255),
    episode_number  INT NOT NULL,
    video_url       VARCHAR(500) NOT NULL,
    duration        INT,
    thumbnail_url   VARCHAR(500),
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    UNIQUE KEY uq_episode_per_movie (movie_id, episode_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6. reviews
-- ============================================================
CREATE TABLE reviews (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    movie_id    BIGINT,
    content     TEXT,
    rating      INT CHECK (rating >= 1 AND rating <= 10),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    INDEX idx_reviews_movie_created (movie_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. favorites
-- ============================================================
CREATE TABLE favorites (
    user_id   BIGINT,
    movie_id  BIGINT,
    added_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, movie_id),
    FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 8. watch_history
-- ============================================================
CREATE TABLE watch_history (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT,
    movie_id       BIGINT,
    episode_id     BIGINT NULL,
    last_position  INT DEFAULT 0 COMMENT 'Vị trí đang xem dở, tính bằng giây',
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    FOREIGN KEY (movie_id)   REFERENCES movies(id)   ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    INDEX idx_history_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- DỮ LIỆU MẪU
-- ============================================================

-- Users (mật khẩu mẫu: 123456, đã mã hoá bằng BCrypt)
INSERT INTO users (username, email, password, role) VALUES
('admin',   'admin@cinehub.com',   '$2a$10$9B5PQqflEoApXa0cnvXyS.st2xy38rH0DlXMYJLfh.bPyDMsxxJOS', 'ADMIN'),
('quanzn',  'quan@cinehub.com',    '$2a$10$9B5PQqflEoApXa0cnvXyS.st2xy38rH0DlXMYJLfh.bPyDMsxxJOS', 'USER'),
('khanh',   'khanh@cinehub.com',   '$2a$10$9B5PQqflEoApXa0cnvXyS.st2xy38rH0DlXMYJLfh.bPyDMsxxJOS', 'USER'),
('linhnt',  'linh@cinehub.com',    '$2a$10$9B5PQqflEoApXa0cnvXyS.st2xy38rH0DlXMYJLfh.bPyDMsxxJOS', 'USER'),
('hoang',   'hoang@cinehub.com',   '$2a$10$9B5PQqflEoApXa0cnvXyS.st2xy38rH0DlXMYJLfh.bPyDMsxxJOS', 'USER'),
('mynguyen','my@cinehub.com',      '$2a$10$9B5PQqflEoApXa0cnvXyS.st2xy38rH0DlXMYJLfh.bPyDMsxxJOS', 'USER');

-- Genres
INSERT INTO genres (name, slug) VALUES
('Hành động',   'hanh-dong'),
('Viễn tưởng',  'vien-tuong'),
('Hoạt hình',   'hoat-hinh'),
('Kinh dị',     'kinh-di'),
('Hài hước',    'hai-huoc'),
('Tình cảm',    'tinh-cam'),
('Tâm lý',      'tam-ly'),
('Phiêu lưu',   'phieu-luu'),
('Tội phạm',    'toi-pham'),
('Chiến tranh', 'chien-tranh'),
('Cổ trang',    'co-trang'),
('Tài liệu',    'tai-lieu');

-- Movies
INSERT INTO movies (title, description, poster_url, release_date, duration, rating, is_trending, is_top_rated, movie_type, status) VALUES
('Inception',              'Kẻ đánh cắp giấc mơ',                        'https://image.tmdb.org/t/p/w500/inception.jpg',   '2010-07-16', 148, 8.8, TRUE,  TRUE,  'movie',  'released'),
('Interstellar',           'Hành trình xuyên không gian tìm nhà mới',    'https://image.tmdb.org/t/p/w500/interstellar.jpg','2014-11-07', 169, 8.6, TRUE,  TRUE,  'movie',  'released'),
('The Dark Knight',        'Batman đối đầu Joker',                       'https://image.tmdb.org/t/p/w500/tdk.jpg',         '2008-07-18', 152, 9.0, FALSE, TRUE,  'movie',  'released'),
('Parasite',               'Hai gia đình, hai tầng lớp',                 'https://image.tmdb.org/t/p/w500/parasite.jpg',    '2019-05-30', 132, 8.5, FALSE, TRUE,  'movie',  'released'),
('Spirited Away',          'Cô bé lạc vào thế giới linh hồn',            'https://image.tmdb.org/t/p/w500/spirited.jpg',    '2001-07-20', 125, 8.6, FALSE, TRUE,  'movie',  'released'),
('Your Name',              'Hai người trẻ hoán đổi thân xác',            'https://image.tmdb.org/t/p/w500/yourname.jpg',    '2016-08-26', 106, 8.4, TRUE,  FALSE, 'movie',  'released'),
('Jujutsu Kaisen',         'Chú Thuật Hồi Chiến',                        'https://image.tmdb.org/t/p/w500/jjk.jpg',         '2020-10-03',  24, 9.0, TRUE,  TRUE,  'anime',  'ongoing'),
('Attack on Titan',        'Nhân loại sau bức tường thành',              'https://image.tmdb.org/t/p/w500/aot.jpg',         '2013-04-07',  24, 9.1, TRUE,  TRUE,  'anime',  'released'),
('Demon Slayer',           'Thanh Gươm Diệt Quỷ',                        'https://image.tmdb.org/t/p/w500/ds.jpg',          '2019-04-06',  24, 8.7, TRUE,  FALSE, 'anime',  'ongoing'),
('One Piece',              'Hải tặc đi tìm kho báu',                     'https://image.tmdb.org/t/p/w500/onepiece.jpg',    '1999-10-20',  24, 8.9, FALSE, TRUE,  'anime',  'ongoing'),
('Breaking Bad',           'Thầy giáo hoá học rẽ trái',                  'https://image.tmdb.org/t/p/w500/bb.jpg',          '2008-01-20',  49, 9.5, FALSE, TRUE,  'series', 'released'),
('Stranger Things',        'Thị trấn nhỏ và thế giới lộn ngược',         'https://image.tmdb.org/t/p/w500/st.jpg',          '2016-07-15',  51, 8.7, TRUE,  FALSE, 'series', 'ongoing'),
('Game of Thrones',        'Cuộc chiến giành ngai sắt',                  'https://image.tmdb.org/t/p/w500/got.jpg',         '2011-04-17',  57, 9.2, FALSE, TRUE,  'series', 'released'),
('The Last of Us',         'Hành trình sinh tồn sau đại dịch',           'https://image.tmdb.org/t/p/w500/tlou.jpg',        '2023-01-15',  55, 8.7, TRUE,  FALSE, 'series', 'ongoing'),
('Mắt Biếc',               'Chuyện tình Ngạn và Hà Lan',                 'https://image.tmdb.org/t/p/w500/matbiec.jpg',     '2019-12-20', 117, 7.9, FALSE, FALSE, 'movie',  'released'),
('Bố Già',                 'Chuyện gia đình xóm lao động',               'https://image.tmdb.org/t/p/w500/bogia.jpg',       '2021-03-12', 128, 7.5, FALSE, FALSE, 'movie',  'released'),
('Nhà Bà Nữ',              'Mâu thuẫn mẹ con trong gia đình Việt',       'https://image.tmdb.org/t/p/w500/nhabanu.jpg',     '2023-01-22', 112, 7.2, FALSE, FALSE, 'movie',  'released'),
('Dune',                   'Cuộc chiến trên hành tinh cát',              'https://image.tmdb.org/t/p/w500/dune.jpg',        '2021-10-22', 155, 8.0, TRUE,  FALSE, 'movie',  'released'),
('Oppenheimer',            'Cha đẻ bom nguyên tử',                       'https://image.tmdb.org/t/p/w500/oppen.jpg',       '2023-07-21', 180, 8.3, FALSE, TRUE,  'movie',  'released'),
('Avengers: Endgame',      'Trận chiến cuối cùng',                       'https://image.tmdb.org/t/p/w500/endgame.jpg',     '2019-04-26', 181, 8.4, FALSE, TRUE,  'movie',  'released'),
('Chainsaw Man',           'Denji và quỷ cưa máy',                       'https://image.tmdb.org/t/p/w500/csm.jpg',         '2022-10-12',  24, 8.6, TRUE,  FALSE, 'anime',  'ongoing'),
('Vinland Saga',           'Hành trình báo thù của Thorfinn',            'https://image.tmdb.org/t/p/w500/vinland.jpg',     '2019-07-08',  24, 8.8, FALSE, TRUE,  'anime',  'ongoing'),
('Squid Game',             'Trò chơi sinh tử',                           'https://image.tmdb.org/t/p/w500/squid.jpg',       '2021-09-17',  55, 8.0, TRUE,  FALSE, 'series', 'ongoing'),
('Chernobyl',              'Thảm hoạ hạt nhân 1986',                     'https://image.tmdb.org/t/p/w500/chernobyl.jpg',   '2019-05-06',  65, 9.4, FALSE, TRUE,  'series', 'released');

-- Gán thể loại cho phim
INSERT INTO movie_genres (movie_id, genre_id) VALUES
(1,1),(1,2),(1,7),
(2,2),(2,8),(2,7),
(3,1),(3,9),(3,7),
(4,7),(4,9),
(5,3),(5,8),
(6,3),(6,6),
(7,1),(7,3),
(8,1),(8,3),(8,10),
(9,1),(9,3),
(10,1),(10,3),(10,8),
(11,9),(11,7),
(12,2),(12,4),
(13,8),(13,11),
(14,1),(14,4),
(15,6),(15,7),
(16,7),(16,5),
(17,7),(17,5),
(18,2),(18,8),
(19,7),(19,10),
(20,1),(20,2),(20,8),
(21,1),(21,3),
(22,1),(22,3),(22,10),
(23,1),(23,7),
(24,7),(24,12);

-- Episodes cho các phim dạng series và anime
INSERT INTO episodes (movie_id, title, episode_number, video_url, duration) VALUES
(7,  'Ryomen Sukuna',        1, 'https://cdn.cinehub.com/jjk-01.mp4', 24),
(7,  'Vì tôi ở đây',         2, 'https://cdn.cinehub.com/jjk-02.mp4', 24),
(7,  'Cô gái thép',          3, 'https://cdn.cinehub.com/jjk-03.mp4', 24),
(7,  'Sinh ra từ lời nguyền',4, 'https://cdn.cinehub.com/jjk-04.mp4', 24),
(8,  'Gửi em, 2000 năm sau', 1, 'https://cdn.cinehub.com/aot-01.mp4', 24),
(8,  'Ngày định mệnh',       2, 'https://cdn.cinehub.com/aot-02.mp4', 24),
(8,  'Ánh sáng le lói',      3, 'https://cdn.cinehub.com/aot-03.mp4', 24),
(9,  'Sự tàn nhẫn',          1, 'https://cdn.cinehub.com/ds-01.mp4',  24),
(9,  'Người dẫn đường',      2, 'https://cdn.cinehub.com/ds-02.mp4',  24),
(11, 'Pilot',                1, 'https://cdn.cinehub.com/bb-01.mp4',  49),
(11, 'Cat in the Bag',       2, 'https://cdn.cinehub.com/bb-02.mp4',  48),
(12, 'Chapter One',          1, 'https://cdn.cinehub.com/st-01.mp4',  51),
(12, 'Chapter Two',          2, 'https://cdn.cinehub.com/st-02.mp4',  50),
(13, 'Winter Is Coming',     1, 'https://cdn.cinehub.com/got-01.mp4', 62),
(14, 'When You are Lost',    1, 'https://cdn.cinehub.com/tlou-01.mp4',81),
(21, 'Chó và cưa máy',       1, 'https://cdn.cinehub.com/csm-01.mp4', 24),
(22, 'Somewhere Not Here',   1, 'https://cdn.cinehub.com/vin-01.mp4', 24),
(23, 'Đèn đỏ đèn xanh',      1, 'https://cdn.cinehub.com/squid-01.mp4',60),
(24, '1:23:45',              1, 'https://cdn.cinehub.com/cher-01.mp4', 65);

-- Reviews
INSERT INTO reviews (user_id, movie_id, content, rating) VALUES
(2, 1,  'Xem ba lần vẫn chưa hiểu hết cái kết.',            9),
(3, 1,  'Nhạc phim quá đỉnh.',                              10),
(2, 3,  'Joker của Heath Ledger không ai thay được.',       10),
(4, 4,  'Kịch bản chặt, cảnh cuối ám ảnh.',                 9),
(5, 7,  'Animation mượt, đánh nhau mãn nhãn.',              9),
(3, 8,  'Càng về sau càng nặng, nhưng đáng xem.',           10),
(6, 11, 'Từng tập đều có lý do tồn tại.',                   10),
(2, 15, 'Phim Việt hiếm hoi xem xong còn nghĩ.',            8),
(4, 18, 'Hình ảnh và âm thanh xứng đáng ra rạp.',           8),
(5, 24, 'Phim tài liệu hoá nhưng hồi hộp hơn phim hành động.', 10),
(6, 19, 'Hơi dài nhưng diễn xuất bù lại.',                  8),
(3, 23, 'Ý tưởng tốt, vài tập giữa hơi lê thê.',            7);

-- Favorites
INSERT INTO favorites (user_id, movie_id) VALUES
(2,1),(2,7),(2,11),(2,15),
(3,1),(3,8),(3,3),
(4,4),(4,19),(4,24),
(5,7),(5,9),(5,21),
(6,11),(6,13);

-- Watch history
INSERT INTO watch_history (user_id, movie_id, episode_id, last_position) VALUES
(2, 7,  2,    820),
(2, 1,  NULL, 3600),
(3, 8,  6,    450),
(4, 11, 10,   1200),
(5, 9,  8,    300),
(6, 13, 14,   2400);
