/** Seat blocks of a seat plan (public `editor/seatplanner/seat-blocks`): seats in seating order, no aisle inside a block. */
export interface SeatBlocksResponse {
  blocks: string[][];
  /** The plan changed after the blocks were saved: they must not be trusted until an admin reviews them again. */
  stale: boolean;
}
