import {ChangeDetectionStrategy} from '@angular/core';
import {Component, OnInit} from '@angular/core';
import {MatTabChangeEvent} from '@angular/material/tabs';
import {ActivatedRoute, Router} from '@angular/router';
import {Wallet} from '../../../api/wallet';
import {Stock} from '../../../api/stock';
import {Transaction} from '../../../api/transaction';
import {PiggyBank} from '../../../api/piggy-bank';
import {StockAnalysis} from '../../../api/stock-analysis';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './home.component.html',
  styleUrls: ['../../../style/vertical-tabs.scss'],
})
export class HomeComponent implements OnInit {
  public wallets!: Wallet[];
  public stocks!: Stock[];
  public transactions!: Transaction[];
  public stockAnalyses!: StockAnalysis[];
  public piggyBanks!: PiggyBank[];
  public stockQuoteList!: string[];

  public fragment!: string | null;

  private tabs = [
    'wallets',
    'assets',
    'transactions',
    'bonds',
    'quote-lists',
    'investments-monitoring',
    'expneses',
    'piggy-banks',
    'analyses',
  ];

  constructor(
    public activatedRoute: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.activatedRoute.data.subscribe(data => {
      this.wallets = data['data']['wallets'];
      this.stocks = data['data']['stocks'];
      this.transactions = data['data']['transactions'];
      this.stockAnalyses = data['data']['stockAnalyses'];
      this.piggyBanks = data['data']['piggyBanks'];
      this.stockQuoteList = data['data']['stockQuoteList'];
    });

    this.activatedRoute.fragment.subscribe(
      fragment => (this.fragment = fragment)
    );
  }

  public getTabIndex(): number {
    const tabIndex = this.tabs.findIndex(value => value === this.fragment);
    return tabIndex >= 0 ? tabIndex : this.tabs.indexOf('investments-monitoring');
  }

  public setTabIndex($event: MatTabChangeEvent) {
    return this.router.navigate([], {fragment: this.tabs[$event.index]});
  }
}
