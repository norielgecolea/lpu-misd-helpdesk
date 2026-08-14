import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const header = auth.getAuthorizationHeader();
  if (!header || req.url.includes('/auth/microsoft')) {
    return next(req);
  }
  return next(
    req.clone({
      setHeaders: { Authorization: header },
    }),
  );
};
