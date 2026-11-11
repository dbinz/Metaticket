import { CustomFieldType } from 'src/app/lib/enums';
import { SelectOptionItem } from 'src/app/lib/dto';

export interface CustomFieldResponse {
  id: string;
  type: CustomFieldType;
  displayName: string;
  constraints: string;
  note?: string;
}

export interface CustomFieldConstraint {
  require: boolean;
  regex?: string;
  options?: SelectOptionItem[];
}
