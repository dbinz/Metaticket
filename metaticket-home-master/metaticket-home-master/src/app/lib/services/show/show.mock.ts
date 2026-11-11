import { GetHighlightShowResponse, SearchShowFilter, SearchShowResponse, ShowResponse } from 'src/app/lib/entities';
import { EdType, SessionStatus } from 'src/app/lib/enums';

export const MOCK_SHOW_CATEGORIES = [
  { id: 'music', name: 'Âm nhạc', titles: ['Ký ức mùa thu', 'Acoustic Chuyện hôm qua', 'Jazz bên sông', 'Rock Thanh xuân', 'Hòa nhạc Ánh trăng', 'Giai điệu phố cũ', 'Tình ca tháng mười', 'Live concert Bình minh', 'Acoustic Hẹn ngày mai', 'Chào mùa mới'] },
  { id: 'cinema', name: 'Điện ảnh', titles: ['Thước phim xưa', 'Điện ảnh tuổi trẻ', 'Phim ngắn cuối tuần', 'Chuyện trên màn ảnh', 'Hành trình điện ảnh', 'Hoạt hình gia đình', 'Gặp gỡ đạo diễn', 'Liên hoan phim mới', 'Chiếu phim ngoài trời', 'Đêm phim Việt'] },
  { id: 'workshop', name: 'Workshop', titles: ['Làm gốm thủ công', 'Vẽ màu nước', 'Pha cà phê', 'Làm nến thơm', 'Nhiếp ảnh cơ bản', 'Cắm hoa nghệ thuật', 'Làm bánh cuối tuần', 'Thiết kế sổ tay', 'Vẽ tranh lụa', 'Sáng tạo cùng đất sét'] },
  { id: 'conference', name: 'Hội thảo', titles: ['Khởi nghiệp sáng tạo', 'Kỹ năng nghề nghiệp', 'Công nghệ cuộc sống', 'Thiết kế cộng đồng', 'Kết nối doanh nghiệp', 'Giáo dục tương lai', 'Truyền thông số', 'Trí tuệ nhân tạo', 'Kinh tế xanh', 'Lãnh đạo trẻ'] },
  { id: 'stage', name: 'Sân khấu', titles: ['Chuyện phố nhỏ', 'Đêm hài tháng trước', 'Sắc màu đương đại', 'Giấc mơ xanh', 'Tình quê', 'Chuyện nhà mình', 'Mùa sân khấu cũ', 'Nhạc kịch Ngày mới', 'Kịch nói Hẹn ước', 'Đêm múa Ánh sáng'] },
  { id: 'sport', name: 'Thể thao', titles: ['Giải chạy Bình minh', 'Ngày hội bóng rổ', 'Yoga ngoài trời', 'Đua xe cuối tuần', 'Cầu lông giao hữu', 'Bóng đá cộng đồng', 'Bơi lội mùa thu', 'Marathon Ngày mới', 'Tennis mở rộng', 'Ngày hội vận động'] },
  { id: 'children', name: 'Thiếu nhi', titles: ['Thế giới cổ tích', 'Xiếc vui cho bé', 'Khoa học nhí', 'Sắc màu tuổi thơ', 'Nhà thám hiểm nhỏ', 'Ngày hội đọc sách', 'Vui cùng rối nước', 'Phiêu lưu vũ trụ', 'Lễ hội bong bóng', 'Âm nhạc cho bé'] },
  { id: 'tourist', name: 'Du lịch', titles: ['Hành trình phố cổ', 'Dã ngoại ven hồ', 'Khám phá làng nghề', 'Chuyến đi miền xanh', 'Văn hóa địa phương', 'Du ngoạn trên sông', 'Về miền di sản', 'Khám phá bình minh', 'Cuối tuần ngoại ô', 'Hành trình mùa mới'] },
];

