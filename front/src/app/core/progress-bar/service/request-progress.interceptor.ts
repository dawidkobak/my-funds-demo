import {Injectable} from '@angular/core';
import {
  HttpEvent,
  HttpInterceptor,
  HttpHandler,
  HttpRequest,
} from '@angular/common/http';
import {Observable} from 'rxjs';
import {finalize} from 'rxjs/operators';
import {LoadingProgressService} from './loading-progress.service';

@Injectable()
export class RequestProgressInterceptor implements HttpInterceptor {
  constructor(private loader: LoadingProgressService) {}

  intercept(
    request: HttpRequest<object>,
    next: HttpHandler
  ): Observable<HttpEvent<object>> {
    // Ignore progress bar option
    if (request.headers.has('ignoreProgressBar')) {
      return next.handle(
        request.clone({
          headers: request.headers.delete('ignoreProgressBar'),
        })
      );
    }

    const response$ = next.handle(request);

    let started = false;
    const subscribeToResponse = response$.subscribe.bind(response$);

    // @ts-ignore
    response$.subscribe = (...args) => {
      this.loader.begin();
      started = true;
      // @ts-ignore
      return subscribeToResponse(...args);
    };

    return response$.pipe(finalize(() => started && this.loader.finish()));
  }
}
