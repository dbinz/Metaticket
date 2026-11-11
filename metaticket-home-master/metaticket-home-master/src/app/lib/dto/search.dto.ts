export type SearchTab = 'all' | 'event' | 'product' | 'news';

/** One result group (Sự kiện / Sản phẩm / Tin tức) with its own loading/failure state. */
export interface SearchGroup<T> {
  items: T[];
  total: number;
  loading: boolean;
  /** True when the request errored/timed out — renders "Không tải được" instead of "Không có kết quả". */
  failed: boolean;
}

export function emptySearchGroup<T>(): SearchGroup<T> {
  return { items: [], total: 0, loading: false, failed: false };
}

export function loadingSearchGroup<T>(): SearchGroup<T> {
  return { items: [], total: 0, loading: true, failed: false };
}

export function failedSearchGroup<T>(): SearchGroup<T> {
  return { items: [], total: 0, loading: false, failed: true };
}
