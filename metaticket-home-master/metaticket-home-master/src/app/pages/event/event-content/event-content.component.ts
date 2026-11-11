import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Subscription, expand, reduce } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { endOfDay, startOfDay } from 'date-fns';
import { appIcons } from 'src/app/lib/theme';
import { DropDownDatePicker, DropdownMenuButton, DropdownMenuItem } from 'src/app/lib/dto';
import { CityResponse, SearchShowResponse } from 'src/app/lib/entities';
import {
  DropdownButtonComponent,
  GridEventTicketComponent,
  ImageComponent,
  SingleDropdownComponent,
} from 'src/app/lib/components';
import { ShowService } from 'src/app/lib/services';
import { SearchShowFilter } from 'src/app/lib/entities/request/show';
import { DatePickerComponent } from 'src/app/lib/components/date-picker/date-picker.component';
import { HomeSearchQuery, HomeSearchService } from 'src/app/lib/stores';
import { groupEvents } from './event-groups';

@Component({
  selector: 'app-event-content',
  standalone: true,
  imports: [
    ImageComponent,
    DropdownButtonComponent,
    GridEventTicketComponent,
    SingleDropdownComponent,
    DatePickerComponent,
  ],
  templateUrl: './event-content.component.html',
  styleUrl: './event-content.component.css',
})
export class EventContentComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  protected readonly appIcons = appIcons;
  readonly priceTypeTicket: DropdownMenuItem[] = [
    { title: 'Mất phí', value: 'fee' },
    { title: 'Miễn phí', value: 'free' },
  ];
  upcoming: SearchShowResponse[] = [];
  past: SearchShowResponse[] = [];
  readonly pageSize = 6;
  upcomingLimit = this.pageSize;
  pastLimit = this.pageSize;
  loading = false;
  error = false;
  choseCity: CityResponse[] = [];
  chosePriceType: DropdownMenuItem | null | undefined;
  choseDate: DropDownDatePicker | null | undefined;
  type = '';
  listCityData: DropdownMenuButton[] = [{ title: 'Tất cả địa điểm', value: 'all', check: false }];
  choseCityNav: CityResponse | null = null;

  constructor(
    private _router: Router,
    private _route: ActivatedRoute,
    private _showService: ShowService,
    private _homeSearchQuery: HomeSearchQuery,
    private _homeSearchService: HomeSearchService,
  ) {
    this._route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this._homeSearchService.onSelectEvent({ title: params.get('name') ?? '', value: params.get('id') ?? '' });
      this.type = params.get('id') ?? '';
      this.reload();
    });
    this._homeSearchQuery
      .select('citySelected')
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe((city) => {
        if (this._router.url.includes('su-kien')) {
          this.choseCityNav = city ? { id: city.value, name: city.title, code: city.value } : null;
          this.reload();
        }
      });
  }

  ngOnInit(): void {
    this._showService
      .getListCity()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (cities) => {
          this.listCityData = [
            ...this.listCityData,
            ...cities.map((city) => ({ title: city.name, value: city.code, check: false })),
          ];
        },
      });
  }

  reload(): void {
    this.request?.unsubscribe();
    this.loading = true;
    this.error = false;
    this.upcoming = [];
    this.past = [];
    this.upcomingLimit = this.pastLimit = this.pageSize;
    const cities = this.choseCityNav ? [...this.choseCity, this.choseCityNav] : this.choseCity;
    const filter: SearchShowFilter = {
      page: 0,
      size: 60,
      eventCategoryCode: this.type,
      cities: [...new Set(cities.map((city) => city.code))],
    };
    if (this.chosePriceType) filter.isFree = this.chosePriceType.value !== 'fee';
    if (this.choseDate?.fromDate) {
      filter.startTime = startOfDay(this.choseDate.fromDate).getTime();
      filter.endTime = endOfDay(this.choseDate.toDate ?? this.choseDate.fromDate).getTime();
    }
    // Group the complete filtered result, not just a page: later pages can still contain active shows.
    let page = 0;
    this.request = this._showService
      .searchShowByFilter(filter)
      .pipe(
        expand((result) => {
          if (++page >= result.totalPages) return EMPTY;
          // The API clamps page numbers to 150; avoid repeatedly fetching that page for larger catalogs.
          if (page > 150) throw new Error('Event results exceed the API pagination window');
          return this._showService.searchShowByFilter({ ...filter, page });
        }),
        reduce((shows, result) => [...shows, ...result.content], [] as SearchShowResponse[]),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (shows) => {
          const groups = groupEvents(shows);
          this.upcoming = groups.upcoming;
          this.past = groups.past;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
          this.error = true;
        },
      });
  }

  selectItemEvent(items: DropdownMenuButton[]): void {
    this.choseCity = items
      .filter((item) => item.value !== 'all')
      .map((item) => ({ id: item.value, name: item.title, code: item.value }));
    this.reload();
  }
  selectItemPriceTicket(item: DropdownMenuItem): void {
    this.chosePriceType = item;
    this.reload();
  }
  selectDatepicker(date: DropDownDatePicker): void {
    this.choseDate = date;
    this.reload();
  }
}
