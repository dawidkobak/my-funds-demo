import {ChangeDetectionStrategy} from '@angular/core';
import {Component, OnInit} from '@angular/core';
import {environment} from '../../../environments/environment';
import {ActivatedRoute} from '@angular/router';
import {User} from '../../../api/core/definition/user';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-layout-menu',
  templateUrl: './layout-menu.component.html',
  styleUrls: ['./layout-menu.component.scss'],
})
export class LayoutMenuComponent implements OnInit {
  // @ts-ignore
  public environment!: any;
  public identity!: User;

  constructor(private activatedRoute: ActivatedRoute) {
    this.environment = environment;
  }

  ngOnInit() {
    this.activatedRoute.data.subscribe(data => {
      this.identity = data['identity'];
    });
  }
}
