import { AfterViewInit, Component } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { AdminService, OrderService } from '@admin.portal/@core/metaticket/data.service';
import { OrderPromotionModel, ShowSessionDraftPromotionModel } from '@admin.portal/@core/metaticket/model';
import { getErrorMessageFromResponse } from '@admin.portal/@core/metaticket/utils/error.response.utils';
import { tap } from 'rxjs/operators';

export enum ApplyDiscountOnType {
    TotalOrder = 'TotalOrder',
    MatchedItems = 'MatchedItems',
}

@Component({
    templateUrl: './edit_promotion_dialog.component.html',
    styleUrls: ['edit_promotion_dialog.component.scss'],
    selector: 'edit-promotion-dialog',
})
export class EditPromotionDialogComponent implements AfterViewInit {
    sessionDraftId?: string;
    showId?: string;
    sessionId?: string;
    adminService: AdminService;
    orderService: OrderService;
    draftPromotion?: ShowSessionDraftPromotionModel;
    promotion?: OrderPromotionModel;
    onUpdateDraftSuccess: (model: ShowSessionDraftPromotionModel) => void;
    onAddDraftSuccess: (model: ShowSessionDraftPromotionModel) => void;
    onUpdateSuccess: () => void;
    promotionValue: string = null;

    promotionForm = this.formBuilder.group({
        name: new FormControl('', Validators.required),
        items: this.formBuilder.array([]),
    });

    loading: number = 0;

    constructor(
        private formBuilder: FormBuilder,
        private toastrService: NbToastrService,
    ) {}

