import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {User} from './definition/user';

@Injectable({
  providedIn: 'root',
})
export class ApiCoreService {
  constructor(private httpClient: HttpClient) {}

  public getIdentity(): Observable<User> {
    // return this.httpClient
    //   .get<User>(environment.apiHost + '/api/auth/identity')
    //   .pipe(map(response => response));
    const staticUser: User = {
      id: 1,
      username: 'demo',
      displayName: 'Użytkownik demo',
      permissions: [],
      roles: [],
    };

    return of(staticUser);
  }
}
