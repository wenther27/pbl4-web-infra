const images = [
  'https://lh3.googleusercontent.com/aida-public/AB6AXuCuX7Zc6paksHUTdK9hKLiv-eTluOh4Q5-cRfhgsAuGYMMZEGRCnk66LLlD3kgKAIvw5SepysdxvJvIGgEmvUPjSJhmypoZ_VjltUrDJ405pddAohETBYY5nswUOaewWopO82OsDE6qk8rX8uNpIVLNEKTbD5Kv2PsDbLWWzox5TMe0Tlb2mEIEtyBC0IUmqueS9oPw8RvYE36KLrh4PDPp_0pLN8G-EScinpEcuChxFa6Ue6OrBT8p',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuBt5gjuku3uv1PSGRK3-_Z5mFP4kCGYcxZ04wNu3jTI8LH0baUfRgtjkE8_38hkqI-50PEQ-kWmszAsBMrXMzxvGhRGMOkejhz4N2cdstsY4Te8vmPGkbEEOAAtc6irXIFPBGlNSAkNUOBokzAR5sXFNJvcOc2ne66FHIARgaIOsl8DREyV3HYIOrHNuPszglPCpEjhQg2r774yZtEsRGM3_1mBVy6aTD34P_iPQaNp1bvvDdnNbH6h',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuAy9KiIvUrQff6fjGZGRmByvrNmd-PfH414ncZYX5N65I5Wa5yf6gm2IcziLCz1KcVv3tevNknylAABAf_6i3n4GTkl66v6ZTRi_WKlOMkXUx09RbdglDeTbFv_-gJ_8r4NV-gOkF9k4FPJ22DZMhUQluRJmpJFZEpp8AUtE36k0e4JwEjJLlMXCuiiCs8WKpCL2aLEFN7M9GQ6-LV7Zv3DuE9hq40DQCkA_xBZQ0cRZRFixF5jJpew',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuAxe8LG1Uekc6lqtYQMPSWOs42r4kCDWHcUhgxUYb5b75S-pf5JX5t0WOaF60NgXnwpqe6KJg2JkM3h5cXf8tlvp28_l__YObBKPTfSVqp3pDWSrgAoQg81CUbdcY8tyexeX5smIN41iaM06CTbnr2GU8psrb5Xa7LE2RFiEZHb78V9FJ74aDwhLqJbW8pyoZw8hl-TVApMOpGSDL_usTzBoGf2HT5n9L_0MBv9qVs-Qb_Rs1PrVDK4',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuACrGZaOUUE4U6jRCUfdqmAX9pKdAWiN2PUyEYf0Hr9OW2Q9O-a1kmtIoEnThwejk69VHG8-xV0ielsOUn7lSnNEY6SyrbslzIHzpxpjZwgSRkGtUwlYwimjM6QPpaCuexhG_H7jMyBmn1bxn0OUb1JSajrXHlDDTQsoJ_e7bZzrqFJjcABtuArlzt_YV4nAQ0L757S3ewodm81QIz0wGbJIdNpfR1WRYqllyyUcEArdQEYxhLGEQdq',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuArO0dUurnimvosYsSJcBsVpvy0G5Ear-LslhMHgjyIumwdzc6zBevMulsV5mmJqhCig1P8IFo9npj7VPynI_UaPD2Us-7zjUZhemHHzIjt8Y9aZ_E109AxnmyxCvjCXTQHZkzb-h4a76qAdvTE9O5yakN0pc25iiFXbr2NfCTqPV01k234MpHebUjRz0oo4R3Gm0qpJ-jO4HvM_dDCItO5bwAq7SEW9fAPLBrzQytEpRBGSYeXwxfg'
];

const source = [
  ['Dune: Hành Tinh Cát', 'Cuộc chiến sinh tồn trên hành tinh cát khắc nghiệt.', 2024, 166, 8.9, 'movie', 'Khoa học viễn tưởng'],
  ['Oppenheimer', 'Câu chuyện về con người đứng sau bước ngoặt của lịch sử.', 2023, 180, 8.8, 'movie', 'Lịch sử'],
  ['Vũ Trụ Vô Tận', 'Một chuyến du hành vượt qua giới hạn của không gian.', 2024, 145, 9.1, 'movie', 'Phiêu lưu'],
  ['Thành Phố Bóng Đêm', 'Bí ẩn được che giấu sau những ánh đèn thành phố.', 2024, 138, 8.6, 'series', 'Bí ẩn'],
  ['Ký Ức Sau Cùng', 'Khi ký ức trở thành manh mối cuối cùng.', 2023, 124, 8.4, 'movie', 'Tâm lý'],
  ['Biên Niên Sử Ánh Sao', 'Những người trẻ bảo vệ thế giới tưởng tượng.', 2025, 24, 8.7, 'anime', 'Hoạt hình']
];

export const fallbackMovies = source.map((movie, index) => ({
  id: index + 1,
  title: movie[0],
  description: movie[1],
  releaseDate: `${movie[2]}-01-01`,
  duration: movie[3],
  rating: movie[4],
  movieType: movie[5],
  status: 'released',
  isTrending: index < 4,
  isTopRated: movie[4] >= 8.8,
  posterUrl: images[index],
  backdropUrl: index === 0 ? 'https://lh3.googleusercontent.com/aida-public/AB6AXuDEFkOz8_x7NjLSEPEXdRNvvsbbOSQcFq4NYuPeNKuof8cN7MhkNbzK6cd-xlxDIpK8gbMaTPoD7bvkq5Sa-xpQmHWrI21DSOKBvD8nKthq6369XVVP8CatfbB3dgWvl4HIPv1ZxfcY5PEZBWUykJjRA64JpI9IOHCumffz2MQak8p7olczpSZ2fbjtjiNMRO-Q9Y46XHLCHYghsXSTR0f8wlGcq74Og35qMuuAYWUq-1QkRRFVAAfk' : images[index],
  genres: [{ id: index + 1, name: movie[6], slug: movie[6].toLowerCase().replaceAll(' ', '-') }]
}));

export const fallbackGenres = [...new Map(fallbackMovies.flatMap((movie) => movie.genres).map((genre) => [genre.slug, genre])).values()];