    ngAfterViewInit(): void {
        if (this.draftPromotion) {
            this.promotionForm.patchValue({ name: this.draftPromotion.name });
            const promotionEncode = JSON.parse(this.draftPromotion.data);
            for (let i = 0; i < promotionEncode.length; i++) {
                switch (promotionEncode[i].type) {
                    case 'discountByDate':
                        const date = new Date(promotionEncode[i].date);
                        this.addDiscountByDatePromotion(
                            promotionEncode[i].name,
                            date,
                            promotionEncode[i].discountValue,
                            promotionEncode[i].discountOption,
                        );
                        break;
                    case 'discountByTicketAmount':
                        this.addDiscountByTicketAmountPromotion(
                            promotionEncode[i].ticketAmountMin,
                            promotionEncode[i].ticketAmountMax,
                            promotionEncode[i].discountValue,
                            promotionEncode[i].discountOption,
                            promotionEncode[i].needCheckQuantity,
                            promotionEncode[i].originalQuantity,
                            promotionEncode[i].quantityOption,
                        );
                        break;
                    case 'discountByPromotionCode':
                        this.addDiscountByPromotionCode(
                            promotionEncode[i].name,
                            promotionEncode[i].code,
                            promotionEncode[i].discountValue,
                            promotionEncode[i].discountOption,
                            promotionEncode[i].needCheckQuantity,
                            promotionEncode[i].originalQuantity,
                        );
                        break;
                    case 'discountByMultiPromotionCode':
                        this.addDiscountByMultiPromotionCode(
                            promotionEncode[i].name,
                            promotionEncode[i].codes,
                            promotionEncode[i].discountValue,
                            promotionEncode[i].discountOption,
                            promotionEncode[i].needCheckQuantity,
                            promotionEncode[i].originalQuantity,
                        );
                        break;
                    case 'discountByPriceCategoryAmount':
                        this.addDiscountByPriceCategoryAmountPromotion(
                            promotionEncode[i].discountValue,
                            promotionEncode[i].discountOption,
                            promotionEncode[i].priceCatConditions,
                            promotionEncode[i].applyDiscountOn,
                        );
                        break;
                    case 'maxDiscountAmount':
                        this.addMaxDiscountAmount(
                            promotionEncode[i].maxDiscountTotal,
                            promotionEncode[i].enablePerType,
                            promotionEncode[i].maxDiscountByDate,
                            promotionEncode[i].maxDiscountByTicketAmount,
                            promotionEncode[i].maxDiscountByPromotionCode,
                        );
                        break;
                }
            }
        } else if (this.promotion) {
            this.promotionForm.patchValue({ name: 'Khuyến mãi' });
            const promotionEncode = JSON.parse(this.promotion.configuration);
            console.log(promotionEncode);
            if (promotionEncode.promotionCodes) {
                for (let i = 0; i < promotionEncode.promotionCodes.length; i++) {
                    this.addDiscountByPromotionCode(
                        promotionEncode.promotionCodes[i].name,
                        promotionEncode.promotionCodes[i].code,
                        promotionEncode.promotionCodes[i].discountPrice
                            ? promotionEncode.promotionCodes[i].discountPrice
                            : promotionEncode.promotionCodes[i].discountPercent,
                        promotionEncode.promotionCodes[i].discountPrice ? 'DiscountPrice' : 'DiscountPercent',
                        promotionEncode.promotionCodes[i].needCheckQuantity,
                        promotionEncode.promotionCodes[i].originalQuantity,
                    );
                }
            }
            if (promotionEncode.discountByDates) {
                for (let i = 0; i < promotionEncode.discountByDates.length; i++) {
                    const date = new Date(promotionEncode.discountByDates[i].date);
                    this.addDiscountByDatePromotion(
                        promotionEncode.discountByDates[i].name,
                        date,
                        promotionEncode.discountByDates[i].discountPrice
                            ? promotionEncode.discountByDates[i].discountPrice
                            : promotionEncode.discountByDates[i].discountPercent,
                        promotionEncode.discountByDates[i].discountPrice ? 'DiscountPrice' : 'DiscountPercent',
                    );
                }
            }
            if (promotionEncode.discountByTicketAmounts) {
                for (let i = 0; i < promotionEncode.discountByTicketAmounts.length; i++) {
                    this.addDiscountByTicketAmountPromotion(
                        promotionEncode.discountByTicketAmounts[i].ticketAmountBelow,
                        promotionEncode.discountByTicketAmounts[i].ticketAmountAbove,
                        promotionEncode.discountByTicketAmounts[i].discountPrice
                            ? promotionEncode.discountByTicketAmounts[i].discountPrice
                            : promotionEncode.discountByTicketAmounts[i].discountPercent,
                        promotionEncode.discountByTicketAmounts[i].discountPrice ? 'DiscountPrice' : 'DiscountPercent',
                        promotionEncode.discountByTicketAmounts[i].needCheckQuantity,
                        promotionEncode.discountByTicketAmounts[i].originalQuantity,
                        promotionEncode.discountByTicketAmounts[i].quantityType,
                    );
                }
            }
            if (promotionEncode.discountByPriceCategoryAmounts) {
                for (let i = 0; i < promotionEncode.discountByPriceCategoryAmounts.length; i++) {
                    this.addDiscountByPriceCategoryAmountPromotion(
                        promotionEncode.discountByPriceCategoryAmounts[i].discountPrice
                            ? promotionEncode.discountByPriceCategoryAmounts[i].discountPrice
                            : promotionEncode.discountByPriceCategoryAmounts[i].discountPercent,
                        promotionEncode.discountByPriceCategoryAmounts[i].discountPrice ? 'DiscountPrice' : 'DiscountPercent',
                        promotionEncode.discountByPriceCategoryAmounts[i].conditions,
                        promotionEncode.discountByPriceCategoryAmounts[i].applyDiscountOn,
                    );
                }
            }
            if (promotionEncode.maxDiscountAmount) {
                const maxConfig = promotionEncode.maxDiscountAmount;
                const enablePerType = !!(maxConfig.maxDiscountPerType && Object.keys(maxConfig.maxDiscountPerType).length > 0);
                this.addMaxDiscountAmount(
                    maxConfig.maxDiscountTotal || null,
                    enablePerType,
                    maxConfig.maxDiscountPerType?.discountByDate || null,
                    maxConfig.maxDiscountPerType?.discountByTicketAmount || null,
                    maxConfig.maxDiscountPerType?.promotionCode || null,
                );
            }
        }
    }

    getItems() {
        return (this.promotionForm.get('items') as FormArray).controls as FormGroup[];
    }

    removeItem(index: number) {
        (this.promotionForm.get('items') as FormArray).removeAt(index);
    }

    get name() {
        return this.promotionForm.get('name');
    }

