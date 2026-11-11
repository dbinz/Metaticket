/**
 * "Ghế lẻ" rule of the seat map (cinema style): a booking must not leave one free seat on its own next to the seats
 * the customer picked, whether between two taken seats or against the row end / an aisle.
 *
 * Which seats sit next to each other comes from the seat plan's seat blocks, prepared and reviewed by an admin in the
 * portal ("Phân chia ghế"): each block lists, in seating order, seats with no aisle between them; both ends of a block
 * are a row end or an aisle. Seat numbers cannot be used instead because venues often number odd/even from the centre.
 */

const MAX_LISTED_SEATS = 5;
const MAX_LISTED_SUGGESTIONS = 2;
/** Alternatives offered per row block. */
const MAX_OPTIONS = 2;

export interface SeatAdjacency {
  /** sid → neighbouring sids in the same block. Seats outside every block are absent (the rule ignores them). */
  readonly neighbors: ReadonlyMap<string, readonly string[]>;
}

export interface SeatSuggestion {
  /** The customer's seats in one row block, in row order. */
  readonly replace: readonly string[];
  /** One or two alternatives, closest first: neighbouring seats of the same price categories that the rule accepts. */
  readonly options: readonly (readonly string[])[];
}

export interface SingleSeatGapCheck {
  /** Seats that would be left alone, natural order; empty = the choice is fine. */
  readonly gaps: readonly string[];
  readonly suggestions: readonly SeatSuggestion[];
}

const naturalOrder = (a: string, b: string): number => a.localeCompare(b, undefined, { numeric: true });

function link(neighbors: Map<string, Set<string>>, a: string, b: string): void {
  if (a === b) {
    return;
  }
  neighbors.get(a)?.add(b);
  neighbors.get(b)?.add(a);
}

/**
 * Builds the neighbour graph from the seat blocks returned by the seat plan service. Returns null when the data holds no
 * usable block (unexpected shape): callers then skip the rule rather than block sales on a data problem. A malformed
 * block is skipped; a malformed entry inside a block cuts it there, so the seats on both sides are never joined.
 */
export function buildSeatAdjacencyFromBlocks(blocks: unknown): SeatAdjacency | null {
  if (!Array.isArray(blocks)) {
    return null;
  }
  const neighbors = new Map<string, Set<string>>();
  for (const block of blocks as unknown[]) {
    if (!Array.isArray(block)) {
      continue;
    }
    let previous: string | null = null;
    for (const sid of block as unknown[]) {
      if (typeof sid !== 'string' || sid === '') {
        previous = null;
        continue;
      }
      if (!neighbors.has(sid)) {
        neighbors.set(sid, new Set());
      }
      if (previous !== null) {
        link(neighbors, previous, sid);
      }
      previous = sid;
    }
  }
  if (neighbors.size === 0) {
    return null;
  }
  return { neighbors: new Map([...neighbors].map(([sid, set]) => [sid, [...set]])) };
}

/** Seats reachable from `start` through seats accepted by `isOpen` (row neighbours only). */
function rowBlock(adjacency: SeatAdjacency, start: string, isOpen: (sid: string) => boolean): Set<string> {
  const block = new Set<string>([start]);
  const queue = [start];
  while (queue.length > 0) {
    const sid = queue.pop() as string;
    for (const next of adjacency.neighbors.get(sid) ?? []) {
      if (!block.has(next) && isOpen(next)) {
        block.add(next);
        queue.push(next);
      }
    }
  }
  return block;
}

/** Orders a row block as a simple path; null when it branches or loops (no sensible "slide" then). */
function asPath(adjacency: SeatAdjacency, block: ReadonlySet<string>): string[] | null {
  const inBlock = (sid: string): string[] => (adjacency.neighbors.get(sid) ?? []).filter((next) => block.has(next));
  const sids = [...block];
  if (sids.some((sid) => inBlock(sid).length > 2)) {
    return null;
  }
  const ends = sids.filter((sid) => inBlock(sid).length <= 1).sort(naturalOrder);
  if (ends.length === 0) {
    return null;
  }
  const path = [ends[0]];
  let previous: string | null = null;
  let current = ends[0];
  while (path.length < sids.length) {
    const next = inBlock(current).find((sid) => sid !== previous);
    if (next === undefined) {
      break;
    }
    path.push(next);
    previous = current;
    current = next;
  }
  return path.length === sids.length ? path : null;
}

const categoriesKey = (sids: readonly string[], priceCatBySid: ReadonlyMap<string, string>): string =>
  sids
    .map((sid) => priceCatBySid.get(sid) ?? '')
    .sort()
    .join('\u0000');

/**
 * Up to `MAX_OPTIONS` blocks of `chosen.length` neighbouring seats in `path` with the same price categories that leave no
 * lone seat on either side, closest to the original choice first (ties: row order). When exactly one seat more than
 * chosen is free the spare one is unavoidable and, with the chosen seats side by side, always at an end, so every window
 * is accepted. (A single seat is refused only in a free block of exactly 3, where both end seats leave no lone seat.)
 */
