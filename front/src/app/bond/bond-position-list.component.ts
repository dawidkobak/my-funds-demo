import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {Component, Input, OnChanges, SimpleChanges} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MatTableDataSource} from '@angular/material/table';
import {BondPosition} from '../../api/bond';
import {Wallet} from '../../api/wallet';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'bond-position-list',
  templateUrl: './bond-position-list.component.html',
  styleUrls: ['./bond-position-list.component.scss'],
})
export class BondPositionListComponent implements OnChanges {
  @Input() wallets!: Wallet[];

  bondWallets: Wallet[] = [];
  selectedWallet: Wallet | null = null;
  public readonly staticDemo = environment.staticDemo;
  valuationDates: string[] = [];
  valuationDate = environment.valuationDate;
  dataSource = new MatTableDataSource<BondPosition>([]);
  displayedColumns: string[] = [
    'symbol',
    'totalUnits',
    'purchaseDate',
    'maturityDate',
    'purchaseCost',
    'currentUnitValue',
    'currentTotalValue',
    'sourceCurrentValue',
  ];

  constructor(private httpClient: HttpClient) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['wallets'] && this.wallets) {
      if (this.staticDemo) this.httpClient.get<string[]>('/api/stock-quote/list').subscribe(dates => this.valuationDates = dates);
      this.bondWallets = this.wallets.filter(
        wallet => wallet.walletType === 'BOND'
      );
      if (!this.selectedWallet && this.bondWallets.length > 0) {
        this.selectedWallet = this.bondWallets[0];
        this.refreshBondPositions();
      }
    }
  }

  onWalletChange(): void {
    this.refreshBondPositions();
  }

  onValuationDateChange(): void {
    this.refreshBondPositions();
  }

  get totalCurrentValue(): number {
    return this.dataSource.data.reduce(
      (sum, bondPosition) => sum + bondPosition.currentTotalValue,
      0
    );
  }

  get totalPurchaseCost(): number {
    return this.dataSource.data.reduce(
      (sum, bondPosition) => sum + bondPosition.purchaseCost,
      0
    );
  }

  private refreshBondPositions(): void {
    if (!this.selectedWallet) {
      this.dataSource.data = [];
      return;
    }

    this.httpClient
      .get<BondPosition[]>('/api/bond/position/list', {
        params: {
          'wallet.id': this.selectedWallet.id,
          date: this.valuationDate,
        },
      })
      .subscribe(bondPositions => {
        this.dataSource.data = bondPositions;
      });
  }
}
