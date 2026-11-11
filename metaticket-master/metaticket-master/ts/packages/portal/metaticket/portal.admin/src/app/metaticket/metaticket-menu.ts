import { NbMenuItem } from '@nebular/theme';

export const MENU_ITEMS: NbMenuItem[] = [
    {
        title: 'META Ticket',
        group: true,
    },
    {
        title: 'Danh sách show',
        icon: 'film-outline',
        home: true,
        link: '/portal/shows',
    },
    {
        title: 'Báo cáo bán vé',
        icon: 'folder-outline',
        expanded: true,
        children: [
            {
                title: 'Filters',
                icon: 'funnel-outline',
                link: '/portal/report/filter',
            },
            {
                title: 'Thống kê',
                icon: 'trending-up-outline',
                link: '/portal/report/statistic',
            },
            {
                title: 'Danh sách đơn hàng',
                icon: 'layers-outline',
                link: '/portal/report/orders',
            },
            {
                title: 'Thanh toán nội bộ',
                icon: 'cast-outline',
                link: '/portal/report/offline-payment',
            },
        ],
    },
    {
        title: 'Hạng vé / Vé',
        icon: 'layers-outline',
        expanded: true,
        children: [
            {
                title: 'Hạng vé',
                icon: 'plus-square-outline',
                link: '/portal/ticket/category',
            },
            {
                title: 'Tạo vé',
                icon: 'plus-square-outline',
                link: '/portal/ticket/create',
            },
            {
                title: 'Lịch bán vé',
                icon: 'calendar-outline',
                link: '/portal/ticket/calendar',
            },
            {
                title: 'Danh sách vé',
                icon: 'list-outline',
                link: '/portal/tickets',
            },
            {
                title: 'Danh sách slot',
                icon: 'list-outline',
                link: '/portal/ticket-slots',
            },
        ],
    },
    {
        title: 'Seat Map',
        icon: 'map-outline',
        expanded: true,
        children: [
            {
                title: 'Danh sách',
                icon: 'list-outline',
                link: '/portal/seat-map/list',
            },
            {
                title: 'Vẽ',
                icon: 'color-palette-outline',
                link: '/portal/seat-map/draw',
            },
        ],
    },
    {
        title: 'Quản lý ghim',
        icon: 'pin-outline',
        expanded: true,
        children: [
            {
                title: 'Sự kiện bán vé',
                icon: 'film-outline',
                link: '/portal/pins/shows',
            },
            {
                title: 'Sự kiện bán hàng',
                icon: 'shopping-bag-outline',
                link: '/portal/pins/accessory-shows',
            },
            {
                title: 'Bài viết',
                icon: 'file-text-outline',
                link: '/portal/pins/news',
            },
        ],
    },
    {
        title: 'Tin tức',
        icon: 'file-text-outline',
        link: '/portal/news',
    },
    {
        title: 'Mua vé hộ',
        icon: 'book',
        link: '/portal/tickets/booking',
    },
    {
        title: 'Mail template',
        icon: 'email-outline',
        link: '/portal/mail/templates',
    },
    {
        title: 'Payment Accounts',
        icon: 'credit-card-outline',
        link: '/portal/pg-accounts',
    },
    {
        title: 'Quản lý sản phẩm',
        icon: 'shopping-bag-outline',
        expanded: true,
        children: [
            {
                title: 'Sự kiện bán hàng',
                icon: 'list-outline',
                link: '/portal/accessory/shows',
            },
            {
                title: 'Sản phẩm',
                icon: 'cube-outline',
                link: '/portal/accessory/products',
            },
            {
                title: 'Khuyến mãi',
                icon: 'pricetags-outline',
                link: '/portal/accessory/promotions',
            },
        ],
    },
    {
        title: 'Báo cáo bán hàng',
        icon: 'pie-chart-outline',
        expanded: true,
        children: [
            {
                title: 'Filters',
                icon: 'funnel-outline',
                link: '/portal/accessory/report/filter',
            },
            {
                title: 'Thống kê',
                icon: 'trending-up-outline',
                link: '/portal/accessory/report/statistic',
            },
            {
                title: 'Đơn hàng',
                icon: 'shopping-cart-outline',
                link: '/portal/accessory/orders',
            },
        ],
    },
];
