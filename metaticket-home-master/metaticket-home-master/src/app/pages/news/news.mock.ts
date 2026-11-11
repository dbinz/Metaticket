import { of, throwError, type Observable } from 'rxjs';
import { NEWS_ALL_CATEGORY_CODE, NEWS_CATEGORY_PALETTE } from 'src/app/lib/constants/news.constants';
import {
  GetNewsListRequest,
  GetNewsListResponse,
  NewsCategoryItem,
  NewsDetailResponse,
  NewsItem,
} from 'src/app/lib/entities/news.response';
import { appImages } from 'src/app/lib/theme';
import { routeName } from 'src/app/lib/constants';

/** Visual tokens for a listing section — not part of the CMS payload. */
export interface NewsCategorySectionUi {
  code: string;
  name: string;
  slug: string;
  shortLabel: string;
  bar: string;
  badgeBg: string;
  badgeFg: string;
  expand: boolean;
}

export const MOCK_NEWS_CATEGORY_UI: NewsCategorySectionUi[] = [
  {
    code: 'show-su-kien',
    name: 'Show & Sự kiện',
    slug: 'show-su-kien',
    shortLabel: 'Show',
    bar: '#3B82F6',
    badgeBg: '#DBEAFE',
    badgeFg: '#1E3A8A',
    expand: true,
  },
  {
    code: 'nghe-si-fandom',
    name: 'Nghệ sĩ & Fandom',
    slug: 'nghe-si-fandom',
    shortLabel: 'Nghệ sĩ',
    bar: '#DB2777',
    badgeBg: '#FCE7F0',
    badgeFg: '#9D174D',
    expand: false,
  },
  {
    code: 'merch-san-pham',
    name: 'Merch & Sản phẩm',
    slug: 'merch-san-pham',
    shortLabel: 'Merch',
    bar: '#7C3AED',
    badgeBg: '#EDE9FE',
    badgeFg: '#5B21B6',
    expand: false,
  },
  {
    code: 'van-hoa-am-thuc-du-lich',
    name: 'Văn hoá – Ẩm thực & Du lịch',
    slug: 'van-hoa-am-thuc-du-lich',
    shortLabel: 'Văn hoá',
    bar: '#D97706',
    badgeBg: '#FEF3C7',
    badgeFg: '#92400E',
    expand: false,
  },
  {
    code: 'cam-nang-trai-nghiem-ve',
    name: 'Cẩm nang & Trải nghiệm vé',
    slug: 'cam-nang-trai-nghiem-ve',
    shortLabel: 'Cẩm nang',
    bar: '#DC5A2E',
    badgeBg: '#FDE4D8',
    badgeFg: '#7C2D12',
    expand: false,
  },
];

/** Visual tokens for a WP category — reuse mock colors when the slug matches. */
export function newsCategorySectionFromApi(cat: NewsCategoryItem, index: number): NewsCategorySectionUi {
  const known = MOCK_NEWS_CATEGORY_UI.find((s) => s.slug === cat.slug || s.code === cat.code);
  const palette = NEWS_CATEGORY_PALETTE[index % NEWS_CATEGORY_PALETTE.length];
  return {
    code: cat.code,
    name: cat.name,
    slug: cat.slug || cat.code,
    shortLabel: known?.shortLabel ?? cat.name,
    bar: known?.bar ?? palette.bar,
    badgeBg: known?.badgeBg ?? palette.badgeBg,
    badgeFg: known?.badgeFg ?? palette.badgeFg,
    expand: true,
  };
}

export const MOCK_NEWS_CATEGORIES: NewsCategoryItem[] = MOCK_NEWS_CATEGORY_UI.map((c, i) => ({
  id: String(i + 1),
  name: c.name,
  code: c.code,
  slug: c.slug,
}));

const DATE = '06/08/2026';
const ISO = '2026-08-06T09:00:00+07:00';
const AUTHOR = 'META Ticket';

