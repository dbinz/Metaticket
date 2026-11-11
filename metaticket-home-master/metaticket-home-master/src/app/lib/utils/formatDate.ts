export function formatDate(timestamp: number): string {
  const date = new Date(timestamp);

  return date.toLocaleDateString('vi-VN', {
    weekday: 'long',
    hour: '2-digit',
    minute: '2-digit',
    day: 'numeric',
    month: 'numeric',
    year: 'numeric',
  });
}
