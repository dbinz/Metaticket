/** Sale chip: only Open / OpeningWait / Paused; other statuses hide the badge. */
export type ShowcaseStatus = 'open' | 'openingWait' | 'paused';

/** Ticket show card for “Sự kiện nổi bật”. */
export interface ShowcaseShowCard {
  id: string;
  title: string;
  category: string;
  dateLabel: string;
  venue: string;
  status: ShowcaseStatus | null;
  statusLabel: string;
  imageUrl: string;
  logoShowUrl: string;
  path: string;
  /** "Từ …₫"; empty when the show has no price to advertise. */
  priceLabel: string;
}

/** First homepage highlight block: pin/order 1 as hero, 2–3 beside, 4–6 below. */
export interface ShowcaseShowBatch {
  hero?: ShowcaseShowCard;
  side: ShowcaseShowCard[];
  bottom: ShowcaseShowCard[];
}

/** Accessory-show card for the merch strip. */
export interface ShowcaseMerchCard {
  id: string;
  title: string;
  category: string;
  meta: string;
  priceLabel: string;
  status: ShowcaseStatus;
  statusLabel: string;
  imageUrl: string;
  path: string;
  ctaLabel: string;
}

/** Editorial card for the “Giải mã” strip. */
export interface ShowcaseArticleCard {
  id: string;
  title: string;
  subdesc: string;
  category: string;
  meta: string;
  imageUrl: string;
  slug: string;
}
