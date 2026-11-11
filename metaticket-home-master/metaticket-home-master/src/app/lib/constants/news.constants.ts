/** Segment URL danh mục: /news/category/:slug */
export const NEWS_CATEGORY_PATH = 'category';

/** Số bài mỗi trang trên /news/category/:slug */
export const NEWS_CATEGORY_PAGE_SIZE = 6;

/** Dùng mock CMS chỉ khi debug layout; production/local đều gọi WordPress qua BFF. */
export const isNewsUseMock = false;

/** Số tin load lần đầu trên trang danh sách */
export const NEWS_INITIAL_PAGE_SIZE = 9;

/** Số tin load mỗi lần bấm "Xem thêm" */
export const NEWS_LOAD_MORE_PAGE_SIZE = 6;

/** Giá trị categoryCode khi xem tất cả danh mục (client-side) */
export const NEWS_ALL_CATEGORY_CODE = 'all';

/** Palette cycled for WP categories that are not in the mock list. */
export const NEWS_CATEGORY_PALETTE: Array<{ bar: string; badgeBg: string; badgeFg: string }> = [
  { bar: '#3B82F6', badgeBg: '#DBEAFE', badgeFg: '#1E3A8A' },
  { bar: '#DB2777', badgeBg: '#FCE7F0', badgeFg: '#9D174D' },
  { bar: '#7C3AED', badgeBg: '#EDE9FE', badgeFg: '#5B21B6' },
  { bar: '#D97706', badgeBg: '#FEF3C7', badgeFg: '#92400E' },
  { bar: '#DC5A2E', badgeBg: '#FDE4D8', badgeFg: '#7C2D12' },
];

/** Label on the article CTA — event books a ticket, product opens merch. */
export function newsShowCtaLabel(type: 'event' | 'product'): string {
  return type === 'product' ? 'XEM SẢN PHẨM' : 'ĐẶT VÉ NGAY';
}

/**
 * Full-page article URL. Must be a plain `href` (not `routerLink`) so nginx
 * serves the BFF HTML instead of the Angular news-detail SPA.
 */
export function newsArticleHref(slug: string): string {
  const clean = slug.replace(/\.html$/i, '');
  return `/news/${clean}.html`;
}
