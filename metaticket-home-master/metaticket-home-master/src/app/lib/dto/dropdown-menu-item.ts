import { EdType } from '../enums';

export interface DropdownMenuItem {
  title: string;
  value: string;
  edType?: EdType;
  legendImageUrl?: string;
  /** Nếu có routerLink, item sẽ được render thành <a> chuẩn SEO thay vì <button> */
  routerLink?: string | any[];
  queryParams?: Record<string, string>;
}
export interface DropdownMenuButton {
  title: string;
  value: string;
  check: boolean;
}
export interface DropDownDatePicker {
  fromDate: Date | null;
  toDate: Date | null;
}