function slideChoices(
  path: readonly string[],
  chosen: ReadonlySet<string>,
  priceCatBySid: ReadonlyMap<string, string>,
): string[][] {
  const chosenIndexes = path.flatMap((sid, index) => (chosen.has(sid) ? [index] : []));
  const size = chosenIndexes.length;
  const centre = chosenIndexes.reduce((sum, index) => sum + index, 0) / size;
  const wanted = categoriesKey(
    path.filter((sid) => chosen.has(sid)),
    priceCatBySid,
  );
  const isSpareUnavoidable = path.length - size === 1;
  const candidates: { window: string[]; offset: number }[] = [];
  for (let start = 0; start + size <= path.length; start++) {
    const before = start;
    const after = path.length - start - size;
    if ((before === 1 || after === 1) && !isSpareUnavoidable) {
      continue;
    }
    const window = path.slice(start, start + size);
    if (categoriesKey(window, priceCatBySid) === wanted) {
      candidates.push({ window, offset: Math.abs(start + (size - 1) / 2 - centre) });
    }
  }
  // Stable sort keeps row order between equally close windows.
  return candidates
    .sort((a, b) => a.offset - b.offset)
    .slice(0, MAX_OPTIONS)
    .map(({ window }) => window);
}

/**
 * Checks the customer's seats against the lone-seat rule and proposes neighbouring seats for each offending row block.
 *
 * A lone seat is a free seat next to a chosen seat with no free neighbour left. A seat that was already alone before the
 * choice never touches a chosen seat, so it never counts. The rule depends on the size of the whole booking:
 * - one seat: refused only when it leaves two lone seats or more (one lone seat next to a single buyer is accepted);
 * - two seats or more: every lone seat is refused, except one that cannot be avoided (its free block, counting chosen
 *   seats as free, is exactly one seat longer than the seats chosen there) and stays at the outer end of that block —
 *   never between the chosen seats.
 * `remCapBySid` holds the latest remaining capacity per seat; seats missing from it count as taken.
 */
export function checkSingleSeatGaps(
  adjacency: SeatAdjacency,
  selectedSids: readonly string[],
  remCapBySid: ReadonlyMap<string, number>,
  priceCatBySid: ReadonlyMap<string, string>,
): SingleSeatGapCheck {
  const chosen = new Set(selectedSids);
  const isFree = (sid: string): boolean => !chosen.has(sid) && (remCapBySid.get(sid) ?? 0) > 0;
  const isOpen = (sid: string): boolean => chosen.has(sid) || isFree(sid);

  const lone: { sid: string; block: Set<string> }[] = [];
  for (const sid of chosen) {
    for (const next of adjacency.neighbors.get(sid) ?? []) {
      const isAlone = isFree(next) && !(adjacency.neighbors.get(next) ?? []).some(isFree);
      if (isAlone && !lone.some((known) => known.sid === next)) {
        lone.push({ sid: next, block: rowBlock(adjacency, next, isOpen) });
      }
    }
  }

  const isSingleSeat = chosen.size === 1;
  const isAcceptedSpare = ({ sid, block }: { sid: string; block: Set<string> }): boolean => {
    const chosenInBlock = [...block].filter((member) => chosen.has(member)).length;
    const neighboursInBlock = (adjacency.neighbors.get(sid) ?? []).filter((next) => block.has(next)).length;
    return block.size - chosenInBlock === 1 && neighboursInBlock <= 1;
  };
  const refused = isSingleSeat ? (lone.length >= 2 ? lone : []) : lone.filter((seat) => !isAcceptedSpare(seat));

  const offendingBlocks: Set<string>[] = [];
  for (const { sid, block } of refused) {
    if (!offendingBlocks.some((known) => known.has(sid))) {
      offendingBlocks.push(block);
    }
  }
  const suggestions: SeatSuggestion[] = [];
  for (const block of offendingBlocks) {
    const path = asPath(adjacency, block);
    const options = path ? slideChoices(path, chosen, priceCatBySid) : [];
    if (path && options.length > 0) {
      suggestions.push({ replace: path.filter((sid) => chosen.has(sid)), options });
    }
  }

  return { gaps: refused.map(({ sid }) => sid).sort(naturalOrder), suggestions };
}

function listSeats(sids: readonly string[]): string {
  const shown = [...sids].sort(naturalOrder).slice(0, MAX_LISTED_SEATS);
  return sids.length > MAX_LISTED_SEATS ? `${shown.join(', ')}, …` : shown.join(', ');
}

/** Ready-to-show texts of the "lone seat" dialog; plain strings, rendered through Angular interpolation. */
export interface SeatGapDialogContent {
  /** Lone seats, e.g. "A1, A3" (capped with "…"). */
  readonly loneSeats: string;
  /** One entry per row block with a lone seat: the customer's seats there and 1–2 alternatives, each like "A1, A2". */
  readonly suggestions: readonly { readonly replace: string; readonly options: readonly string[] }[];
}

export function seatGapDialogContent(check: SingleSeatGapCheck): SeatGapDialogContent {
  return {
    loneSeats: listSeats(check.gaps),
    suggestions: check.suggestions.slice(0, MAX_LISTED_SUGGESTIONS).map((hint) => ({
      replace: listSeats(hint.replace),
      options: hint.options.map((option) => listSeats(option)),
    })),
  };
}
