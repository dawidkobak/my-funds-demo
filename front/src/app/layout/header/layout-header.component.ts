import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Input, OnInit} from '@angular/core';
import {MatSidenav} from '@angular/material/sidenav';
import {User} from '../../../api/core/definition/user';
import {ActivatedRoute} from '@angular/router';
import {environment} from '../../../environments/environment';
import {FormBuilder, FormGroup} from '@angular/forms';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-layout-header',
  templateUrl: './layout-header.component.html',
  styleUrls: ['./layout-header.component.scss'],
})
export class LayoutHeaderComponent implements OnInit {
  @Input() public sidenav!: MatSidenav;
  public identity!: User;
  public environment: any;
  public searchForm!: FormGroup;

  constructor(
    private activatedRoute: ActivatedRoute,
    private formBuilder: FormBuilder
  ) {
    this.environment = environment;
    this.searchForm = this.formBuilder.group({
      search: this.formBuilder.control(''),
    });
  }

  ngOnInit(): void {
    this.activatedRoute.data.subscribe(data => {
      this.identity = data['identity'];
    });
  }
}
