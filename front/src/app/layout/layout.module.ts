import {NgModule} from '@angular/core';
import {MatToolbarModule} from '@angular/material/toolbar';
import {LayoutHeaderComponent} from './header/layout-header.component';
import {LayoutMenuComponent} from './menu/layout-menu.component';
import {MatListModule} from '@angular/material/list';
import {RouterModule} from '@angular/router';
import {FlexLayoutModule} from '@ngbracket/ngx-layout';
import {MatButtonModule} from '@angular/material/button';
import {CommonModule} from '@angular/common';
import {FontAwesomeModule} from '@fortawesome/angular-fontawesome';
import {MatInputModule} from '@angular/material/input';
import {MatMenuModule} from '@angular/material/menu';
import {NavigationProgressModule} from '../core/progress-bar/navigation-progress.module';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ReactiveFormsModule} from '@angular/forms';

@NgModule({
  declarations: [LayoutHeaderComponent, LayoutMenuComponent],
  exports: [LayoutHeaderComponent, LayoutMenuComponent],
  imports: [
    MatToolbarModule,
    MatListModule,
    MatButtonModule,
    RouterModule,
    FlexLayoutModule,
    CommonModule,
    FontAwesomeModule,
    MatInputModule,
    MatMenuModule,
    NavigationProgressModule,
    MatTooltipModule,
    ReactiveFormsModule,
  ],
})
export class LayoutModule {}
