import { NgOptimizedImage } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NbEvaIconsModule } from '@nebular/eva-icons';
import {
    NbAlertModule,
    NbAutocompleteModule,
    NbButtonGroupModule,
    NbButtonModule,
    NbCalendarRangeModule,
    NbCardModule,
    NbCheckboxModule,
    NbDatepickerModule,
    NbFormFieldModule,
    NbIconModule,
    NbInputModule,
    NbLayoutModule,
    NbMenuModule,
    NbRadioModule,
    NbSelectModule,
    NbSelectWithAutocompleteModule,
    NbSidebarModule,
    NbSpinnerModule,
    NbStepperModule,
    NbTabsetModule,
    NbTagModule,
    NbTimepickerModule,
    NbTreeGridModule,
} from '@nebular/theme';
import { CategoryItemComponent } from './category_mapping/category_item/category_item.component';
import { CategoryMappingComponent } from './category_mapping/category_mapping.component';
import { ShowSessionTableComponent } from './create_show_session/show_session_table/show_session_table.component';
import { PaymentGateway } from './gateway/payment.gateway';
import { PolicyDescGateway } from './gateway/policydesc.gateway';
import { QRCodeGateway } from './gateway/qrcode.gateway';
import { SeatplannerGateway } from './gateway/seatplanner.gateway';
import { TicketGateway } from './gateway/ticket.gateway';
import { TicketSlotGateway } from './gateway/ticket.slot.gateway';
import { OrderStatisticComponent } from './order_statistic/order-statistic.component';
import { OrdersComponent } from './orders/orders.component';
import { MetaticketPaymentService } from './service/metaticket.payment.service';
import { MetaticketPolicyDescService } from './service/metaticket.policydesc.service';
import { MetaticketSeatplannerService } from './service/metaticket.seatplanner.service';
import { Angular2SmartTableModule } from 'angular2-smart-table';
import { QRCodeService } from './service/metaticket.service.qrcode';
import {
    AdminService,
    NotificationService,
    OrderService,
    PaymentService,
    ShowService,
    UserService,
    NewsService,
    AccessoryShowService,
} from '../@core/metaticket/data.service';
import { ThemeModule } from '../@theme/theme.module';
import { CreateMailTemplateComponent } from './create_mail_template/create_mail_template.component';
import { CreateShowComponent } from './create_show/create_show.component';
import { CreateAccessoryShowComponent } from './create_accessory_show/create-accessory-show.component';
import { AccessoryShowListComponent } from './accessory_show/accessory-show-list.component';
import { AccessoryProductListComponent } from './accessory_product/accessory-product-list.component';
import { AccessoryPromotionListComponent } from './accessory_promotion/accessory-promotion-list.component';
import { AccessoryPromotionBatchDialogComponent } from './accessory_promotion/accessory-promotion-batch-dialog.component';
import { AccessoryOrderDetailDialogComponent } from './accessory_order/accessory-order-detail-dialog.component';
import { AccessoryOrderListComponent } from './accessory_order/accessory-order-list.component';
import { AccessoryOrderStatisticComponent } from './accessory_order_statistic/accessory-order-statistic.component';
import { AccessoryReportFilterComponent } from './accessory_report_filter/accessory-report-filter.component';
import { CreateAccessoryProductComponent } from './create_accessory_product/create-accessory-product.component';
import { CreateAccessoryPromotionComponent } from './create_accessory_promotion/create-accessory-promotion.component';
import { SummaryCreateShowSessionComponent } from './create_show_session/components/summary_create/summary_create_show_session.component';
import { UpdateEmailTemplateComponent } from './create_show_session/components/update_email_template/update_show_session_email_template.component';
import { BooleanOrderCustomFieldComponent } from './create_show_session/components/update_order_custom_fields/boolean_order_custom_field/boolean_order_custom_field.component';
import { DateOrderCustomFieldComponent } from './create_show_session/components/update_order_custom_fields/date_order_custom_field/date_order_custom_field.component';
import { ListOrderCustomFieldComponent } from './create_show_session/components/update_order_custom_fields/list_order_custom_field/list_order_custom_field.component';
import { NumberOrderCustomFieldComponent } from './create_show_session/components/update_order_custom_fields/number_order_custom_field/number_order_custom_field.component';
import { StringOrderCustomFieldComponent } from './create_show_session/components/update_order_custom_fields/string_order_custom_field/string_order_custom_field.component';
import { UpdateOrderCustomFieldsComponent } from './create_show_session/components/update_order_custom_fields/update_order_custom_fields.component';
import { UpdateShowSessionPaymentComponent } from './create_show_session/components/update_payment/update_show_session_payment.component';
import { EditPromotionDialogComponent } from './create_show_session/components/update_promotion/edit_promotion_dialog/edit_promotion_dialog.component';
import { MultiPromotionCodeComponent } from './create_show_session/components/update_promotion/multi_promotion_code/multi_promotion_code.component';
import { PromotionCodeComponent } from './create_show_session/components/update_promotion/promotion_code/promotion_code.component';
import { PromotionDiscountByDateComponent } from './create_show_session/components/update_promotion/promotion_discount_by_date/promotion_discount_by_date.component';
import { PromotionDiscountByTicketAmountComponent } from './create_show_session/components/update_promotion/promotion_discount_by_ticket_amount/promotion_discount_by_ticket_amount.component';
import { PromotionDiscountByPriceCategoryAmountComponent } from './create_show_session/components/update_promotion/promotion_discount_by_price_category_amount/promotion_discount_by_price_category_amount.component';
import { PromotionMaxDiscountAmountComponent } from './create_show_session/components/update_promotion/promotion_max_discount_amount/promotion_max_discount_amount.component';

