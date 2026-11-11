import { AccessoryShowResponse, SearchShowResponse } from 'src/app/lib/entities';
import { NewsItem } from 'src/app/lib/entities/news.response';
import { SearchSuggestGroups } from 'src/app/lib/services/search/search.service';

export type SearchFlatItem =
  | { kind: 'event'; item: SearchShowResponse }
  | { kind: 'product'; item: AccessoryShowResponse }
  | { kind: 'news'; item: NewsItem };

/** Flattens the 3 suggestion groups in display order (Sự kiện → Sản phẩm → Tin tức) for arrow-key navigation. */
export function flattenSuggestGroups(groups: SearchSuggestGroups): SearchFlatItem[] {
  return [
    ...groups.events.items.map((item): SearchFlatItem => ({ kind: 'event', item })),
    ...groups.products.items.map((item): SearchFlatItem => ({ kind: 'product', item })),
    ...groups.news.items.map((item): SearchFlatItem => ({ kind: 'news', item })),
  ];
}

/**
 * Whether the suggest panel has anything worth showing — loading, at least one result, or every
 * group failed (a real "couldn't load" message). A plain "no results for this keyword" is
 * deliberately excluded: the dropdown should just show nothing rather than clutter typing with an
 * empty-state message — that message only belongs on the full `/search` page.
 */
export function hasSuggestContent(groups: SearchSuggestGroups): boolean {
  const g = groups;
  return (
    g.events.loading ||
    g.products.loading ||
    g.news.loading ||
    g.events.items.length > 0 ||
    g.products.items.length > 0 ||
    g.news.items.length > 0 ||
    (g.events.failed && g.products.failed && g.news.failed)
  );
}
