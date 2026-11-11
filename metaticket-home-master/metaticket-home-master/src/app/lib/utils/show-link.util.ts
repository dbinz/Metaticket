import { routeName } from 'src/app/lib/constants';

/** A show reference that may carry a canonical slug. */
export interface ShowLinkRef {
  id: string;
  slug?: string;
}

/**
 * Path to a show's detail page. Prefers the slug link `/s/{slug}` when the show has a non-empty
 * canonical slug; otherwise falls back to the existing id route `/show/{id}`. Both open the same
 * show detail page.
 */
export function showDetailPath(show: ShowLinkRef): string {
  const slug = show.slug?.trim();
  return slug ? `/${routeName.slugPrefix}/${slug}` : `/${routeName.show}/${show.id}`;
}
