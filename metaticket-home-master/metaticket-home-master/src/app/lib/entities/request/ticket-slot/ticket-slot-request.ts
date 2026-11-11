export interface TransferTicketSlotRequest {
  slotId: string;
  currentStateIdx: number;
  version: number;
  newOwnerPhone: string;
  newOwnerName: string;
}