/** Builds a list card; `show` is omitted for a normal editorial article. */
function article(
  id: string,
  slug: string,
  title: string,
  summary: string,
  cat: NewsCategorySectionUi,
  thumbnail: string,
  show?: NewsItem['show'],
): NewsItem {
  return {
    id,
    slug,
    title,
    summary,
    subdesc: summary,
    thumbnail,
    createdAt: ISO,
    displayDate: DATE,
    categoryId: cat.code,
    categoryCode: cat.code,
    categoryName: cat.name,
    author: AUTHOR,
    show,
  };
}

const showCat = MOCK_NEWS_CATEGORY_UI[0];
const artistCat = MOCK_NEWS_CATEGORY_UI[1];
const merchCat = MOCK_NEWS_CATEGORY_UI[2];
const cultureCat = MOCK_NEWS_CATEGORY_UI[3];
const guideCat = MOCK_NEWS_CATEGORY_UI[4];

export const MOCK_NEWS_ARTICLES: NewsItem[] = [
  article(
    'n1',
    'the-flob-dai-dong-tour-2026',
    'The FLOB | Đại Đồng Tour 2026 chính thức mở bán vé',
    'Bảng giá, sơ đồ chỗ ngồi và cách đặt vé sớm cho tour lớn nhất năm của The FLOB.',
    showCat,
    appImages.bgEventMusic,
    { type: 'event', showName: 'The FLOB | Đại Đồng Tour 2026', url: `/${routeName.event}` },
  ),
  article(
    'n2',
    'liveshow-thanh-tung-legacy-of-love',
    'Liveshow Thanh Tùng – Legacy of Love: bảng giá & sơ đồ',
    'Hướng dẫn chọn hạng vé và vị trí ngồi cho đêm nhạc Legacy of Love.',
    showCat,
    appImages.bgStage,
    { type: 'event', showName: 'Legacy of Love', url: `/${routeName.event}` },
  ),
  article(
    'n3',
    'dem-nhac-ky-uc-ha-noi',
    'Đêm nhạc Ký ức Hà Nội: 4 diva hội ngộ 21/11',
    'Bốn giọng ca hội tụ trên một sân khấu — lịch sử, lineup và không khí đêm diễn.',
    showCat,
    appImages.bgConference,
  ),
  article(
    'n4',
    'lich-mo-ban-ve-tuan-nay',
    'Lịch mở bán vé tuần này: 6 show không nên lỡ',
    'Tổng hợp các suất mở bán trong 7 ngày tới trên META Ticket.',
    showCat,
    appImages.bgSport,
  ),
  article(
    'n5',
    'concert-ha-nhi-ecopark',
    'Concert Hà Nhi – Người yêu cũ là tri kỷ tại Ecopark',
    'Thông tin suất diễn, giá vé và mẹo di chuyển tới Ecopark.',
    showCat,
    appImages.bgEventMusic,
    { type: 'event', showName: 'Hà Nhi tại Ecopark', url: `/${routeName.event}` },
  ),
  article(
    'n6',
    'show-thuc-canh-anh-hung-co-lau',
    'Show thực cảnh Anh Hùng Cờ Lau mở thêm suất diễn',
    'Suất mới cho khán giả cuối tuần — đặt sớm để giữ chỗ đẹp.',
    showCat,
    appImages.bgTourist,
    { type: 'event', showName: 'Anh Hùng Cờ Lau', url: `/${routeName.event}` },
  ),
  article(
    'n7',
    'ho-so-tung-duong',
    'Hồ sơ Tùng Dương: từ Chạy Trốn đến classical crossover',
    'Hành trình âm nhạc và những đêm diễn đáng nhớ.',
    artistCat,
    appImages.bgStage,
  ),
  article(
    'n8',
    'vicky-nhung-the-voice',
    'Vicky Nhung – hành trình từ The Voice đến sân khấu riêng',
    'Từ cuộc thi đến liveshow: những cột mốc của Vicky Nhung.',
    artistCat,
    appImages.bgConference,
  ),
  article(
    'n9',
    'van-hoa-du-concert-fan-viet',
    "Văn hoá 'đu concert' của fan Việt: đẹp và văn minh",
    'Cách cộng đồng concert Việt xây dựng văn hoá xem show tích cực.',
    artistCat,
    appImages.bgChild,
  ),
  article(
    'n10',
    'bo-suu-tap-lightstick',
    'Bộ sưu tập lightstick chính hãng cho concert',
    'Lightstick theo dự án — chất liệu, giá và cách nhận hàng.',
    merchCat,
    appImages.imgMetaTicketThumbnail,
    { type: 'product', showName: 'Lightstick concert', url: `/${routeName.product}` },
  ),
  article(
    'n11',
    'merch-the-flob-tour',
    'Merch The FLOB Tour: có gì, giá bao nhiêu',
    'Áo, tote, sổ — bộ sưu tập theo tour Đại Đồng.',
    merchCat,
    appImages.imgMacbookPhone,
    { type: 'product', showName: 'Merch The FLOB Tour', url: `/${routeName.product}` },
  ),
  article(
    'n12',
    'huong-dan-dat-san-pham-meta-ticket',
    'Hướng dẫn đặt sản phẩm trên META Ticket',
    'Từ giỏ hàng đến thanh toán: các bước mua merch an toàn.',
    merchCat,
    appImages.bgHome,
  ),
  article(
    'n13',
    'giai-ma-show-thuc-canh',
    'Giải mã show thực cảnh: vì sao nên xem một lần',
    'Sân khấu ngoài trời, ánh sáng và câu chuyện dân gian trên quy mô lớn.',
    cultureCat,
    appImages.bgTourist,
  ),
  article(
    'n14',
    'di-da-lat-xem-liveshow-quoc-thien',
    'Đi Đà Lạt xem liveshow Quốc Thiên: ăn gì, chơi gì?',
    'Gợi ý lịch trình 2 ngày quanh đêm nhạc tại Đà Lạt.',
    cultureCat,
    appImages.bgSport,
  ),
  article(
    'n15',
    'cam-nang-ninh-binh-co-lau',
    'Cẩm nang 2N1Đ Ninh Bình + show Anh Hùng Cờ Lau',
    'Kết hợp du lịch và show thực cảnh trong một chuyến đi.',
    cultureCat,
    appImages.bgTourist,
  ),
  article(
    'n16',
    'cach-mua-ve-meta-ticket-a-z',
    'Cách mua vé trên META Ticket từ A–Z',
    'Tài khoản, chọn ghế, thanh toán và nhận vé điện tử.',
    guideCat,
    appImages.bgHome,
  ),
  article(
    'n17',
    'so-do-cho-ngoi-trung-tam-hoi-nghi-quoc-gia',
    'Sơ đồ chỗ ngồi Trung tâm Hội nghị Quốc gia',
    'Đọc vị trí ghế trước khi đặt để chọn view phù hợp.',
    guideCat,
    appImages.bgConference,
  ),
  article(
    'n18',
    'nhan-biet-ve-gia-mua-ve-an-toan',
    'Nhận biết vé giả và mua vé an toàn',
    'Chỉ mua từ kênh chính thức — dấu hiệu vé giả thường gặp.',
    guideCat,
    appImages.bgStage,
  ),
  article(
    'n19',
    'concert-tuan-hung-ha-noi',
    'Concert Tuấn Hưng tại Hà Nội: suất diễn và giá vé',
    'Lịch mở bán, hạng ghế và lưu ý di chuyển đêm diễn.',
    showCat,
    appImages.bgEventMusic,
    { type: 'event', showName: 'Concert Tuấn Hưng Hà Nội', url: `/${routeName.event}` },
  ),
  article(
    'n20',
    'liveshow-my-tam-tang-thuong',
    'Liveshow Mỹ Tâm – Tầng Thương: những điều cần biết trước giờ G',
    'Dress code, giờ mở cửa và cách nhận vé điện tử.',
    showCat,
    appImages.bgStage,
    { type: 'event', showName: 'Tầng Thương', url: `/${routeName.event}` },
  ),
  article(
    'n21',
    'dem-nhac-jazz-sai-gon',
    'Đêm nhạc jazz Sài Gòn: lineup khách mời quốc tế',
    'Ba đêm liên tiếp tại nhà hát lớn — chọn suất phù hợp.',
    showCat,
    appImages.bgConference,
  ),
  article(
    'n22',
    'festival-am-nhac-da-lat',
    'Festival âm nhạc Đà Lạt 2026 công bố lineup',
    'Sân khấu ngoài trời, camping và combo vé + merch.',
    showCat,
    appImages.bgTourist,
    { type: 'event', showName: 'Festival Đà Lạt 2026', url: `/${routeName.event}` },
  ),
  article(
    'n23',
    'show-thieu-nhi-he-2026',
    'Show thiếu nhi hè 2026: lịch diễn cuối tuần',
    'Gợi ý suất sáng và chiều cho gia đình có trẻ nhỏ.',
    showCat,
    appImages.bgChild,
    { type: 'event', showName: 'Show thiếu nhi hè 2026', url: `/${routeName.event}` },
  ),
  article(
    'n24',
    'mo-ban-ve-premier-league-fan-fest',
    'Mở bán vé Premier League Fan Fest tại Việt Nam',
    'Cách chọn khu vực xem chung kết trên màn hình lớn.',
    showCat,
    appImages.bgSport,
    { type: 'event', showName: 'PL Fan Fest', url: `/${routeName.event}` },
  ),
];

