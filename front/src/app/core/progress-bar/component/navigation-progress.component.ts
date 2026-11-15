import {ChangeDetectionStrategy, Component} from '@angular/core';
import {LoadingProgressService} from '../service/loading-progress.service';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-core-progress-bar',
  templateUrl: './navigation-progress.component.html',
  styleUrls: ['./navigation-progress.component.scss'],
})
export class NavigationProgressComponent {
  constructor(public loader: LoadingProgressService) {}
}
