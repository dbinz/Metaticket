import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CategoryMappingComponent } from './category_mapping/category_mapping.component';
import { CreateTicketComponent } from './create_ticket/create-ticket.component';
import { OrderStatisticComponent } from './order_statistic/order-statistic.component';
import { OrdersComponent } from './orders/orders.component';
import { CreateNinePayAccountComponent } from './pg_account/create_ninepay_account/create-ninepay-account.component';
import { CreateOnePayAccountComponent } from './pg_account/create_one_pay_account/create-onepay-account.component';
import { CreateVNPayAccountComponent } from './pg_account/create_vnpay_account/create-vnpay-account.component';
import { DetailNinePayAccountComponent } from './pg_account/detail_ninepay_account/detail-ninepay-account.component';
import { DetailOnePayAccountComponent } from './pg_account/detail_onepay_account/detail-onepay-account.component';
import { DetailVNPayAccountComponent } from './pg_account/detail_vnpaypay_account/detail-vnpay-account.component';
import { PGAccountComponent } from './pg_account/pg-account.component';
import { ReportFilterComponent } from './report_filter/report.filter.component';
import { ReservationOfTicketComponent } from './reservation_of_ticket/reservation.of.ticket.component';
import { ReservationOrderDetailsComponent } from './reservation_order_details/reservation.order.details.component';
import { TicketBookingComponent } from './ticket_booking/ticket.booking.component';
import { TicketSlotsComponent } from './ticket_slots/ticket-slots.component';
import { TicketsComponent } from './tickets/tickets.component';
import { TicketCalendarComponent } from './ticket_calendar/ticket-calendar.component';
import { CreateMailTemplateComponent } from './create_mail_template/create_mail_template.component';
import { CreateShowComponent } from './create_show/create_show.component';
import { CreateShowSessionComponent } from './create_show_session/create_show_session.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { MailTemplatesComponent } from './mail_templates/mail_templates.component';
import { MetaticketComponent } from './metaticket.component';
import { OfflinePaymentComponent } from './offline_payment/offline_payment.component';
import { MetaticketAuthGuardService } from './service/metaticket.auth.guard.service';
import { ShowsComponent } from './shows/shows.component';
import { NewsListComponent } from './news/news-list.component';
import { PinShowComponent } from './pin_show/pin-show.component';
import { PinAccessoryShowComponent } from './pin_accessory_show/pin-accessory-show.component';
import { PinNewsComponent } from './pin_news/pin-news.component';
import { AccessoryShowListComponent } from './accessory_show/accessory-show-list.component';
import { AccessoryProductListComponent } from './accessory_product/accessory-product-list.component';
import { AccessoryPromotionListComponent } from './accessory_promotion/accessory-promotion-list.component';
import { AccessoryOrderListComponent } from './accessory_order/accessory-order-list.component';
import { AccessoryOrderStatisticComponent } from './accessory_order_statistic/accessory-order-statistic.component';
import { AccessoryReportFilterComponent } from './accessory_report_filter/accessory-report-filter.component';
import { CreateAccessoryProductComponent } from './create_accessory_product/create-accessory-product.component';
import { CreateAccessoryShowComponent } from './create_accessory_show/create-accessory-show.component';
import { CreateAccessoryPromotionComponent } from './create_accessory_promotion/create-accessory-promotion.component';

const routes: Routes = [
    {
        path: '',
        canActivate: [MetaticketAuthGuardService],
        component: MetaticketComponent,
        children: [
            { path: 'dashboard', component: DashboardComponent },
            { path: 'news', pathMatch: 'full', component: NewsListComponent },
            { path: 'pins/shows', component: PinShowComponent },
            { path: 'pins/accessory-shows', component: PinAccessoryShowComponent },
            { path: 'pins/news', component: PinNewsComponent },
            { path: 'report/orders', component: OrdersComponent },
            { path: 'report/statistic', component: OrderStatisticComponent },
            { path: 'report/offline-payment', component: OfflinePaymentComponent },
            { path: 'report/filter', component: ReportFilterComponent },
            { path: 'tickets', component: TicketsComponent },
            { path: 'ticket/category', component: CategoryMappingComponent },
            { path: 'ticket/create', component: CreateTicketComponent },
            { path: 'ticket/calendar', component: TicketCalendarComponent },
            { path: 'ticket-slots', component: TicketSlotsComponent },
            { path: 'ticket/reservations', component: ReservationOfTicketComponent },
            { path: 'reservation-order', component: ReservationOrderDetailsComponent },
            { path: 'show/create', component: CreateShowComponent },
            { path: 'show/update', component: CreateShowComponent },
            { path: 'show/session/create', component: CreateShowSessionComponent },
            { path: 'shows', component: ShowsComponent },
            { path: 'accessory/shows', component: AccessoryShowListComponent },
            { path: 'accessory/products', component: AccessoryProductListComponent },
            { path: 'accessory/product/create', component: CreateAccessoryProductComponent },
            { path: 'accessory/product/update', component: CreateAccessoryProductComponent },
            { path: 'accessory/show/create', component: CreateAccessoryShowComponent },
            { path: 'accessory/show/update', component: CreateAccessoryShowComponent },
            { path: 'accessory/promotions', component: AccessoryPromotionListComponent },
            { path: 'accessory/orders', component: AccessoryOrderListComponent },
            { path: 'accessory/report/filter', component: AccessoryReportFilterComponent },
            { path: 'accessory/report/statistic', component: AccessoryOrderStatisticComponent },
            { path: 'accessory/promotion/create', component: CreateAccessoryPromotionComponent },
            { path: 'accessory/promotion/update', component: CreateAccessoryPromotionComponent },
            { path: 'tickets/booking', component: TicketBookingComponent },
            { path: 'mail/templates', component: MailTemplatesComponent },
            { path: 'mail/template/create', component: CreateMailTemplateComponent },
            { path: 'mail/template/update', component: CreateMailTemplateComponent },
            { path: 'pg-accounts', component: PGAccountComponent },
            { path: 'pg-account/onepay/create', component: CreateOnePayAccountComponent },
            { path: 'pg-account/onepay/detail', component: DetailOnePayAccountComponent },
            { path: 'pg-account/ninepay/create', component: CreateNinePayAccountComponent },
            { path: 'pg-account/ninepay/detail', component: DetailNinePayAccountComponent },
            { path: 'pg-account/vnpay/create', component: CreateVNPayAccountComponent },
            { path: 'pg-account/vnpay/detail', component: DetailVNPayAccountComponent },
            {
                path: 'seat-map',
                loadChildren: () => import('./seat_map/seat_map.module').then(m => m.SeatMapModule)
            },
            { path: '', redirectTo: 'shows', pathMatch: 'full' },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class MetaticketRoutingModule {}
