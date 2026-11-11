import { ModuleWithProviders, NgModule, Optional, SkipSelf } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NbAuthModule, NbDummyAuthStrategy } from '@nebular/auth';
import { NbRoleProvider, NbSecurityModule } from '@nebular/security';
import { of as observableOf } from 'rxjs';

import { throwIfAlreadyLoaded } from './module-import-guard';
import { AnalyticsService, LayoutService, PlayerService, SeoService, StateService } from './utils';
import { UserData } from './data/users';
import { ElectricityData } from './data/electricity';
import { SmartTableData } from './data/smart-table';
import { UserActivityData } from './data/user-activity';
import { OrdersChartData } from './data/orders-chart';
import { ProfitChartData } from './data/profit-chart';
import { TrafficListData } from './data/traffic-list';
import { EarningData } from './data/earning';
import { OrdersProfitChartData } from './data/orders-profit-chart';
import { TrafficBarData } from './data/traffic-bar';
import { ProfitBarAnimationChartData } from './data/profit-bar-animation-chart';
import { TemperatureHumidityData } from './data/temperature-humidity';
import { SolarData } from './data/solar';
import { TrafficChartData } from './data/traffic-chart';
import { StatsBarData } from './data/stats-bar';
import { CountryOrderData } from './data/country-order';
import { StatsProgressBarData } from './data/stats-progress-bar';
import { VisitorsAnalyticsData } from './data/visitors-analytics';
import { SecurityCamerasData } from './data/security-cameras';
import { MockElectricityService } from './mock/mock-electricity.service';
import { MockSmartTableService } from './mock/mock-smart-table.service';
import { MockUserActivityService } from './mock/mock-user-activity.service';
import { MockOrdersChartService } from './mock/mock-orders-chart.service';
import { MockProfitChartService } from './mock/mock-profit-chart.service';
import { MockTrafficListService } from './mock/mock-traffic-list.service';
import { MockEarningService } from './mock/mock-earning.service';
import { MockOrdersProfitChartService } from './mock/mock-orders-profit-chart.service';
import { MockTrafficBarService } from './mock/mock-traffic-bar.service';
import { MockProfitBarAnimationChartService } from './mock/mock-profit-bar-animation-chart.service';
import { MockTemperatureHumidityService } from './mock/mock-temperature-humidity.service';
import { MockSolarService } from './mock/mock-solar.service';
import { MockTrafficChartService } from './mock/mock-traffic-chart.service';
import { MockStatsBarService } from './mock/mock-stats-bar.service';
import { MockCountryOrderService } from './mock/mock-country-order.service';
import { MockStatsProgressBarService } from './mock/mock-stats-progress-bar.service';
import { MockVisitorsAnalyticsService } from './mock/mock-visitors-analytics.service';
import { MockSecurityCamerasService } from './mock/mock-security-cameras.service';
import { MockDataModule } from './mock/mock-data.module';
import { MockUserService } from './mock/mock-users.service';

const socialLinks = [
    {
        url: 'https://github.com/akveo/nebular',
        target: '_blank',
        icon: 'github',
    },
    {
        url: 'https://www.facebook.com/akveo/',
        target: '_blank',
        icon: 'facebook',
    },
    {
        url: 'https://twitter.com/akveo_inc',
        target: '_blank',
        icon: 'twitter',
    },
];

const DATA_SERVICES = [
    { provide: UserData, useClass: MockUserService },
    { provide: ElectricityData, useClass: MockElectricityService },
    { provide: SmartTableData, useClass: MockSmartTableService },
    { provide: UserActivityData, useClass: MockUserActivityService },
    { provide: OrdersChartData, useClass: MockOrdersChartService },
    { provide: ProfitChartData, useClass: MockProfitChartService },
    { provide: TrafficListData, useClass: MockTrafficListService },
    { provide: EarningData, useClass: MockEarningService },
    { provide: OrdersProfitChartData, useClass: MockOrdersProfitChartService },
    { provide: TrafficBarData, useClass: MockTrafficBarService },
    { provide: ProfitBarAnimationChartData, useClass: MockProfitBarAnimationChartService },
    { provide: TemperatureHumidityData, useClass: MockTemperatureHumidityService },
    { provide: SolarData, useClass: MockSolarService },
    { provide: TrafficChartData, useClass: MockTrafficChartService },
    { provide: StatsBarData, useClass: MockStatsBarService },
    { provide: CountryOrderData, useClass: MockCountryOrderService },
    { provide: StatsProgressBarData, useClass: MockStatsProgressBarService },
    { provide: VisitorsAnalyticsData, useClass: MockVisitorsAnalyticsService },
    { provide: SecurityCamerasData, useClass: MockSecurityCamerasService },
];

export class NbSimpleRoleProvider extends NbRoleProvider {
    getRole() {
        // here you could provide any role based on any auth flow
        return observableOf('guest');
    }
}

export const NB_CORE_PROVIDERS = [
    ...MockDataModule.forRoot().providers,
    ...DATA_SERVICES,
    ...NbAuthModule.forRoot({
        strategies: [
            NbDummyAuthStrategy.setup({
                name: 'email',
                delay: 3000,
            }),
        ],
        forms: {
            login: {
                socialLinks: socialLinks,
            },
            register: {
                socialLinks: socialLinks,
            },
        },
    }).providers,

    NbSecurityModule.forRoot({
        accessControl: {
            guest: {
                view: '*',
            },
            user: {
                parent: 'guest',
                create: '*',
                edit: '*',
                remove: '*',
            },
        },
    }).providers,

    {
        provide: NbRoleProvider,
        useClass: NbSimpleRoleProvider,
    },
    AnalyticsService,
    LayoutService,
    PlayerService,
    SeoService,
    StateService,
];

@NgModule({
    imports: [CommonModule],
    exports: [NbAuthModule],
    declarations: [],
})
export class CoreModule {
    constructor(@Optional() @SkipSelf() parentModule: CoreModule) {
        throwIfAlreadyLoaded(parentModule, 'CoreModule');
    }

    static forRoot(): ModuleWithProviders<CoreModule> {
        return {
            ngModule: CoreModule,
            providers: [...NB_CORE_PROVIDERS],
        };
    }
}
