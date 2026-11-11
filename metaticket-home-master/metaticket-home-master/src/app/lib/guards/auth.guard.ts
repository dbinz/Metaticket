import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services';
import { routeName } from '../constants';
import { catchError, map, of } from 'rxjs';

export const authGuard: CanActivateFn = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // 1. Kiểm tra access token trước
  if (authService.isAuthenticated()) {
    return true;
  }

  // 2. Nếu access token không hợp lệ, ta không thể biết refresh token có tồn tại hay không.
  //    Cách duy nhất là thử gọi API refreshToken().
  //    Nếu thành công, nghĩa là có cookie hợp lệ.
  //    Nếu thất bại, nghĩa là không có hoặc cookie đã hết hạn.
  return authService.refreshToken().pipe(
    map((response) => {
      // Nếu refreshToken thành công, nghĩa là ta đã có access token mới.
      // Cho phép truy cập route.
      return !!response?.token;
    }),
    catchError(() => {
      // Nếu API refreshToken thất bại, chuyển hướng về login.
      router.navigate([routeName.login], { queryParams: { returnUrl: state.url } });
      return of(false);
    }),
  );
};