import { UpdateShowSessionPromotionComponent } from './create_show_session/components/update_promotion/update_show_session_promotion.component';
import { UpdateShowSessionSeatPlanComponent } from './create_show_session/components/update_ticket_seat_plan/update_show_session_seat_plan.component';
import { CreateShowSessionComponent } from './create_show_session/create_show_session.component';
import { DashboardModule } from './dashboard/dashboard.module';
import { DialogModule } from './dialog/dialog.module';
import { AdminGateway } from './gateway/admin.gateway';
import { NotificationGateway } from './gateway/notification.gateway';
import { OrderGateway } from './gateway/order.gateway';
import { ShowGateway } from './gateway/show.gateway';
import { NewsGateway } from './gateway/news.gateway';
import { AccessoryShowGateway } from './gateway/accessory-show.gateway';
import { MailTemplatesComponent } from './mail_templates/mail_templates.component';
import { MetaticketRoutingModule } from './metaticket-routing.module';
import { MetaticketComponent } from './metaticket.component';
import { OfflinePaymentComponent } from './offline_payment/offline_payment.component';
import { MetaticketAdminService } from './service/metaticket.admin.service';
import { MetaticketNotificationService } from './service/metaticket.notification.service';
import { MetaticketOrderService } from './service/metaticket.order.service';
import { MetaticketShowService } from './service/metaticket.show.service';
import { MetaticketNewsService } from './service/metaticket.news.service';
import { MetaticketAccessoryShowService } from './service/metaticket.accessory-show.service';
import { MetaticketUserService } from './service/metaticket.user.service';
import { ShowsComponent } from './shows/shows.component';
import { PinShowComponent } from './pin_show/pin-show.component';
import { PinShowPickerDialogComponent } from './pin_show/pin-show-picker.dialog';
import { PinAccessoryShowComponent } from './pin_accessory_show/pin-accessory-show.component';
import { PinAccessoryShowPickerDialogComponent } from './pin_accessory_show/pin-accessory-show-picker.dialog';
import { PinNewsComponent } from './pin_news/pin-news.component';
import { PinNewsPickerDialogComponent } from './pin_news/pin-news-picker.dialog';
import { NewsListComponent } from './news/news-list.component';
import { TicketCalendarComponent } from './ticket_calendar/ticket-calendar.component';
import { CalendarGenerateDialogComponent } from './ticket_calendar/calendar-generate-dialog.component';
import { CalendarBulkDialogComponent } from './ticket_calendar/calendar-bulk-dialog.component';

