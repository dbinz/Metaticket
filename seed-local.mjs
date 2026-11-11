// Local search fixtures only. Stable IDs make this safe to run again without duplicates.
import fs from 'node:fs';
import path from 'node:path';
const imageManifest = JSON.parse(fs.readFileSync(path.join(import.meta.dirname, 'metaticket-home-master/metaticket-home-master/src/assets/demo-events/manifest.json'), 'utf8'));
const api = 'http://127.0.0.1:48080';
const entries = [
  ['am-nhac', '[Mẫu] Đêm nhạc mùa thu', 'music.jpg', 'Âm nhạc', 'music', 'Hồ Chí Minh', '79', 350000],
  ['san-khau', '[Mẫu] Sân khấu cuối tuần', 'stage.jpg', 'Sân khấu', 'stage', 'Hà Nội', '01', 250000],
  ['the-thao', '[Mẫu] Ngày hội thể thao', 'sport.jpg', 'Thể thao', 'sport', 'Đà Nẵng', '48', 150000],
  ['hoi-thao', '[Mẫu] Hội thảo sáng tạo', 'conference.jpg', 'Hội thảo', 'conference', 'Hồ Chí Minh', '79', 0],
  ['thieu-nhi', '[Mẫu] Lễ hội thiếu nhi', 'children.jpg', 'Thiếu nhi', 'children', 'Hà Nội', '01', 120000],
  ['du-lich', '[Mẫu] Khám phá cuối tuần', 'tourist.jpg', 'Du lịch', 'tourist', 'Đà Nẵng', '48', 200000],
  ['workshop', '[Mẫu] Workshop sáng tạo cuối tuần', 'conference.jpg', 'Workshop', 'workshop', 'Hồ Chí Minh', '79', 180000],
  ['dien-anh', '[Mẫu] Đêm điện ảnh', 'stage.jpg', 'Điện ảnh', 'cinema', 'Hà Nội', '01', 100000],
];
const extraTitles = {
  music: ['Acoustic dưới ánh đèn', 'Giai điệu thanh xuân', 'Jazz bên sông', 'Rock và những người bạn', 'Hòa nhạc cuối năm'],
  stage: ['Kịch nói Chuyện phố nhỏ', 'Đêm hài cuối tuần', 'Múa đương đại Sắc màu', 'Nhạc kịch Giấc mơ xanh', 'Sân khấu cải lương Tình quê'],
  sport: ['Giải chạy Bình minh', 'Ngày hội bóng rổ', 'Yoga ngoài trời', 'Thử thách đạp xe', 'Giải cầu lông giao hữu'],
  conference: ['Hội thảo khởi nghiệp', 'Công nghệ và cuộc sống', 'Thiết kế vì cộng đồng', 'Kỹ năng nghề nghiệp', 'Kết nối doanh nghiệp trẻ'],
  children: ['Thế giới cổ tích', 'Xiếc vui cho bé', 'Ngày hội khoa học nhí', 'Sắc màu tuổi thơ', 'Nhà thám hiểm tí hon'],
  tourist: ['Hành trình phố cổ', 'Dã ngoại ven hồ', 'Khám phá làng nghề', 'Chuyến đi miền xanh', 'Trải nghiệm văn hóa địa phương'],
  workshop: ['Workshop làm gốm', 'Workshop vẽ màu nước', 'Workshop pha cà phê', 'Workshop làm nến thơm', 'Workshop nhiếp ảnh cơ bản'],
  cinema: ['Chiếu phim dưới bầu trời', 'Ngày hội phim ngắn', 'Điện ảnh và âm nhạc', 'Phim hoạt hình gia đình', 'Giao lưu cùng nhà làm phim'],
};
// Keep the original eight IDs; append five stable fixtures for each category.
const expandedEntries = entries.flatMap((entry) => [
  entry,
  ...extraTitles[entry[4]].map((title, index) => [
    `${entry[0]}-${index + 2}`, `[Mẫu] ${title}`, ...entry.slice(2),
  ]),
]);
const now = Date.now();
const firstDate = new Date(now + 14 * 86400000);
firstDate.setUTCHours(12, 0, 0, 0); // 19:00 Asia/Saigon.
const locations = [
  { city: 'Hà Nội', code: '01', key: 'ha-noi' },
  { city: 'Hồ Chí Minh', code: '79', key: 'ho-chi-minh' },
];
for (const [i, [key, name, image, category, categoryCode, , , price]] of expandedEntries.entries()) {
  const photo = imageManifest[categoryCode]?.[i % 6];
  if (!photo || !fs.existsSync(path.join(import.meta.dirname, 'metaticket-home-master/metaticket-home-master/src', photo.url))) {
    throw new Error(`Missing event image for ${key}. Run download-event-images.mjs first.`);
  }
  const imageUrl = photo.url;
  const startTime = firstDate.getTime() + (i % 6 * 30 + Math.floor(i / 6)) * 86400000;
  const location = locations[i % 2];
  const show = {
    id: `local-demo-${key}`,
    code: `LOCAL-DEMO-${i + 1}`,
    slug: `local-demo-${key}`,
    showName: name,
    status: 'Open',
    eventCategory: category,
    eventCategoryCode: categoryCode,
    thumbnailUrl: imageUrl,
    bannerImageUrl: imageUrl,
    detailImageUrl: imageUrl,
    createdAt: now,
    description: `${name.replace('[Mẫu] ', '')}: chương trình trải nghiệm thuộc nhóm ${category}, tổ chức tại ${location.city}. Dữ liệu mẫu dành cho thử nghiệm local, không phải sự kiện mở bán thực tế.`,
    minPrice: price,
    pinned: i % 6 === 0,
    pinOrder: i % 6 === 0 ? Math.floor(i / 6) + 1 : null,
    ranking: expandedEntries.length - i,
    sessions: [location].map((location) => ({
      id: `local-demo-session-${key}-${location.key}`,
      code: `LOCAL-DEMO-SESSION-${i + 1}-${location.code}`,
      slug: `local-demo-session-${key}-${location.key}`,
      status: 'Open',
      sessionName: `Suất diễn tại ${location.city}`,
      startTime,
      endTime: startTime + 7200000,
      cityName: location.city,
      cityCode: location.code,
      venue: `Địa điểm mẫu tại ${location.city}`,
      showInHome: true,
      isFree: price === 0,
      sessionType: 'SINGLE',
    })),
  };
  const response = await fetch(`${api}/internal/metasearch/shows/create-or-update`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-API-KEY': 'local-development-only' },
    body: JSON.stringify(show),
  });
  if (!response.ok) throw new Error(`${show.id}: HTTP ${response.status}: ${await response.text()}`);
  console.log(`Saved ${show.id}`);
}
console.log(`Created ${expandedEntries.length} local demo events (6 per category). Reload the website.`);
