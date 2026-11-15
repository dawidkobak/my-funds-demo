import {Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, Router} from '@angular/router';
import {Observable} from 'rxjs';
import {ApiCoreService} from '../api/core/api-core.service';
import {User} from '../api/core/definition/user';
import {CookieService} from 'ngx-cookie-service';

@Injectable({
  providedIn: 'root',
})
export class AppResolver {
  constructor(
    private apiCoreService: ApiCoreService,
    private cookieService: CookieService,
    private router: Router
  ) {}

  resolve(route: ActivatedRouteSnapshot): Observable<User> {
    // If we have an auth token, save it as a cookie
    if (route.queryParams['authToken']?.length) {
      this.cookieService.set('authToken', route.queryParams['authToken'], new Date(new Date().getTime() + 60 * 60 * 24 * 10 * 1000));

      const redirectUrl = this.cookieService.get('redirectUrl');

      if (redirectUrl) {
        this.cookieService.delete('redirectUrl');
        this.router.navigateByUrl(redirectUrl).then();
      }
    }

    return this.apiCoreService.getIdentity();
  }
}
