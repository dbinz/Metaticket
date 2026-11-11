export function normalizePhoneNumber(phone: string): string {
  if (!phone) return '';

  // Loại bỏ khoảng trắng và ký tự đặc biệt
  const cleanPhone = phone.replace(/[\s\-()]/g, '');

  // Nếu có mã quốc gia +84 hoặc 84, chuyển về định dạng địa phương
  if (cleanPhone.startsWith('+84')) {
    return '0' + cleanPhone.substring(3);
  } else if (cleanPhone.startsWith('84') && cleanPhone.length === 11) {
    return '0' + cleanPhone.substring(2);
  }

  // Trả về số điện thoại gốc nếu đã ở định dạng địa phương
  return cleanPhone;
}