    onPromotionItemChange(value: string) {
        this.promotionValue = value;
    }
    onAddPromotionClick() {
        switch (this.promotionValue) {
            case 'discountByDate':
                this.addDiscountByDatePromotion('', new Date(), 0, 'DiscountPercent');
                break;
            case 'discountByTicketAmount':
                this.addDiscountByTicketAmountPromotion(0, null, 0, 'DiscountPercent', false, 0, 'Ticket');
                break;
            case 'discountByPromotionCode':
                this.addDiscountByPromotionCode('', '', 0, 'DiscountPercent', false, 0);
                break;
            case 'discountByMultiPromotionCode':
                this.addDiscountByMultiPromotionCode('', '', 0, 'DiscountPercent', false, 0);
                break;
            case 'discountByPriceCategoryAmount':
                this.addDiscountByPriceCategoryAmountPromotion(0, 'DiscountPercent', [], ApplyDiscountOnType.TotalOrder);
                break;
            case 'maxDiscountAmount':
                this.addMaxDiscountAmount(null, false, null, null, null);
                break;
        }
    }
    addDiscountByDatePromotion(name: string, date: Date, discountValue: number, discountOption: string) {
        (<FormArray>this.promotionForm.controls['items']).push(
            this.formBuilder.group({
                type: new FormControl('discountByDate', Validators.required),
                name: new FormControl(name, Validators.required),
                date: new FormControl(date),
                discountValue: new FormControl(discountValue, Validators.required),
                discountOption: new FormControl(discountOption, Validators.required),
            }),
        );
    }

    addDiscountByTicketAmountPromotion(
        ticketAmountMin: number,
        ticketAmountMax: number | null,
        discountValue: number,
        discountOption: string,
        needCheckQuantity: boolean,
        originalQuantity: number,
        quantityOption: string,
    ) {
        (<FormArray>this.promotionForm.controls['items']).push(
            this.formBuilder.group({
                type: new FormControl('discountByTicketAmount', Validators.required),
                ticketAmountMin: new FormControl(ticketAmountMin, Validators.required),
                ticketAmountMax: new FormControl(ticketAmountMax),
                discountOption: new FormControl(discountOption),
                discountValue: new FormControl(discountValue, Validators.required),
                needCheckQuantity: new FormControl(needCheckQuantity),
                originalQuantity: new FormControl(originalQuantity),
                quantityOption: new FormControl(quantityOption),
            }),
        );
    }

    addDiscountByPriceCategoryAmountPromotion(
        discountValue: number,
        discountOption: string,
        priceCatConditions: any[],
        applyDiscountOn: string = ApplyDiscountOnType.TotalOrder,
    ) {
        const conditionsArray = this.formBuilder.array<FormGroup>([]);
        if (priceCatConditions && priceCatConditions.length > 0) {
            priceCatConditions.forEach((c) => {
                conditionsArray.push(
                    this.formBuilder.group({
                        priceCat: new FormControl(c.priceCat, Validators.required),
                        minQuantity: new FormControl(c.minQuantity, Validators.required),
                        maxQuantity: new FormControl(c.maxQuantity),
                    }),
                );
            });
        } else {
            conditionsArray.push(
                this.formBuilder.group({
                    priceCat: new FormControl('', Validators.required),
                    minQuantity: new FormControl(0, Validators.required),
                    maxQuantity: new FormControl(null),
                }),
            );
        }

        (<FormArray>this.promotionForm.controls['items']).push(
            this.formBuilder.group({
                type: new FormControl('discountByPriceCategoryAmount', Validators.required),
                discountOption: new FormControl(discountOption),
                discountValue: new FormControl(discountValue, Validators.required),
                priceCatConditions: conditionsArray,
                applyDiscountOn: new FormControl(applyDiscountOn || ApplyDiscountOnType.TotalOrder),
            }),
        );
    }

    addDiscountByPromotionCode(
        name: string,
        code: string,
        discountValue: number,
        discountOption: string,
        needCheckQuantity: boolean,
        originalQuantity: number,
    ) {
        (<FormArray>this.promotionForm.controls['items']).push(
            this.formBuilder.group({
                type: new FormControl('discountByPromotionCode', Validators.required),
                name: new FormControl(name, Validators.required),
                code: new FormControl(code, Validators.required),
                discountOption: new FormControl(discountOption),
                discountValue: new FormControl(discountValue, Validators.required),
                needCheckQuantity: new FormControl(needCheckQuantity),
                originalQuantity: new FormControl(originalQuantity),
            }),
        );
    }

    addDiscountByMultiPromotionCode(
        name: string,
        codes: string,
        discountValue: number,
        discountOption: string,
        needCheckQuantity: boolean,
        originalQuantity: number,
    ) {
        (<FormArray>this.promotionForm.controls['items']).push(
            this.formBuilder.group({
                type: new FormControl('discountByMultiPromotionCode', Validators.required),
                name: new FormControl(name, Validators.required),
                codes: new FormControl(codes, Validators.required),
                discountOption: new FormControl(discountOption),
                discountValue: new FormControl(discountValue, Validators.required),
                needCheckQuantity: new FormControl(needCheckQuantity),
                originalQuantity: new FormControl(originalQuantity),
            }),
        );
    }