/** Dates are relative to today's Vietnam calendar date, always seven past and three future shows. */
export function mockShows(now = Date.now()): SearchShowResponse[] {
  const today = new Date(now + 7 * 3600000).toISOString().slice(0, 10);
  const evening = Date.parse(`${today}T19:00:00+07:00`);
  return MOCK_SHOW_CATEGORIES.flatMap((category) => category.titles.map((name, index) => {
    const startTime = evening + (index < 7 ? -(7 - index) * 7 : (index - 6) * 7) * 86400000;
    const id = `mock-${category.id}-show-${index + 1}`;
    const image = `/assets/demo-events/${category.id}-${index % 6 + 1}.jpg`;
    const city = index % 2 === 0
      ? { code: '01', name: 'Hà Nội' }
      : { code: '79', name: 'Hồ Chí Minh' };
    const venue = `Địa điểm mẫu tại ${city.name}`;
    return {
      id, slug: id, code: id.toUpperCase(), showName: `[Mẫu] ${name}`,
      bannerImageUrl: image, thumbnailUrl: image, detailImageUrl: image,
      description: null, status: index < 7 ? 'Stopped' : 'Open', createdAt: evening,
      eventCategoryCode: category.id, eventCategory: category.name,
      priceList: [], minPrice: 250000 + index * 25000, ranking: 10 - index,
      startTime, venue, cityName: city.name,
      sessions: [{
        id: `${id}-session`, slug: `${id}-session`, code: `${id}-session`,
        sessionName: 'Suất diễn 19:00', startTime, endTime: startTime + 7200000,
        cityName: city.name, cityCode: city.code, venue,
        isFree: false, showInHome: true, sessionType: 'SINGLE',
      }],
    };
  }));
}

export function mockShowPage(filter: SearchShowFilter, existing: SearchShowResponse[] = []): GetHighlightShowResponse {
  const page = Math.max(0, filter.page);
  const size = Math.max(1, filter.size);
  const fixtures = mockShows().filter((show) => {
    const session = show.sessions![0];
    return (!filter.eventCategoryCode || show.eventCategoryCode === filter.eventCategoryCode) &&
      (!filter.cities?.length || filter.cities.includes(session.cityCode!)) &&
      (filter.isFree == null || filter.isFree === session.isFree) &&
      (!filter.startTime || session.startTime >= filter.startTime) &&
      (!filter.endTime || session.startTime <= filter.endTime);
  });
  const fixtureIds = new Set(fixtures.map((show) => show.id));
  const shows = [...fixtures, ...existing.filter((show) => !fixtureIds.has(show.id))];
  const content = shows.slice(page * size, (page + 1) * size);
  const sort = { empty: true, sorted: false, unsorted: true };
  return {
    content, totalElements: shows.length, totalPages: Math.ceil(shows.length / size),
    size, number: page, numberOfElements: content.length, first: page === 0,
    last: (page + 1) * size >= shows.length, empty: content.length === 0,
    sort, ranking: 0, sessions: [],
    pageable: { sort, offset: page * size, pageSize: size, pageNumber: page, paged: true, unpaged: false },
  };
}

export function mockShowDetail(key: string): ShowResponse | undefined {
  const show = mockShows().find((item) => item.id === key || item.slug === key);
  if (!show) return undefined;
  return {
    ...show, website: '', logoShowUrl: '', shipPrice: 0,
    detailImageUrl: show.detailImageUrl!,
    description: '<p>Show diễn mẫu dùng để kiểm tra giao diện. Không mở bán vé thật.</p>',
    sessions: show.sessions!.map((session) => ({
      ...session, endTime: session.endTime!, seatPlanId: '', edType: EdType.PolicyConfig,
      city: { id: session.cityCode!, code: session.cityCode!, name: session.cityName! },
      status: SessionStatus.Stopped, requireDelivery: false, paymentOffline: false,
      paymentNote: '', orderNote: null, mailPluginTemplate: [], paymentMethods: [],
    })),
  };
}
