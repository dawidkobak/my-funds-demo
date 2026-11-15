import {Pipe, PipeTransform} from '@angular/core';

@Pipe({
  standalone: false,
  name: 'toDateObject',
})
export class DateValuePipe implements PipeTransform {
  transform(value: any): any {
    if (!value) {
      return null;
    }

    const dateText = value.toString().replace(' ', 'T');
    return new Date(dateText);
  }
}
