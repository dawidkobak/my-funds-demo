import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MatSnackBarRef} from '@angular/material/snack-bar';
import {CookieService} from 'ngx-cookie-service';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div fxLayout="row">
      <div matSnackBarLabel>A new version My Funds is available.</div>
      <div matSnackBarActions>
        <button mat-button matSnackBarAction (click)="reload()">Reload</button>
        <button mat-button matSnackBarAction (click)="dismiss()">
          Remind me later
        </button>
      </div>
    </div>
  `,
})
export class AppReloadComponent {
  static APP_RELOAD_COOKIE = 'app.reload.cookie';

  constructor(
    @Inject(MatSnackBarRef) private snackBarRef: MatSnackBarRef<any>,
    private cookieService: CookieService
  ) {}

  reload() {
    window.location.reload();
  }

  dismiss() {
    let date = new Date();
    date = new Date(date.getTime() + 60000 * 60);

    this.cookieService.set(AppReloadComponent.APP_RELOAD_COOKIE, 'true', date);
    this.snackBarRef.dismiss();
  }
}
