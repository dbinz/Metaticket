import { EdType, PaymentType, SessionStatus } from 'src/app/lib/enums';
import { CityResponse } from './city.response';

export interface ShowResponse {
  id: string;
  showName: string;
  code: string;
  website: string;
  bannerImageUrl: string;
  logoHostUrl?: string;
  logoShowUrl: string;
  faq?: string;
  description?: string;
  policy?: string;
  ticketDescription?: string;
  /**
   * When true, hide the sessions/products section ("Suất diễn/Sản phẩm") on the FE.
   * Default is false.
   */
  hideSessions?: boolean;
  status: string;
  shipPrice: number;
  createdAt: number;
  sessions: SessionResponse[];
  detailImageUrl: string;
  thumbnailUrl: string;
  // Canonical_Show_Slug; always present in fetch responses, empty string when absent
  slug: string;
}

export interface SessionResponse {
  id: string;
  venue: string;
  seatPlanId: string;
  edType: EdType;
  isFree: boolean;
  ledgendImageUrl?: string;
  extraInfo?: string;
  requireDelivery: boolean;
  paymentOffline: boolean;
  paymentNote: string;
  orderNote: string | null;
  code: string;
  sessionName: string | null;
  startTime: number;
  endTime: number;
  status: SessionStatus;
  mailPluginTemplate: MailPluginTemplateResponse[];
  city?: CityResponse;
  paymentMethods: PaymentType[];
  slug: string;
  /** Absent on sessions created before calendar tickets existed — treat as SINGLE. */
  sessionType?: SessionType;
  /** Only set for CALENDAR sessions; startTime/endTime then mean 00:00 of the first and 23:59 of the last date (VN). */
  calendar?: SessionCalendarResponse | null;
  /** Seat map refuses choices that leave a lone free seat; absent on older backends = off. */
  preventSingleSeatGap?: boolean;
}

export type SessionType = 'SINGLE' | 'CALENDAR';

export interface SessionCalendarResponse {
  /** "HH:mm", Vietnam time. */
  openTime: string;
  closeTime: string;
  bookingWindowDays: number;
  sameDayCutoffMinutes?: number | null;
}

/**
 * Result of the Fetch_By_Slug_Endpoint: a ShowResponse plus the identifier of the
 * session matched by a session slug (present only when a session slug was resolved).
 */
export type ShowBySlugResponse = ShowResponse & { matchedSessionId?: string };

export interface SearchSessionResponse {
  id: string;
  startTime: number;
  venue: string;
  code: string;
  sessionName: string | null;
  cityName: string | null;
  cityCode: string | null;
  isFree: boolean;
  showInHome: boolean;
  // Canonical_Session_Slug; "" when the session has no slug.
  slug: string;
  /** For a running CALENDAR session metasearch already sends startTime = "now"; endTime is its last date. */
  endTime?: number;
  sessionType?: SessionType;
}

export interface MailPluginTemplateResponse {
  orderStatus: string;
  plugin: string;
  templateId: string;
}

export interface GetShowResponse {
  id: string;
  showName: string;
  code: string;
  website: string;
  bannerImageUrl: string;
  logoHostUrl: null;
  faq: null;
  description: null;
  policy: null;
  logoShowUrl: string;
  status: string;
  shipPrice: number;
  createdAt: number;
  sessions: SessionResponse[];
  price: number;
}
export interface SearchShowResponse {
  id: string;
  showName: string;
  code: string;
  bannerImageUrl: string;
  description: null;
  status: string;
  createdAt: number;
  /** Present on filter/banner/search; omitted on highlight cards. */
  sessions?: SearchSessionResponse[];
  eventCategoryCode: string;
  eventCategory: string;
  priceList: {
    key: string;
    value: number;
  }[];
  ranking: number;
  detailImageUrl?: string;
  thumbnailUrl: string;
  logoShowUrl?: string;
  // Canonical_Show_Slug; "" when the show has no slug.
  slug: string;
  /** Highlight card: nearest session start (omit on filter/banner payloads). */
  startTime?: number;
  venue?: string | null;
  cityName?: string | null;
  /**
   * Lowest ticket base price (VND) of the show, for the "Từ …₫" card label.
   * Absent for free shows and for shows whose tickets are not priced yet — the card then shows
   * no price rather than "Từ 0₫".
   */
  minPrice?: number | null;
  /** Highlight card: last date of a running calendar session, for "Đang diễn ra · đến …". */
  ongoingUntil?: number | null;
}

export interface GetHighlightShowResponse {
  content: SearchShowResponse[];
  pageable: PageableResponse;
  last: boolean;
  ranking: number;
  sessions: SessionResponse[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  sort: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  numberOfElements: number;
  first: boolean;
  empty: boolean;
}
export interface PageableResponse {
  sort: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  offset: number;
  pageSize: number;
  pageNumber: number;
  paged: boolean;
  unpaged: boolean;
}
