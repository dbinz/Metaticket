/**

 * Danh mục tin tức.

 */

export interface NewsCategoryItem {
  id: string;
  name: string;
  code: string;
  slug: string;
}

/** Linked ticket/product on an article — omit for a normal editorial piece. */
export type NewsLinkedShowType = 'event' | 'product';

export interface NewsLinkedShow {
  type: NewsLinkedShowType;
  showName: string;
  url: string;
}

export interface NewsItem {
  id: string;
  thumbnail: string;
  createdAt: string;
  displayDate: string;
  title: string;
  summary: string;
  /** Card excerpt under the title — same role as homepage `ShowcaseArticle.subdesc`. */
  subdesc?: string;
  slug: string;
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  author?: string;
  show?: NewsLinkedShow;
}

/**

 * Mô hình chi tiết bài viết — mở rộng từ NewsItem với nội dung đầy đủ.

 */

export interface NewsDetailResponse extends NewsItem {
  content: string;

  author: string;

  readTime: string;

  relatedNews: NewsItem[];

  relatedEventIds: string[];

  tags?: string[];
}

/** Compact list page used by featured / related rails. */
export interface NewsHighlightPage {
  content: NewsItem[];
  totalElements: number;
}

/**

 * Request lấy danh sách tin — hỗ trợ offset-based pagination.

 */

export interface GetNewsListRequest {
  offset: number;

  size: number;

  categoryCode?: string;

  sort?: string;

  excludeSlug?: string;
}

/**

 * Response danh sách tin có phân trang.

 */

export interface GetNewsListResponse {
  content: NewsItem[];

  totalElements: number;

  offset: number;

  size: number;

  last: boolean;

  empty: boolean;
}

/** Response of `GET public/news/search` (Spring-Page-shaped, 0-based `number` — unlike the offset-based list above). */
export interface NewsSearchPage {
  content: NewsItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