export const MOCK_NEWS_FEATURED = MOCK_NEWS_ARTICLES[0];

export const MOCK_NEWS_MOST_VIEWED: NewsItem[] = [
  MOCK_NEWS_ARTICLES[15],
  MOCK_NEWS_ARTICLES[16],
  MOCK_NEWS_ARTICLES[2],
  MOCK_NEWS_ARTICLES[17],
  MOCK_NEWS_ARTICLES[12],
];

/** Short HTML body for mock detail pages. */
function htmlBody(title: string, extra: string): string {
  return `<p>${extra}</p><h2>Chi tiết</h2><p>${title} — nội dung mẫu để dựng giao diện. Khi CMS sẵn sàng, HTML sẽ lấy từ WordPress.</p><p>META Ticket kết nối khán giả với sân khấu, merch và trải nghiệm văn hoá.</p>`;
}

function toDetail(item: NewsItem, extras: NewsItem[]): NewsDetailResponse {
  return {
    ...item,
    author: item.author ?? AUTHOR,
    readTime: '5 phút đọc',
    content: htmlBody(item.title, item.summary),
    relatedNews: extras.filter((x) => x.id !== item.id).slice(0, 4),
    relatedEventIds: [],
  };
}

const DETAILS: Record<string, NewsDetailResponse> = Object.fromEntries(
  MOCK_NEWS_ARTICLES.map((item) => [item.slug, toDetail(item, MOCK_NEWS_ARTICLES)]),
);

/** Navbar + listing category chips. */
export function getMockNewsCategories(): Observable<NewsCategoryItem[]> {
  return of(MOCK_NEWS_CATEGORIES);
}

/** Offset list used by listing, related rails, and load-more. */
export function getMockNewsList(request: GetNewsListRequest): Observable<GetNewsListResponse> {
  const code = request.categoryCode;
  const filtered =
    !code || code === NEWS_ALL_CATEGORY_CODE
      ? MOCK_NEWS_ARTICLES
      : MOCK_NEWS_ARTICLES.filter((item) => item.categoryCode === code);
  const offset = request.offset ?? 0;
  const size = request.size ?? filtered.length;
  const content = filtered.slice(offset, offset + size);
  return of({
    content,
    totalElements: filtered.length,
    offset,
    size,
    last: offset + content.length >= filtered.length,
    empty: filtered.length === 0,
  });
}

/** Detail by slug — missing slug fails so the page can route to not-found. */
export function getMockNewsBySlug(slug: string): Observable<NewsDetailResponse> {
  const detail = DETAILS[slug];
  if (!detail) {
    return throwError(() => new Error(`Unknown mock news slug: ${slug}`));
  }
  return of(detail);
}
