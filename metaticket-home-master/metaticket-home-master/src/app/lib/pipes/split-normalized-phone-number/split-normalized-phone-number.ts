import { Pipe, PipeTransform } from '@angular/core';
import { parsePhoneNumberFromString } from 'libphonenumber-js';

@Pipe({
  name: 'normalizePhone',
  standalone: true,
})
export class NormalizePhonePipe implements PipeTransform {
  transform(phoneNumber: string, ret: 'countryCode' | 'nationalNumber'): string | null {
    if (!phoneNumber) return null;

    const countryCode = '84';
    const code = countryCode.replace(/^\+/, '');

    if (!/^\d+$/.test(code)) {
      return null; // Invalid country code
    }

    const cleanedNumber = phoneNumber.replace(/\s+/g, '');
    if (cleanedNumber.length === 0) {
      return null;
    }

    const isHadPlus: boolean = cleanedNumber.startsWith('+');
    const numberPortion = isHadPlus ? cleanedNumber.slice(1) : cleanedNumber;

    if (!/^\d+$/.test(numberPortion)) {
      return null;
    }

    let normalized: string;

    if (numberPortion.startsWith(code)) {
      normalized = numberPortion;
    } else if (isHadPlus) {
      return null;
    } else if (numberPortion.startsWith('0')) {
      const nationalNumber = numberPortion.slice(1);
      if (nationalNumber.length === 0) return null;
      normalized = code + nationalNumber;
    } else {
      normalized = code + numberPortion;
    }

    return ret === 'countryCode' ? code : normalized.slice(code.length);
  }
}

export function splitNormalizedPhoneNumber(
  normalized: string,
): { countryCode: string; nationalNumber: string; number: string } | null {
  const phoneNumber = parsePhoneNumberFromString(`+${normalized}`);

  if (!phoneNumber) {
    return null;
  }

  return {
    countryCode: phoneNumber.countryCallingCode,
    nationalNumber: phoneNumber.nationalNumber,
    number: phoneNumber.number, // Ex: '+84912345678'
  };
}
