/* eslint-disable @typescript-eslint/no-unsafe-call */
import { CommonModule, NgIf } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnInit,
  Output,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzInputDirective, NzInputGroupComponent, NzInputGroupWhitSuffixOrPrefixDirective } from 'ng-zorro-antd/input';
import { DropdownMenuItem } from '../../dto';
import { AccessoryShowResponse, SearchShowResponse } from 'src/app/lib/entities';
import { NavigationService, ShowService } from 'src/app/lib/services';
import { SearchSuggestGroups, SearchService } from 'src/app/lib/services/search/search.service';
import { routeName } from 'src/app/lib/constants';
import { flattenSuggestGroups, hasSuggestContent, productDetailPath, showDetailPath } from 'src/app/lib/utils';
import { newsArticleHref } from 'src/app/lib/constants/news.constants';
import { ImageComponent } from '../image/image.component';
import { SearchSuggestPanelComponent } from '../search-suggest-panel/search-suggest-panel.component';

@Component({
  selector: 'app-input-search',
  standalone: true,
  imports: [
    NzInputDirective,
    NzInputGroupComponent,
    NzInputGroupWhitSuffixOrPrefixDirective,
    ReactiveFormsModule,
    NgIf,
    FormsModule,
    NzDropDownModule,
    CommonModule,
    ImageComponent,
    SearchSuggestPanelComponent,
  ],
  templateUrl: './input-search.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InputSearchComponent implements OnInit {
  @Input() placeholder = '';
  @Input() value = '';
  @Input() fontSize: 'sm' | 'default' | 'lg' = 'default';
  @Input() border: 'none' | 'default' = 'default';
  @Input() type: 'text' | 'password' = 'text';
  @Input() suffixIcon?: TemplateRef<void>;
  /** Empty-keyword state ("Top sự kiện nổi bật") — unrelated to the 3-group suggest state below. */
  @Input() highlightResults: SearchShowResponse[] = [];
  @Input() suggestGroups: SearchSuggestGroups = SearchService.emptySuggestGroups();
  @Output() handlerOnChanged = new EventEmitter<string>();
  @Output() handlerOnBlur = new EventEmitter<void>();
  @Output() handlerOnFocus = new EventEmitter<void>();
  @Input() showResults = false;
  @Input() data: DropdownMenuItem[] = [];
  @Output() handlerOnSelected = new EventEmitter<DropdownMenuItem>();
  public textSize = '';
  public inputBorder = '';
  activeIndex: number | null = null;
  @ViewChild('content')
  content?: ElementRef;

  private _getTextSize(): void {
    this.textSize = {
      default: 'text-base font-medium',
      sm: 'text-sm leading-6 font-medium',
      lg: 'text-lg leading-6 font-medium',
    }[this.fontSize];
  }
  constructor(
    private _showService: ShowService,
    private _navigation: NavigationService,
    private _cdr: ChangeDetectorRef,
  ) {}

  /** Closes the panel on outside click; opening is owned by (focus) so a click inside never re-opens it. */
  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: EventTarget | null): void {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    if (this.showResults && !this.content?.nativeElement.contains(target)) {
      this.showResults = false;
      this.activeIndex = null;
      this._cdr.markForCheck();
    }
  }

  private _getInputBorder(): void {
    this.inputBorder = {
      default: 'border border-gray-C9C9C9',
      none: 'border-0',
    }[this.border];
  }

  onInputChange(): void {
    this.activeIndex = null;
    this.handlerOnChanged.emit(this.value);
  }

  onBlur(): void {
    this.handlerOnBlur.emit();
  }

  onFocus(): void {
    this.handlerOnFocus.emit();
    this.showResults = true;
  }

  ngOnInit(): void {
    this._getTextSize();
    this._getInputBorder();
  }

  onSelected(item: DropdownMenuItem): void {
    this.handlerOnSelected?.emit(item);
  }

  /** Whether the 3-group suggest panel has anything worth rendering — see hasSuggestContent(). */
  get showSuggestPanel(): boolean {
    return hasSuggestContent(this.suggestGroups);
  }

  goToDetail(show: SearchShowResponse): void {
    this._navigation.navigateTo(showDetailPath(show));
    this._showService.saveToLocalStorage(show);
    this._closePanel();
  }

  goToProduct(show: AccessoryShowResponse): void {
    this._navigation.navigateTo(productDetailPath(show));
    this._closePanel();
  }

  onEnter(): void {
    if (this.activeIndex != null) {
      const flat = flattenSuggestGroups(this.suggestGroups)[this.activeIndex];
      if (flat) {
        this._pickFlat(flat);
        return;
      }
    }
    this.onViewAll();
  }

  /** "Xem tất cả kết quả cho ..." — always goes to /search, regardless of arrow-key selection. */
  onViewAll(): void {
    const trimmed = this.value.trim();
    if (!trimmed) {
      return;
    }
    this._navigation.navigateTo(routeName.search, { queryParams: { key: trimmed } });
    this._closePanel();
  }

  onArrowDown(event: Event): void {
    event.preventDefault();
    this._moveActive(1);
  }

  onArrowUp(event: Event): void {
    event.preventDefault();
    this._moveActive(-1);
  }

  onEscape(): void {
    this._closePanel();
  }

  private _moveActive(delta: number): void {
    const total = flattenSuggestGroups(this.suggestGroups).length;
    if (total === 0) {
      return;
    }
    const current = this.activeIndex ?? -1;
    this.activeIndex = (current + delta + total) % total;
  }

  private _pickFlat(flat: ReturnType<typeof flattenSuggestGroups>[number]): void {
    if (flat.kind === 'event') {
      this.goToDetail(flat.item);
    } else if (flat.kind === 'product') {
      this.goToProduct(flat.item);
    } else {
      window.location.href = newsArticleHref(flat.item.slug);
    }
  }

  private _closePanel(): void {
    this.showResults = false;
    this.activeIndex = null;
  }
}
