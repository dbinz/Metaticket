export type SeatType = 'SeatZone' | 'Seat';

export class SeatplanCategoryModel {
    id: string;
    displayName: string;
    catNames: string[];
}

export class CategoryMappingModel {
    showId: string;
    sessionId: string;
    kindMap: string;
    mapId: string;
    catMapping: Map<string, CategoryItemModel>;
}

export class CategoryItemModel {
    category: string;
    displayName: string;
    price: number;
    color: string;
    openDate: number;
    closeDate: number;
}

export class SeatplanModel {
    id: string;
    displayName: string;
    catNames: string[];
    items: PlanItemModel[];
}

export class PlanItemModel {
    id: string;
    sid: string;
    cat: string;
    displayName: string;
    type: SeatType;
    quantity: number = 1;
}
