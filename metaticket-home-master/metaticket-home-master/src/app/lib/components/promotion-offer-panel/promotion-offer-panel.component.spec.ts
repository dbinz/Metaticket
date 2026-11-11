import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { NEVER, Subject, of, throwError } from 'rxjs';
import { PromotionOfferResponse } from 'src/app/lib/entities';
import { PromotionOfferPanelComponent, PromotionOfferSource } from './promotion-offer-panel.component';

/**
 * Behaviour the booking / accessory pages rely on: lazy single fetch per `source`, hide everything when there is
 * nothing to offer, emit the picked code, and close on Esc / outside press without closing on the code input itself.
 */
describe('PromotionOfferPanelComponent', () => {
  const OFFERS: PromotionOfferResponse[] = [
    {
      code: 'SALE10',
      title: 'Giảm 10% cuối tuần',
      discountType: 'PERCENT',
      discountValue: 10,
      maxDiscountAmount: 50000,
    },
    { code: ' Ship50k ', title: 'Ship50k', discountType: 'AMOUNT', discountValue: 50000, limitedQuantity: true },
  ];

  const fixtures: ComponentFixture<PromotionOfferPanelComponent>[] = [];

  // Every case builds its own spy: the panel shares requests per source *reference*, so reusing one would leak cache.
  function sourceOf(result: ReturnType<PromotionOfferSource>): jasmine.Spy<PromotionOfferSource> {
    return jasmine.createSpy<PromotionOfferSource>('source').and.returnValue(result);
  }

  function create(source: PromotionOfferSource | null): ComponentFixture<PromotionOfferPanelComponent> {
    const fixture = TestBed.createComponent(PromotionOfferPanelComponent);
    fixture.componentRef.setInput('source', source);
    fixture.detectChanges();
    fixtures.push(fixture);
    return fixture;
  }

  function host(fixture: ComponentFixture<PromotionOfferPanelComponent>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function toggleLink(fixture: ComponentFixture<PromotionOfferPanelComponent>): HTMLButtonElement | null {
    return host(fixture).querySelector<HTMLButtonElement>('button[aria-expanded]');
  }

  function cards(fixture: ComponentFixture<PromotionOfferPanelComponent>): HTMLElement[] {
    return Array.from(host(fixture).querySelectorAll<HTMLElement>('li'));
  }

  function pickButton(card: HTMLElement): HTMLButtonElement {
    const button = card.querySelector<HTMLButtonElement>('button');
    if (!button) {
      throw new Error('card without a pick button');
    }
    return button;
  }

  function openPanel(fixture: ComponentFixture<PromotionOfferPanelComponent>): void {
    fixture.componentInstance.open();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PromotionOfferPanelComponent] });
  });

  afterEach(() => {
    fixtures.splice(0).forEach((fixture) => fixture.destroy());
  });

  describe('lazy loading', () => {
    it('does not call source before the panel is opened', () => {
      const source = sourceOf(of(OFFERS));
      const fixture = create(source);

      expect(source.calls.count()).toBe(0);
      expect(toggleLink(fixture)?.textContent?.trim()).toBe('Xem mã ưu đãi');
      expect(cards(fixture).length).toBe(0);
    });

    it('calls source once across repeated open/close and keeps showing the list', () => {
      const source = sourceOf(of(OFFERS));
      const fixture = create(source);

      openPanel(fixture);
      fixture.componentInstance.close();
      fixture.detectChanges();
      expect(cards(fixture).length).toBe(0);
      openPanel(fixture);

      expect(source.calls.count()).toBe(1);
      expect(cards(fixture).length).toBe(2);
    });

    it('drops the cache and fetches again when source changes reference', () => {
      const first = sourceOf(of(OFFERS));
      const second = sourceOf(of([OFFERS[0]]));
      const fixture = create(first);
      openPanel(fixture);

      fixture.componentRef.setInput('source', second);
      fixture.detectChanges();
      expect(cards(fixture).length).withContext('changing source closes the panel').toBe(0);
      openPanel(fixture);

      expect(first.calls.count()).toBe(1);
      expect(second.calls.count()).toBe(1);
      expect(cards(fixture).length).toBe(1);
    });

    it('shares one request between panels bound to the same source (several layouts of one page)', () => {
      const source = sourceOf(of(OFFERS));
      const desktop = create(source);
      const mobile = create(source);

      openPanel(desktop);
      openPanel(mobile);

      expect(source.calls.count()).toBe(1);
      expect(cards(desktop).length).toBe(2);
      expect(cards(mobile).length).toBe(2);
    });

    it('shows a loading label without an empty frame while the request is in flight', () => {
      const fixture = create(sourceOf(new Subject<PromotionOfferResponse[]>()));
      openPanel(fixture);

      expect(toggleLink(fixture)?.textContent?.trim()).toBe('Đang tải mã ưu đãi...');
      expect(host(fixture).textContent).not.toContain('Mỗi đơn áp dụng một mã');
    });
  });

  describe('nothing to offer hides the link', () => {
    type SourceResult = ReturnType<PromotionOfferSource>;
    const unusable: [string, () => SourceResult][] = [
      ['empty list', (): SourceResult => of([])],
      ['HTTP error', (): SourceResult => throwError(() => new Error('403'))],
      ['null body', (): SourceResult => of(null as unknown as PromotionOfferResponse[])],
      ['non-array body', (): SourceResult => of({ code: 'X' } as unknown as PromotionOfferResponse[])],
    ];

    for (const [label, result] of unusable) {
      it(`hides the link and never shows a frame for ${label}`, () => {
        const source = sourceOf(result());
        const fixture = create(source);

        openPanel(fixture);
        expect(toggleLink(fixture)).toBeNull();
        expect(host(fixture).textContent?.trim()).toBe('');

        openPanel(fixture);
        expect(source.calls.count()).withContext('a failed/empty source is not retried').toBe(1);
      });
    }

    it('hides the link when the request times out', fakeAsync(() => {
      const fixture = create(sourceOf(NEVER));
      openPanel(fixture);
      tick(10000);
      fixture.detectChanges();

      expect(toggleLink(fixture)).toBeNull();
      discardPeriodicTasks();
    }));

    it('renders nothing and ignores open() without a source', () => {
      const fixture = create(null);
      openPanel(fixture);

      expect(toggleLink(fixture)).toBeNull();
    });
  });

  describe('picking', () => {
    it('emits the picked code (trimmed) and closes', () => {
      const fixture = create(sourceOf(of(OFFERS)));
      const picked: string[] = [];
      fixture.componentInstance.picked.subscribe((code) => picked.push(code));
      openPanel(fixture);

      pickButton(cards(fixture)[1]).click();
      fixture.detectChanges();

      expect(picked).toEqual(['Ship50k']);
      expect(cards(fixture).length).toBe(0);
    });

    it('blocks picking while disabled but still lists the codes', () => {
      const fixture = create(sourceOf(of(OFFERS)));
      fixture.componentRef.setInput('disabled', true);
      const picked: string[] = [];
      fixture.componentInstance.picked.subscribe((code) => picked.push(code));
      openPanel(fixture);

      const button = pickButton(cards(fixture)[0]);
      button.click();

      expect(button.disabled).toBeTrue();
      expect(picked).toEqual([]);
      expect(cards(fixture).length).toBe(2);
    });

    it('marks the applied code (case-insensitive) and does not re-emit it', () => {
      const fixture = create(sourceOf(of(OFFERS)));
      fixture.componentRef.setInput('appliedCode', 'ship50K');
      openPanel(fixture);

      const applied = pickButton(cards(fixture)[1]);
      const other = pickButton(cards(fixture)[0]);
      expect(applied.textContent?.trim()).toBe('Đang áp dụng');
      expect(applied.disabled).toBeTrue();
      expect(other.textContent?.trim()).toBe('Dùng mã');
      expect(other.disabled).toBeFalse();
    });

    it('finds a loaded offer by code for rejection messages', () => {
      const fixture = create(sourceOf(of(OFFERS)));
      expect(fixture.componentInstance.findOffer('sale10')).withContext('not loaded yet').toBeUndefined();

      openPanel(fixture);

      expect(fixture.componentInstance.findOffer('  sale10 ')?.code).toBe('SALE10');
      expect(fixture.componentInstance.findOffer('NOPE')).toBeUndefined();
    });
  });

  describe('rendering', () => {
    it('renders the code, a distinct title, the discount, conditions and the one-code note', () => {
      const fixture = create(sourceOf(of(OFFERS)));
      openPanel(fixture);
      const [first, second] = cards(fixture).map((card) => card.textContent ?? '');

      expect(host(fixture).textContent).toContain('Mỗi đơn áp dụng một mã');
      expect(first).toContain('SALE10');
      expect(first).toContain('Giảm 10% cuối tuần');
      expect(first).toContain('-10%');
      expect(first).toContain('Giảm tối đa 50.000₫');
      // Accessory titles repeat the code; it must not be printed twice.
      expect(second.split('Ship50k').length - 1).toBe(1);
      expect(second).toContain('Số lượng có hạn');
    });

    it('fits 500 offers with 200-char codes/titles into a 358px column (390px phone minus gutters)', () => {
      const many: PromotionOfferResponse[] = Array.from({ length: 500 }, (_item, index) => ({
        code: `${'X'.repeat(200)}${index}`,
        title: 'T'.repeat(200),
        discountType: 'AMOUNT',
        discountValue: 9_000_000_000_000,
        minOrderAmount: 1_000_000_000_000,
        limitedQuantity: true,
        startAt: 0,
        endAt: 253402300799000,
      }));
      const fixture = create(sourceOf(of(many)));
      const element = host(fixture);
      element.style.display = 'block';
      element.style.width = '358px';

      const startedAt = performance.now();
      openPanel(fixture);
      const elapsedMs = performance.now() - startedAt;

      const rendered = cards(fixture);
      expect(rendered.length).toBe(500);
      expect(elapsedMs).withContext('render time of 500 cards').toBeLessThan(2000);
      expect(element.scrollWidth).toBeLessThanOrEqual(358);
      expect(rendered.filter((card) => card.scrollWidth > card.clientWidth).length)
        .withContext('cards wider than 358px')
        .toBe(0);
      // The list scrolls inside a bounded box instead of pushing the checkout button off screen.
      const scrollBox = rendered[0].closest('ul')?.parentElement;
      expect(scrollBox?.clientHeight).toBeLessThanOrEqual(384);
      expect(scrollBox && scrollBox.scrollHeight > scrollBox.clientHeight).toBeTrue();
    });

    it('prints markup from the API as text', () => {
      const hostile: PromotionOfferResponse = {
        code: '<b>x</b>',
        title: '<script>alert(1)</script>',
        discountType: 'PERCENT',
        discountValue: 5,
      };
      const fixture = create(sourceOf(of([hostile])));
      openPanel(fixture);

      expect(host(fixture).querySelector('script, b')).toBeNull();
      expect(host(fixture).textContent).toContain('<script>alert(1)</script>');
      expect(host(fixture).textContent).toContain('<b>x</b>');
    });
  });

  describe('closing', () => {
    let anchor: HTMLElement;

    beforeEach(() => {
      anchor = document.createElement('div');
      document.body.appendChild(anchor);
    });

    afterEach(() => {
      anchor.remove();
    });

    function openWithAnchor(): ComponentFixture<PromotionOfferPanelComponent> {
      const fixture = create(sourceOf(of(OFFERS)));
      fixture.componentRef.setInput('anchor', anchor);
      openPanel(fixture);
      return fixture;
    }

    // pointerdown, not mousedown: iOS WebKit fires no mouse events when tapping a non-clickable area.
    function pressOn(target: EventTarget, fixture: ComponentFixture<PromotionOfferPanelComponent>): void {
      target.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      fixture.detectChanges();
    }

    it('closes on Escape', () => {
      const fixture = openWithAnchor();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();

      expect(cards(fixture).length).toBe(0);
    });

    it('closes on a press outside the panel and the anchor', () => {
      const fixture = openWithAnchor();
      pressOn(document.body, fixture);

      expect(cards(fixture).length).toBe(0);
    });

    it('keeps an outside button in place until its click has been handled', () => {
      const fixture = openWithAnchor();
      const button = document.createElement('button');
      document.body.appendChild(button);
      let cardsWhenClicked = -1;
      button.addEventListener('click', () => {
        cardsWhenClicked = cards(fixture).length;
      });
      try {
        pressOn(button, fixture);
        expect(cards(fixture).length).toBe(2);

        button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        fixture.detectChanges();

        expect(cardsWhenClicked).toBe(2);
        expect(cards(fixture).length).toBe(0);
      } finally {
        button.remove();
      }
    });

    it('stays open on a press inside the anchor (the code input) or inside the panel', () => {
      const fixture = openWithAnchor();
      pressOn(anchor, fixture);
      pressOn(host(fixture), fixture);
      pressOn(cards(fixture)[0], fixture);

      expect(cards(fixture).length).toBe(2);
    });
  });

  it('fetches again for a new panel when the only panel of a source was destroyed mid-request', () => {
    const pending: Subject<PromotionOfferResponse[]>[] = [];
    const source = jasmine
      .createSpy<PromotionOfferSource>('source')
      .and.callFake((): Subject<PromotionOfferResponse[]> => {
        const request = new Subject<PromotionOfferResponse[]>();
        pending.push(request);
        return request;
      });
    const first = create(source);
    openPanel(first);
    first.destroy();

    // e.g. the mobile summary bar is re-created by *ngIf while the first request was still in flight
    const second = create(source);
    openPanel(second);
    expect(source.calls.count()).withContext('the cancelled request must not stay cached').toBe(2);

    pending[1].next(OFFERS);
    pending[1].complete();
    second.detectChanges();

    expect(cards(second).length).toBe(2);
    expect(pending[0].observed).toBeFalse();
  });

  it('unsubscribes from an in-flight request on destroy', () => {
    const pending = new Subject<PromotionOfferResponse[]>();
    const fixture = create(sourceOf(pending));
    openPanel(fixture);
    expect(pending.observed).toBeTrue();

    fixture.destroy();

    expect(pending.observed).toBeFalse();
    expect(() => pending.next(OFFERS)).not.toThrow();
  });
});
