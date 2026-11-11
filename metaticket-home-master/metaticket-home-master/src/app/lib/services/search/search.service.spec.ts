import { HttpClientTestingModule, HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { SearchSuggestGroups, SearchService } from './search.service';

describe('SearchService HTTP result states', () => {
  let service: SearchService;
  let http: HttpTestingController;
  let latest: SearchSuggestGroups;

  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 3, empty: true };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(SearchService);
    http = TestBed.inject(HttpTestingController);
    latest = SearchService.emptySuggestGroups();
  });

  afterEach(() => http.verify());

  function start(): { events: TestRequest; products: TestRequest; news: TestRequest } {
    service.suggest('tinh hoa bac bo').subscribe((groups) => (latest = groups));
    const events = http.expectOne((request) => request.url.endsWith('/public/metasearch/shows/suggestion'));
    const products = http.expectOne((request) => request.url.endsWith('/public/accessory/show/search'));
    const news = http.expectOne((request) => request.url.endsWith('/public/news/search'));
    expect(events.request.params.get('keyword')).toBe('tinh hoa bac bo');
    expect(events.request.params.get('size')).toBe('5');
    expect(products.request.params.get('size')).toBe('3');
    expect(news.request.params.get('size')).toBe('3');
    return { events, products, news };
  }

  it('HTTP 200 with empty pages leaves all three sources empty without failure', () => {
    const requests = start();
    requests.events.flush(emptyPage);
    requests.products.flush(emptyPage);
    requests.news.flush(emptyPage);
    for (const group of [latest.events, latest.products, latest.news]) {
      expect(group.items).toEqual([]);
      expect(group.total).toBe(0);
      expect(group.loading).toBeFalse();
      expect(group.failed).toBeFalse();
    }
  });

  for (const statusCode of [404, 500]) {
    it(`HTTP ${statusCode} in products stays failed while other sources retain their results`, () => {
      const requests = start();
      requests.events.flush({ ...emptyPage, content: [{ id: 'event-target' }], totalElements: 1 });
      requests.products.flush({}, { status: statusCode, statusText: 'Request failed' });
      requests.news.flush({
        ...emptyPage,
        content: [{ id: 'news-target', title: 'Văn hóa', summary: '' }],
        totalElements: 1,
      });
      expect(latest.products.failed).toBeTrue();
      expect(latest.events.items.map((item) => item.id)).toEqual(['event-target']);
      expect(latest.news.items.map((item) => item.id)).toEqual(['news-target']);
      expect(latest.events.failed).toBeFalse();
      expect(latest.news.failed).toBeFalse();
    });
  }

  it('a timed out source does not delay or erase the other results', fakeAsync(() => {
    const requests = start();
    requests.events.flush({ ...emptyPage, content: [{ id: 'event-target' }], totalElements: 1 });
    requests.products.flush(emptyPage);
    expect(latest.events.items.map((item) => item.id)).toEqual(['event-target']);
    expect(latest.news.loading).toBeTrue();
    tick(8001);
    expect(latest.news.failed).toBeTrue();
    expect(latest.news.loading).toBeFalse();
    expect(latest.events.items.map((item) => item.id)).toEqual(['event-target']);
    expect(requests.news.cancelled).toBeTrue();
  }));
});
