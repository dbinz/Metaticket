import { NgClass, NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { AccessoryShowResponse, SearchShowResponse } from 'src/app/lib/entities';
import { SearchSuggestGroups } from 'src/app/lib/services/search/search.service';
import { newsArticleHref } from 'src/app/lib/constants/news.constants';
import { appImages } from 'src/app/lib/theme';
import { ImageComponent } from '../image/image.component';

/**
 * Dumb, presentational 3-column (Sự kiện / Sản phẩm / Tin tức) suggestion body, shared by the
 * desktop and mobile search inputs so the layout is defined once. Navigation on Sự kiện/Sản phẩm
 * clicks is the caller's responsibility (`pickEvent`/`pickProduct`); Tin tức links are plain
 * `<a href>` since article pages are server-rendered HTML, not Angular routes.
 */
@Component({
  selector: 'app-search-suggest-panel',
  standalone: true,
  imports: [NgIf, NgFor, NgClass, ImageComponent],
  templateUrl: './search-suggest-panel.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchSuggestPanelComponent {
  @Input({ required: true }) groups!: SearchSuggestGroups;
  @Input() keyword = '';
  /** Index into the flattened (events → products → news) list, for arrow-key highlighting. */
  @Input() activeIndex: number | null = null;
  @Output() pickEvent = new EventEmitter<SearchShowResponse>();
  @Output() pickProduct = new EventEmitter<AccessoryShowResponse>();
  @Output() viewAll = new EventEmitter<void>();

  protected readonly newsArticleHref = newsArticleHref;

  get hasAnyResult(): boolean {
    return (
      this.groups.events.items.length > 0 || this.groups.products.items.length > 0 || this.groups.news.items.length > 0
    );
  }

  get isAnyLoading(): boolean {
    return this.groups.events.loading || this.groups.products.loading || this.groups.news.loading;
  }

  get isAllFailed(): boolean {
    return this.groups.events.failed && this.groups.products.failed && this.groups.news.failed;
  }

  isEventActive(index: number): boolean {
    return this.activeIndex === index;
  }

  isProductActive(index: number): boolean {
    return this.activeIndex === this.groups.events.items.length + index;
  }

  isNewsActive(index: number): boolean {
    return this.activeIndex === this.groups.events.items.length + this.groups.products.items.length + index;
  }

  productImage(item: AccessoryShowResponse): string {
    return item.bannerImageUrl || appImages.imgMetaTicketThumbnail;
  }

  trackById(_index: number, item: { id: string }): string {
    return item.id;
  }
}
