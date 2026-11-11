export interface SearchShowFilter {
  page: number;
  size: number;
  eventCategoryCode: string;
  cities: string[];
  startTime?: number | null;
  endTime?: number | null;
  isFree?: boolean | null;
}
