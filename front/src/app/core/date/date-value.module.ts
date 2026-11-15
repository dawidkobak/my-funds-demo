import {NgModule} from '@angular/core';
import {DateValuePipe} from './date-value.pipe';

@NgModule({
  declarations: [DateValuePipe],
  exports: [DateValuePipe],
})
export class DateValueModule {}
