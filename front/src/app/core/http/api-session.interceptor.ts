import {Injectable} from '@angular/core';
import {
  HttpInterceptor,
  HttpHandler,
  HttpRequest,
  HttpErrorResponse,
  HttpEvent,
} from '@angular/common/http';
import {MatSnackBar} from '@angular/material/snack-bar';
import {tap} from 'rxjs/operators';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';
import {ApiCoreService} from '../../../api/core/api-core.service';
import {CookieService} from 'ngx-cookie-service';

@Injectable({
  providedIn: 'root',
})
export class ApiSessionInterceptor implements HttpInterceptor {
  constructor(
    private snackBar: MatSnackBar,
    private apiCoreService: ApiCoreService,
    private cookieService: CookieService
  ) {}

  intercept(
    request: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    // Track API calls
    if (
      request.url.indexOf('/event/create') === -1 &&
      request.url.indexOf('/auth/identity') === -1 &&
      request.url.indexOf('/?version=true') === -1
    ) {
      if (environment.name !== 'development') {
        // this.apiCoreService
        //   .postEvent(request.url, {
        //     body: request.body,
        //     referer: window.location.href,
        //   })
        //   .subscribe();
      }
    }

    // Add authorization header
    const authorizedRequest = request.clone({
      headers: request.headers.set(
        'Authorization',
        'Bearer ' + this.cookieService.get('authToken')
      ),
    });

    return next.handle(authorizedRequest).pipe(
      tap({
        error: error => {
          // Ignore progress bar option
          if (authorizedRequest.headers.has('ignoreHttpInterceptor')) {
            return next.handle(authorizedRequest);
          }

          if (error instanceof HttpErrorResponse) {
            if (error.status === 401) {
              this.cookieService.set(
                'redirectUrl',
                window.location.pathname + window.location.search
              );
            } else if (error.status === 403) {
              this.snackBar.open(
                'You are not authorized to perform this action.',
                'OK',
                {
                  duration: 5000,
                }
              );
            } else {
              this.snackBar.open(
                'An error occured' +
                  ' (' +
                  (error.status ? error.status : 'Try to reload application') +
                  ').',
                'OK',
                {
                  duration: 5000,
                }
              );
            }
          }

          return next.handle(authorizedRequest);
        },
      })
    );
  }
}
