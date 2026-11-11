export interface SeatPlanEvent {
  eventType: SeatPlanEventType;
  state: SeatPlanEventState;
}

type SeatPlanEventType = 'item-lowlight' | 'item-highlight' | 'item-selected' | 'item-deselected' | 'item-clicked';

interface SeatPlanItem {
  sid: string;
}

interface SeatPlanEventState {
  items: SeatPlanItem[];
  triggerItem?: SeatPlanItem;
  triggerEl: HTMLElement;
}

export interface SeatPlanTriggerEvent {
  eventType: SeatPlanEventType;
  items: SeatPlanItem[];
  triggerItem?: SeatPlanTriggerItem;
}

export interface SeatPlanTriggerItem {
  id: string;
  sid: string;
  priceCat: string;
  displayName: string;
  quantity: number;
  basePrice: number;
  remCap: number;
}
