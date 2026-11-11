import {
  buildSeatAdjacencyFromBlocks,
  checkSingleSeatGaps,
  seatGapDialogContent,
  SeatAdjacency,
} from './seat-gap.util';

function adjacencyOf(...blocks: string[][]): SeatAdjacency {
  const adjacency = buildSeatAdjacencyFromBlocks(blocks);
  if (!adjacency) {
    throw new Error('fixture did not build');
  }
  return adjacency;
}

/** Every listed sid is on sale with one seat left, the rest of the plan is sold. */
function free(...sids: string[]): Map<string, number> {
  return new Map(sids.map((sid) => [sid, 1]));
}

function range(prefix: string, from: number, to: number): string[] {
  return Array.from({ length: to - from + 1 }, (_, index) => `${prefix}${from + index}`);
}

function sameCat(sids: string[], cat = 'VIP'): Map<string, string> {
  return new Map(sids.map((sid) => [sid, cat]));
}

describe('seat-gap.util', () => {
  const tenSeats = range('A', 1, 10);

  describe('booking a single seat: at most one lone seat', () => {
    const box = ['T2AT1', 'T2AT2', 'T2AT3'];

    it('refuses the middle seat of a free 3-seat block (two lone seats)', () => {
      const result = checkSingleSeatGaps(adjacencyOf(box), ['T2AT2'], free(...box), sameCat(box));
      expect(result.gaps).toEqual(['T2AT1', 'T2AT3']);
    });

    it('accepts a seat that leaves one lone seat', () => {
      expect(checkSingleSeatGaps(adjacencyOf(tenSeats), ['A2'], free(...tenSeats), sameCat(tenSeats)).gaps).toEqual([]);
      // Next to a sold seat.
      expect(
        checkSingleSeatGaps(adjacencyOf(tenSeats), ['A7'], free(...range('A', 1, 8)), sameCat(tenSeats)).gaps,
      ).toEqual([]);
    });

    it('accepts the end seat of a free 3-seat block and either seat of a free pair', () => {
      expect(checkSingleSeatGaps(adjacencyOf(box), ['T2AT1'], free(...box), sameCat(box)).gaps).toEqual([]);
      expect(checkSingleSeatGaps(adjacencyOf(['P1', 'P2']), ['P2'], free('P1', 'P2'), sameCat(['P1'])).gaps).toEqual(
        [],
      );
    });

    it('refuses the middle seat of a 3-seat block cut off by an aisle', () => {
      const adjacency = adjacencyOf(['C1', 'C3', 'C5'], ['C2', 'C4', 'C6']);
      const sids = ['C1', 'C2', 'C3', 'C4', 'C5', 'C6'];
      expect(checkSingleSeatGaps(adjacency, ['C3'], free(...sids), sameCat(sids)).gaps).toEqual(['C1', 'C5']);
    });

    it('works with seat ids that clash with object keys', () => {
      const sids = ['__proto__', 'constructor', 'A1'];
      const result = checkSingleSeatGaps(adjacencyOf(sids), ['constructor'], free(...sids), sameCat(sids));
      expect(result.gaps).toEqual(jasmine.arrayWithExactContents(['__proto__', 'A1']));
    });
  });

  describe('booking two seats or more: no lone seat', () => {
    it('refuses a seat left alone at the block end', () => {
      const result = checkSingleSeatGaps(adjacencyOf(tenSeats), ['A2', 'A3'], free(...tenSeats), sameCat(tenSeats));
      expect(result.gaps).toEqual(['A1']);
    });

    it('refuses a single seat left between two chosen seats', () => {
      const result = checkSingleSeatGaps(adjacencyOf(tenSeats), ['A3', 'A5'], free(...tenSeats), sameCat(tenSeats));
      expect(result.gaps).toEqual(['A4']);
    });

    it('refuses a single seat left between a chosen seat and a sold one', () => {
      const result = checkSingleSeatGaps(
        adjacencyOf(tenSeats),
        ['A6', 'A7'],
        free(...range('A', 1, 8)),
        sameCat(tenSeats),
      );
      expect(result.gaps).toEqual(['A8']);
    });

    it('counts all chosen seats of the booking, even in different blocks', () => {
      // One seat in each block leaving one lone seat: fine for a single-seat booking, but this booking has two seats.
      const adjacency = adjacencyOf(range('A', 1, 4), range('B', 1, 4));
      const sids = [...range('A', 1, 4), ...range('B', 1, 4)];
      expect(checkSingleSeatGaps(adjacency, ['A2', 'B1'], free(...sids), sameCat(sids)).gaps).toEqual(['A1']);
    });

    it('accepts seats taken from the block end', () => {
      const result = checkSingleSeatGaps(adjacencyOf(tenSeats), ['A1', 'A2'], free(...tenSeats), sameCat(tenSeats));
      expect(result.gaps).toEqual([]);
    });

    it('ignores a lone seat that was already alone before the customer chose', () => {
      const result = checkSingleSeatGaps(
        adjacencyOf(tenSeats),
        ['A6', 'A7'],
        free('A2', ...range('A', 4, 10)),
        sameCat(tenSeats),
      );
      expect(result.gaps).toEqual([]);
    });

    it('treats a seat someone else is holding (remCap 0) like a sold seat', () => {
      const remCap = free(...tenSeats);
      remCap.set('A3', 0);
      const result = checkSingleSeatGaps(adjacencyOf(tenSeats), ['A5', 'A6'], remCap, sameCat(tenSeats));
      expect(result.gaps).toEqual(['A4']);
    });

    it('treats the end of a block as an aisle: two blocks of one row never share a neighbour', () => {
      const adjacency = adjacencyOf(['C1', 'C3', 'C5'], ['C2', 'C4', 'C6']);
      const sids = ['C1', 'C2', 'C3', 'C4', 'C5', 'C6'];
      // 2 of the 3-seat block: the spare C5 stays at the aisle end, which cannot be avoided.
      expect(checkSingleSeatGaps(adjacency, ['C1', 'C3'], free(...sids), sameCat(sids)).gaps).toEqual([]);
      expect(checkSingleSeatGaps(adjacency, ['C3', 'C2'], free(...sids), sameCat(sids)).gaps).toEqual(['C1', 'C5']);
    });

    it('follows the block order, not the seat numbers (odd/even from the centre)', () => {
      const adjacency = adjacencyOf(['C5', 'C3', 'C1', 'C2', 'C4', 'C6']);
      const sids = ['C1', 'C2', 'C3', 'C4', 'C5', 'C6'];
      // C1 and C2 sit side by side in the middle although their numbers are not consecutive.
      expect(checkSingleSeatGaps(adjacency, ['C1', 'C2'], free(...sids), sameCat(sids)).gaps).toEqual([]);
      expect(checkSingleSeatGaps(adjacency, ['C3', 'C2'], free(...sids), sameCat(sids)).gaps).toEqual(['C1', 'C5']);
    });

    it('ignores chosen seats that are not part of any block (seat zones, loose seats)', () => {
      const result = checkSingleSeatGaps(
        adjacencyOf(range('A', 1, 3)),
        ['ZONE-1', 'ZONE-2'],
        free('ZONE-1', 'ZONE-2', 'A1'),
        new Map(),
      );
      expect(result.gaps).toEqual([]);
    });
  });

  describe('booking two seats or more: a spare seat that cannot be avoided', () => {
    // Free cluster X2..X6 (X1 and X7 sold): 4 seats out of 5 always leave one.
    const row = range('X', 1, 7);
    const cluster = range('X', 2, 6);

    function gapsFor(...chosen: string[]): readonly string[] {
      return checkSingleSeatGaps(adjacencyOf(row), chosen, free(...cluster), sameCat(row)).gaps;
    }

    it('accepts it at the outer end of the free block', () => {
      expect(gapsFor('X2', 'X3', 'X4', 'X5')).toEqual([]);
      expect(gapsFor('X3', 'X4', 'X5', 'X6')).toEqual([]);
    });

    it('refuses it between the chosen seats', () => {
      expect(gapsFor('X2', 'X3', 'X5', 'X6')).toEqual(['X4']);
      expect(gapsFor('X2', 'X3', 'X4', 'X6')).toEqual(['X5']);
    });

    it('applies to a 3-seat block with 2 seats bought', () => {
      const box = ['T2AT1', 'T2AT2', 'T2AT3'];
      const check = (...chosen: string[]): readonly string[] =>
        checkSingleSeatGaps(adjacencyOf(box), chosen, free(...box), sameCat(box)).gaps;
      expect(check('T2AT1', 'T2AT2')).toEqual([]);
      expect(check('T2AT2', 'T2AT3')).toEqual([]);
      expect(check('T2AT1', 'T2AT3')).toEqual(['T2AT2']);
    });

    it('does not apply when one more seat is free: the spare seats can be avoided', () => {
      const remCap = free(...range('A', 4, 7));
      expect(checkSingleSeatGaps(adjacencyOf(tenSeats), ['A5', 'A6'], remCap, sameCat(tenSeats)).gaps).toEqual([
        'A4',
        'A7',
      ]);
    });
  });

  describe('suggestions', () => {
    it('offers the two closest blocks of neighbouring seats that leave nothing alone', () => {
      const result = checkSingleSeatGaps(adjacencyOf(tenSeats), ['A2', 'A3'], free(...tenSeats), sameCat(tenSeats));
      expect(result.suggestions).toEqual([
        {
          replace: ['A2', 'A3'],
          options: [
            ['A1', 'A2'],
            ['A3', 'A4'],
          ],
        },
      ]);
    });

    it('closes a hole between two chosen seats', () => {
      const result = checkSingleSeatGaps(adjacencyOf(tenSeats), ['A4', 'A6'], free(...tenSeats), sameCat(tenSeats));
      expect(result.suggestions).toEqual([
        {
          replace: ['A4', 'A6'],
          options: [
            ['A4', 'A5'],
            ['A5', 'A6'],
          ],
        },
      ]);
    });

    it('moves a single seat to where it leaves at most one lone seat', () => {
      const box = ['T2AT1', 'T2AT2', 'T2AT3'];
      const result = checkSingleSeatGaps(adjacencyOf(box), ['T2AT2'], free(...box), sameCat(box));
      expect(result.suggestions).toEqual([{ replace: ['T2AT2'], options: [['T2AT1'], ['T2AT3']] }]);
    });

    it('keeps the spare seat at the block end when it cannot be avoided', () => {
      const row = range('X', 1, 7);
      const result = checkSingleSeatGaps(
        adjacencyOf(row),
        ['X2', 'X3', 'X5', 'X6'],
        free(...range('X', 2, 6)),
        sameCat(row),
      );
      expect(result.suggestions).toEqual([
        {
          replace: ['X2', 'X3', 'X5', 'X6'],
          options: [
            ['X2', 'X3', 'X4', 'X5'],
            ['X3', 'X4', 'X5', 'X6'],
          ],
        },
      ]);
    });

    it('stays inside the block that has the lone seat', () => {
      const all = range('Y', 1, 12);
      const result = checkSingleSeatGaps(
        adjacencyOf(['Y12', 'Y11', 'Y10', 'Y9', 'Y8', 'Y7'], ['Y6', 'Y5', 'Y4', 'Y3', 'Y2', 'Y1']),
        ['Y9', 'Y8'],
        free(...all),
        sameCat(all),
      );
      expect(result.gaps).toEqual(['Y7']);
      expect(result.suggestions).toEqual([
        {
          replace: ['Y8', 'Y9'],
          options: [
            ['Y7', 'Y8'],
            ['Y9', 'Y10'],
          ],
        },
      ]);
    });

    it('keeps the price categories of the chosen seats so the total does not change', () => {
      const sids = range('A', 1, 8);
      const cats = new Map<string, string>([
        ['A1', 'VIP'],
        ['A2', 'VIP'],
        ['A3', 'VIP'],
        ['A4', 'VIP'],
        ['A5', 'STD'],
        ['A6', 'STD'],
        ['A7', 'STD'],
        ['A8', 'STD'],
      ]);
      // A3 + A5 (VIP + STD) leave A4 alone; the only VIP+STD pair that leaves no lone seat is A4 A5: one option only.
      const result = checkSingleSeatGaps(adjacencyOf(sids), ['A3', 'A5'], free(...sids), cats);
      expect(result.suggestions).toEqual([{ replace: ['A3', 'A5'], options: [['A4', 'A5']] }]);
    });

    it('gives no suggestion when no block fits', () => {
      const sids = range('A', 1, 4);
      const cats = new Map<string, string>([
        ['A1', 'VIP'],
        ['A2', 'STD'],
        ['A3', 'VIP'],
        ['A4', 'STD'],
      ]);
      // A2 + A4 (STD + STD) leave A3 alone; no two neighbouring STD seats exist in the row.
      const result = checkSingleSeatGaps(adjacencyOf(sids), ['A2', 'A4'], free(...sids), cats);
      expect(result.gaps).toEqual(['A1', 'A3']);
      expect(result.suggestions).toEqual([]);
    });
  });

  describe('buildSeatAdjacencyFromBlocks', () => {
    it('returns null for data that holds no block', () => {
      expect(buildSeatAdjacencyFromBlocks(null)).toBeNull();
      expect(buildSeatAdjacencyFromBlocks(undefined)).toBeNull();
      expect(buildSeatAdjacencyFromBlocks({})).toBeNull();
      expect(buildSeatAdjacencyFromBlocks([])).toBeNull();
      expect(buildSeatAdjacencyFromBlocks([[], 'A1', [1, 2]])).toBeNull();
    });

    it('cuts a block at a malformed entry instead of joining the seats around it', () => {
      const adjacency = buildSeatAdjacencyFromBlocks([['A1', 'A2', null, 'A3', 'A4', '', 'A5'], 'garbage', null]);
      expect(adjacency?.neighbors.get('A2')).toEqual(['A1']);
      expect(adjacency?.neighbors.get('A3')).toEqual(['A4']);
      expect(adjacency?.neighbors.get('A5')).toEqual([]);
    });
  });

  describe('seatGapDialogContent', () => {
    it('lists the lone seats and each alternative as text', () => {
      expect(
        seatGapDialogContent({
          gaps: ['A1'],
          suggestions: [
            {
              replace: ['A2', 'A3'],
              options: [
                ['A1', 'A2'],
                ['A3', 'A4'],
              ],
            },
          ],
        }),
      ).toEqual({ loneSeats: 'A1', suggestions: [{ replace: 'A2, A3', options: ['A1, A2', 'A3, A4'] }] });
    });

    it('caps long seat lists and keeps an empty suggestion list', () => {
      const gaps = ['A1', 'A3', 'A5', 'A7', 'A9', 'A11'];
      expect(seatGapDialogContent({ gaps, suggestions: [] })).toEqual({
        loneSeats: 'A1, A3, A5, A7, A9, …',
        suggestions: [],
      });
    });
  });
});
