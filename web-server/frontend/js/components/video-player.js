function safeVideoUrl(value) {
  try {
    const url = new URL(value, window.location.origin);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
  } catch {
    return '';
  }
}

export function createVideoPlayer({ video, message, onProgress, onEnded }) {
  let lastSavedSecond = -1;

  function setMessage(text = '') {
    message.textContent = text;
    message.hidden = !text;
  }

  function saveProgress(force = false) {
    const second = Math.floor(video.currentTime || 0);
    if (!force && (second < 1 || Math.abs(second - lastSavedSecond) < 15)) return;
    lastSavedSecond = second;
    onProgress?.(second);
  }

  video.addEventListener('timeupdate', () => saveProgress());
  video.addEventListener('pause', () => saveProgress(true));
  video.addEventListener('ended', () => {
    saveProgress(true);
    onEnded?.();
  });
  video.addEventListener('error', () => {
    setMessage('Không thể phát nguồn video này. Hãy kiểm tra URL video hoặc quyền truy cập của máy chủ chứa video.');
  });
  video.addEventListener('playing', () => setMessage());

  return {
    load(source, startAt = 0) {
      const url = safeVideoUrl(source);
      video.pause();
      video.removeAttribute('src');
      video.load();
      lastSavedSecond = -1;

      if (!url) {
        setMessage('Tập phim chưa có URL video hợp lệ.');
        return;
      }

      setMessage('Nhấn nút phát để bắt đầu xem.');
      video.src = url;
      video.load();
      if (startAt > 0) {
        video.addEventListener('loadedmetadata', () => {
          if (Number.isFinite(video.duration)) {
            video.currentTime = Math.min(startAt, Math.max(0, video.duration - 2));
          }
        }, { once: true });
      }
    },
    destroy() {
      video.pause();
      video.removeAttribute('src');
      video.load();
    }
  };
}
