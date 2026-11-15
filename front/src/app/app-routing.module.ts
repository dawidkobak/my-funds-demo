import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {AppResolver} from './app.resolver';
import {PageNotFoundComponent} from './404/component/page-not-found.component';

const routes: Routes = [
  {
    path: '',
    loadChildren: () =>
      import('./home/home.module').then(module => module.HomeModule),
    resolve: {
      identity: AppResolver,
    },
  },
  {
    path: '**',
    pathMatch: 'full',
    component: PageNotFoundComponent,
    resolve: {
      identity: AppResolver,
    },
  },
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      onSameUrlNavigation: 'reload',
    }),
  ],
  exports: [RouterModule],
})
export class AppRoutingModule {}