@NgModule({
    imports: [
        MetaticketRoutingModule,
        ThemeModule,
        NbMenuModule,
        DashboardModule,
        NbCardModule,
        Angular2SmartTableModule,
        FormsModule,
        NbInputModule,
        NbFormFieldModule,
        NbButtonModule,
        NbCheckboxModule,
        NbSelectModule,
        NbSelectWithAutocompleteModule,
        NbAutocompleteModule,
        DialogModule,
        NbAlertModule,
        NbSpinnerModule,
        ReactiveFormsModule,
        NbDatepickerModule,
        NbTimepickerModule,
        NbStepperModule,
        NgOptimizedImage,
        NbSidebarModule,
        NbLayoutModule,
        NbRadioModule,
        NbTabsetModule,
        NbIconModule,
        NbEvaIconsModule,
        NbTagModule,
        NbTreeGridModule,
        NbCalendarRangeModule,
        NbButtonGroupModule,
    ],
    declarations: [
        ShowSessionTableComponent,
        CategoryItemComponent,
        CategoryMappingComponent,
        MetaticketComponent,
        OrdersComponent,
        OfflinePaymentComponent,
        UpdateShowSessionPaymentComponent,
        UpdateShowSessionPromotionComponent,
        CreateShowComponent,
        CreateShowSessionComponent,
        ShowsComponent,
        PinShowComponent,
        PinShowPickerDialogComponent,
        PinAccessoryShowComponent,
        PinAccessoryShowPickerDialogComponent,
        PinNewsComponent,
        PinNewsPickerDialogComponent,
        NewsListComponent,
        AccessoryShowListComponent,
        AccessoryProductListComponent,
        AccessoryPromotionListComponent,
        AccessoryPromotionBatchDialogComponent,
        AccessoryOrderListComponent,
        AccessoryOrderDetailDialogComponent,
        AccessoryOrderStatisticComponent,
        AccessoryReportFilterComponent,
        CreateAccessoryShowComponent,
        CreateAccessoryProductComponent,
        CreateAccessoryPromotionComponent,
        PromotionDiscountByDateComponent,
        PromotionDiscountByTicketAmountComponent,
        PromotionDiscountByPriceCategoryAmountComponent,
        PromotionCodeComponent,
        MultiPromotionCodeComponent,
        PromotionMaxDiscountAmountComponent,
        EditPromotionDialogComponent,
        UpdateOrderCustomFieldsComponent,
        StringOrderCustomFieldComponent,
        NumberOrderCustomFieldComponent,
        DateOrderCustomFieldComponent,
        ListOrderCustomFieldComponent,
        BooleanOrderCustomFieldComponent,
        UpdateEmailTemplateComponent,
        SummaryCreateShowSessionComponent,
        UpdateShowSessionSeatPlanComponent,
        MailTemplatesComponent,
        CreateMailTemplateComponent,
        OrderStatisticComponent,
        TicketCalendarComponent,
        CalendarGenerateDialogComponent,
        CalendarBulkDialogComponent,
    ],
    providers: [
        { provide: ShowService, useClass: MetaticketShowService },
        { provide: NewsService, useClass: MetaticketNewsService },
        { provide: AccessoryShowService, useClass: MetaticketAccessoryShowService },
        { provide: UserService, useClass: MetaticketUserService },
        { provide: AdminService, useClass: MetaticketAdminService },
        { provide: NotificationService, useClass: MetaticketNotificationService },
        { provide: OrderService, useClass: MetaticketOrderService },
        { provide: PaymentService, useClass: MetaticketPaymentService },
        { provide: MetaticketSeatplannerService },
        { provide: MetaticketPolicyDescService },
        { provide: ShowGateway },
        { provide: NewsGateway },
        { provide: AccessoryShowGateway },
        { provide: OrderGateway },
        { provide: PaymentGateway },
        { provide: TicketGateway },
        { provide: TicketSlotGateway },
        { provide: AdminGateway },
        { provide: QRCodeGateway },
        { provide: QRCodeService },
        { provide: NotificationGateway },
        { provide: SeatplannerGateway },
        { provide: PolicyDescGateway },
    ],
})
export class MetaticketModule {}