    addMaxDiscountAmount(
        maxDiscountTotal: number | null,
        enablePerType: boolean,
        maxDiscountByDate: number | null,
        maxDiscountByTicketAmount: number | null,
        maxDiscountByPromotionCode: number | null,
    ) {
        (<FormArray>this.promotionForm.controls['items']).push(
            this.formBuilder.group({
                type: new FormControl('maxDiscountAmount', Validators.required),
                maxDiscountTotal: new FormControl(maxDiscountTotal),
                enablePerType: new FormControl(enablePerType),
                maxDiscountByDate: new FormControl(maxDiscountByDate),
                maxDiscountByTicketAmount: new FormControl(maxDiscountByTicketAmount),
                maxDiscountByPromotionCode: new FormControl(maxDiscountByPromotionCode),
            }),
        );
    }

    onSubmit() {
        if (this.promotionForm.invalid) {
            this.promotionForm.markAsDirty();
            this.toastrService.show(
                'Cập nhật thông tin khuyến mãi thất bại, vui lòng kiểm tra lại thông tin nhập',
                'Cập nhật thông tin khuyến mãi thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }
        if (this.draftPromotion) {
            this.loading += 1;
            this.adminService
                .updateShowSessionDraftPromotions$(
                    this.draftPromotion.id,
                    this.name.getRawValue(),
                    JSON.stringify(this.promotionForm.get('items').getRawValue()),
                )
                .pipe(
                    tap(
                        e => {
                            this.loading -= 1;
                        },
                        e => {
                            this.loading -= 1;
                        },
                    ),
                )
                .subscribe(
                    sessionDraftPromotionModel => {
                        this.onUpdateDraftSuccess(sessionDraftPromotionModel);
                        this.toastrService.show(
                            'Cập nhật thông tin khuyến mãi thành công',
                            'Cập nhật thông tin khuyến mãi thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(
                            'Cập nhật thông tin khuyến mãi thất bại',
                            'Cập nhật thông tin khuyến mãi thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                );
        } else if (this.promotion) {
            this.loading += 1;
            this.orderService
                .updatePromotion(
                    this.showId,
                    this.sessionId,
                    this.promotion.id,
                    this.promotion.pluginName,
                    JSON.stringify(this.promotionForm.get('items').getRawValue()),
                )
                .pipe(
                    tap(
                        e => {
                            this.loading -= 1;
                        },
                        e => {
                            this.loading -= 1;
                        },
                    ),
                )
                .subscribe(
                    () => {
                        this.onUpdateSuccess();
                        this.toastrService.show(
                            'Cập nhật thông tin khuyến mãi thành công',
                            'Cập nhật thông tin khuyến mãi thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(
                            'Cập nhật thông tin khuyến mãi thất bại',
                            'Cập nhật thông tin khuyến mãi thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                );
        } else if (this.showId && this.sessionId) {
            this.loading += 1;
            this.orderService
                .updatePromotion(
                    this.showId,
                    this.sessionId,
                    null,
                    'metaticket.order.calculate.promotion.meta_promo.MetaPromoPlugin',
                    JSON.stringify(this.promotionForm.get('items').getRawValue()),
                )
                .pipe(
                    tap(
                        e => {
                            this.loading -= 1;
                        },
                        e => {
                            this.loading -= 1;
                        },
                    ),
                )
                .subscribe(
                    () => {
                        this.onUpdateSuccess();
                        this.toastrService.show(
                            'Cập nhật thông tin khuyến mãi thành công',
                            'Cập nhật thông tin khuyến mãi thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(
                            getErrorMessageFromResponse(error),
                            'Cập nhật thông tin khuyến mãi thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                );
        } else {
            this.loading += 1;
            this.adminService
                .createShowSessionDraftPromotions$(
                    this.sessionDraftId,
                    this.name.getRawValue(),
                    JSON.stringify(this.promotionForm.get('items').getRawValue()),
                )
                .pipe(
                    tap(
                        e => {
                            this.loading -= 1;
                        },
                        e => {
                            this.loading -= 1;
                        },
                    ),
                )
                .subscribe(
                    sessionDraftPromotionModel => {
                        this.onAddDraftSuccess(sessionDraftPromotionModel);
                        this.toastrService.show(
                            'Cập nhật thông tin khuyến mãi thành công',
                            'Cập nhật thông tin khuyến mãi thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(
                            getErrorMessageFromResponse(error),
                            'Cập nhật thông tin khuyến mãi thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                );
        }
    }
}
