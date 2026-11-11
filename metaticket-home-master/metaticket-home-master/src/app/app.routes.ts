import { Routes } from '@angular/router';
import { routeName } from 'src/app/lib/constants';
import { authGuard } from './lib/guards/auth.guard';

export const routes: Routes = [
  {
    path: routeName.root,
    loadComponent: () => import('src/app/pages/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: routeName.event,
    loadComponent: () => import('./pages/event/event.component').then((m) => m.EventComponent),
  },
  // accessory storefront ("Sản phẩm") — browsing/cart are public, but checkout (the actual
  // purchase) requires login.
  {
    path: routeName.productCheckout, // san-pham/thanh-toan (static, must precede :showCode)
    loadComponent: () =>
      import('src/app/pages/accessory-checkout/accessory-checkout.component').then((m) => m.AccessoryCheckoutComponent),
    canActivate: [authGuard], // buying accessories requires a logged-in user
  },
  {
    path: routeName.product, // san-pham
    loadComponent: () =>
      import('src/app/pages/product-list/product-list.component').then((m) => m.ProductListComponent),
  },
  {
    path: `${routeName.productDetail}/:showCode`, // san-pham/:showCode
    loadComponent: () =>
      import('src/app/pages/product-detail/product-detail.component').then((m) => m.ProductDetailComponent),
  },
  {
    path: routeName.booking,
    loadComponent: () => import('src/app/pages/booking/booking.component').then((m) => m.BookingComponent),
    canActivate: [authGuard], // Use the functional guard
  },
  {
    path: routeName.vote,
    loadComponent: () => import('src/app/pages/vote/vote.component').then((m) => m.VoteComponent),
  },
  {
    path: routeName.ticketManager,
    loadComponent: () =>
      import('src/app/pages/ticket/ticket-manager/ticket-manager.component').then((m) => m.TicketManagerComponent),
    canActivate: [authGuard], // Use the functional guard
  },
  {
    path: routeName.orderManager,
    loadComponent: () =>
      import('src/app/pages/order-manager/order-manager.component').then((m) => m.OrderManagerComponent),
    canActivate: [authGuard],
  },
  {
    path: `${routeName.ticketSlotManager}`,
    loadComponent: () =>
      import('src/app/pages/ticket/ticket-manager/ticket-slot-manager/ticket-slot-manager.component').then(
        (m) => m.TicketSlotManagerComponent,
      ),
    canActivate: [authGuard], // Use the functional guard
  },
  {
    path: `${routeName.paymentResult}/:gateway/:code/:pD/:pC`,
    loadComponent: () =>
      import('src/app/pages/payment-result/payment-result.component').then((m) => m.PaymentResultComponent),
  },
  {
    path: `${routeName.show}/:id`,
    loadComponent: () => import('./pages/event-detail/event-detail.component').then((m) => m.EventDetailComponent),
  },
  {
    path: `${routeName.slugPrefix}/:showSlug`,
    loadComponent: () => import('./pages/event-detail/event-detail.component').then((m) => m.EventDetailComponent),
  },
  {
    path: `${routeName.slugPrefix}/:showSlug/:sessionSlug`,
    loadComponent: () => import('src/app/pages/booking/booking.component').then((m) => m.BookingComponent),
    canActivate: [authGuard], // Use the functional guard
  },
  {
    path: routeName.login,
    loadComponent: () => import('src/app/pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: routeName.register,
    loadComponent: () => import('src/app/pages/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: routeName.registerVerifyOtp,
    loadComponent: () =>
      import('src/app/pages/register/register-otp-validator/register.otp.validator.component').then(
        (m) => m.RegisterOtpValidatorComponent,
      ),
  },
  {
    path: routeName.registerUserInfo,
    loadComponent: () =>
      import('src/app/pages/register/register-user-info/register.user.info.component').then(
        (m) => m.RegisterUserInfoComponent,
      ),
  },
  {
    path: routeName.resetPassword,
    loadComponent: () =>
      import('src/app/pages/reset-password/reset.password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: routeName.resetPasswordVerifyOtp,
    loadComponent: () =>
      import('src/app/pages/reset-password/reset-password-otp-validator/reset.password.otp.validator.component').then(
        (m) => m.ResetPasswordOtpValidatorComponent,
      ),
  },
  {
    path: routeName.resetPasswordNewPass,
    loadComponent: () =>
      import('src/app/pages/reset-password/reset-password-new-password/reset.password.new.password.component').then(
        (m) => m.ResetPasswordNewPasswordComponent,
      ),
  },
  {
    path: routeName.accountInfo,
    loadComponent: () => import('src/app/pages/account/account.component').then((m) => m.AccountComponent),
  },
  {
    path: routeName.faq,
    loadComponent: () => import('src/app/pages/faq/frag.component').then((m) => m.FragComponent),
  },
  {
    path: routeName.contact,
    loadComponent: () => import('src/app/pages/contact/contact.component').then((m) => m.ContactComponent),
  },
  {
    path: routeName.aboutUs,
    loadComponent: () => import('src/app/pages/about-us/about-us.component').then((m) => m.AboutUsComponent),
  },
  {
    path: routeName.news,
    loadComponent: () => import('src/app/pages/news/news-home/news-home.component').then((m) => m.NewsHomeComponent),
  },
  {
    path: `${routeName.news}/${routeName.newsCategory}/:category`,
    loadComponent: () =>
      import('./pages/news/news-category/news-category.component').then((m) => m.NewsCategoryComponent),
  },
  {
    path: `${routeName.news}/:slug`,
    loadComponent: () =>
      import('src/app/pages/news/news-detail/news-detail.component').then((m) => m.NewsDetailComponent),
  },
  {
    path: routeName.search,
    loadComponent: () => import('src/app/pages/search/search.component').then((m) => m.SearchComponent),
  },
  {
    path: routeName.notFound,
    loadComponent: () => import('src/app/pages/not-found/not-found.component').then((m) => m.NotFoundComponent),
  },
  {
    path: routeName.policyRegulation,
    loadComponent: () =>
      import('src/app/pages/policy-regulation/policy-regulation.component').then((m) => m.PolicyRegulationComponent),
  },
  {
    path: routeName.policyInformationSecurity,
    loadComponent: () =>
      import('src/app/pages/policy-information-security-user/policy-information-security-user.component').then(
        (m) => m.PolicyInformationSecurityUserComponent,
      ),
  },
  {
    path: '**',
    redirectTo: routeName.notFound,
  },
];
