import { ModuleWithProviders, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { MockUserService } from './mock-users.service';
import { MockElectricityService } from './mock-electricity.service';
import { MockSmartTableService } from './mock-smart-table.service';
import { MockUserActivityService } from './mock-user-activity.service';
import { MockOrdersChartService } from './mock-orders-chart.service';
import { MockProfitChartService } from './mock-profit-chart.service';
import { MockTrafficListService } from './mock-traffic-list.service';
import { MockPeriodsService } from './mock-periods.service';
import { MockEarningService } from './mock-earning.service';
import { MockOrdersProfitChartService } from './mock-orders-profit-chart.service';
import { MockTrafficBarService } from './mock-traffic-bar.service';
import { MockProfitBarAnimationChartService } from './mock-profit-bar-animation-chart.service';
import { MockTemperatureHumidityService } from './mock-temperature-humidity.service';
import { MockSolarService } from './mock-solar.service';
import { MockTrafficChartService } from './mock-traffic-chart.service';
import { MockStatsBarService } from './mock-stats-bar.service';
import { MockCountryOrderService } from './mock-country-order.service';
import { MockStatsProgressBarService } from './mock-stats-progress-bar.service';
import { MockVisitorsAnalyticsService } from './mock-visitors-analytics.service';
import { MockSecurityCamerasService } from './mock-security-cameras.service';

const SERVICES = [
    MockUserService,
    MockElectricityService,
    MockSmartTableService,
    MockUserActivityService,
    MockOrdersChartService,
    MockProfitChartService,
    MockTrafficListService,
    MockPeriodsService,
    MockEarningService,
    MockOrdersProfitChartService,
    MockTrafficBarService,
    MockProfitBarAnimationChartService,
    MockTemperatureHumidityService,
    MockSolarService,
    MockTrafficChartService,
    MockStatsBarService,
    MockCountryOrderService,
    MockStatsProgressBarService,
    MockVisitorsAnalyticsService,
    MockSecurityCamerasService,
];

@NgModule({
    imports: [CommonModule],
    providers: [...SERVICES],
})
export class MockDataModule {
    static forRoot(): ModuleWithProviders<MockDataModule> {
        return {
            ngModule: MockDataModule,
            providers: [...SERVICES],
        };
    }
}
