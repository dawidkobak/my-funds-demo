import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {FlexLayoutModule} from '@ngbracket/ngx-layout';
import {
  Data,
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
} from '@angular/router';
import {NavigationProgressComponent} from './component/navigation-progress.component';
import {LoadingProgressService} from './service/loading-progress.service';

@NgModule({
  imports: [CommonModule, MatProgressBarModule, FlexLayoutModule],
  declarations: [NavigationProgressComponent],
  exports: [NavigationProgressComponent],
})
export class NavigationProgressModule {
  constructor(router: Router, loader: LoadingProgressService) {
    router.events.subscribe(event => {
      const navState = NavigationProgressModule.readNavigationState(router);
      if (navState && navState['ignoreLoadingBar']) {
        return;
      }

      if (event instanceof NavigationStart) {
        loader.begin();
      } else if (
        event instanceof NavigationError ||
        event instanceof NavigationEnd ||
        event instanceof NavigationCancel
      ) {
        loader.finish();
      }
    });
  }

  private static readNavigationState(router: Router): Data {
    const currentNavigation =
      router.getCurrentNavigation && router.getCurrentNavigation();
    if (currentNavigation && currentNavigation.extras) {
      return currentNavigation.extras.state as Data;
    }

    return {};
  }
}
