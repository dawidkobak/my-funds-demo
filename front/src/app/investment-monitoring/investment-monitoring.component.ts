import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {
  Component,
  Input,
  OnInit,
  ViewChild,
  AfterViewInit,
} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MatSort} from '@angular/material/sort';
import {MatTableDataSource} from '@angular/material/table';
import {Wallet} from '../../api/wallet';
import {StockQuote} from '../../api/core/stock-quote';
import {Transaction} from '../../api/transaction';
import {BondPosition} from '../../api/bond';

interface PortfolioAsset {
  id: number;
  name: string;
  value: number;
  color: string;
  purchaseCost: number;
  purchaseDate: string;
  profitPerYear: number;
}

interface WalletValueInTime {
  date: string;
  value: number;
  ownContribution: number;
}

interface NetWorthSummary {
  date: string;
  totalValue: number;
  categories: Array<{
    name: string;
    value: number;
    color: string;
  }>;
}

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'investment-monitoring',
  templateUrl: './investment-monitoring.component.html',
  styleUrls: ['./investment-monitoring.component.scss'],
})
export class InvestmentMonitoringComponent implements OnInit, AfterViewInit {
  @Input() wallets!: Wallet[];
  @ViewChild(MatSort) sort!: MatSort;

  public readonly staticDemo = environment.staticDemo;
  stockQuotes: string[] = [];
  availableWallets: Wallet[] = [];
  selectedWallet: Wallet | null = null;
  selectedStockQuoteDate: string | null = null;
  dataSource = new MatTableDataSource<PortfolioAsset>([]);
  walletValueInTime: WalletValueInTime[] = [];
  totalPortfolioValue = 0;
  displayedColumns: string[] = [
    'name',
    'value',
    'purchaseDate',
    'purchaseCost',
    'profitPerYear',
  ];

  constructor(private httpClient: HttpClient) {}

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
  }

  ngOnInit(): void {
    this.availableWallets = [
      {id: -1, name: 'Majątek netto', walletType: 'NET_WORTH'},
      ...this.wallets,
    ];
    this.selectedWallet = this.availableWallets[0];

    this.httpClient.get<string[]>('/api/stock-quote/list').subscribe(quotes => {
      this.stockQuotes = quotes;
      this.selectedStockQuoteDate =
        this.stockQuotes.length > 0
          ? this.stockQuotes[0]
          : new Date().toISOString().slice(0, 10);
      if (this.selectedWallet) {
        this.fetchCurrentAssetsAndStockQuotes();
      }
    });
  }

  onWalletChange(): void {
    if (this.selectedStockQuoteDate && this.selectedWallet) {
      this.fetchCurrentAssetsAndStockQuotes();
    }
  }

  onStockQuoteDateChange(): void {
    if (this.selectedStockQuoteDate && this.selectedWallet) {
      this.fetchCurrentAssetsAndStockQuotes();
    }
  }

  private fetchCurrentAssetsAndStockQuotes(): void {
    if (!this.selectedWallet || !this.selectedStockQuoteDate) {
      return;
    }

    if (this.selectedWallet.walletType === 'NET_WORTH') {
      this.httpClient
        .get<NetWorthSummary>('/api/wallet/net-worth', {
          params: {date: this.selectedStockQuoteDate},
        })
        .subscribe(netWorthSummary => {
          this.dataSource.data = netWorthSummary.categories.map(
            (category, index) => ({
              id: index,
              name: category.name,
              value: category.value,
              color: category.color,
              purchaseCost: 0,
              purchaseDate: netWorthSummary.date,
              profitPerYear: 0,
            })
          );
          this.totalPortfolioValue = netWorthSummary.totalValue;
        });
      this.httpClient
        .get<WalletValueInTime[]>('/api/wallet/net-worth/value-in-time')
        .subscribe(valueInTime => {
          this.walletValueInTime = valueInTime;
        });
      return;
    }

    if (this.selectedWallet.walletType === 'BOND') {
      this.httpClient
        .get<BondPosition[]>('/api/bond/position/list', {
          params: {
            'wallet.id': this.selectedWallet.id.toString(),
            date: this.selectedStockQuoteDate,
          },
        })
        .subscribe(bondPositions => {
          const assets = bondPositions
            .map(bondPosition => {
              const profitPerYear = this.calculatePerYearProfitNumber(
                bondPosition.currentTotalValue,
                bondPosition.purchaseCost,
                bondPosition.purchaseDate,
                this.selectedStockQuoteDate!
              );
              return {
                id: bondPosition.id,
                name: bondPosition.bondIssue.symbol,
                value: bondPosition.currentTotalValue,
                color: '#006CFF',
                purchaseCost: bondPosition.purchaseCost,
                purchaseDate: bondPosition.purchaseDate,
                profitPerYear,
              };
            })
            .sort((a, b) => b.value - a.value);

          this.dataSource.data = assets;
          this.totalPortfolioValue = assets.reduce(
            (sum, asset) => sum + asset.value,
            0
          );

          this.httpClient
            .get<
              WalletValueInTime[]
            >(`/api/bond/wallet/${this.selectedWallet!.id}/value-in-time`)
            .subscribe(valueInTime => {
              this.walletValueInTime = valueInTime;
            });
        });
      return;
    }

    this.httpClient
      .get<StockQuote>(`/api/stock-quote/${this.selectedStockQuoteDate}`)
      .subscribe(stockQuoteData => {
        this.httpClient
          .get<Transaction[]>('/api/transaction/list', {
            params: {'wallet.id': this.selectedWallet!.id.toString()},
          })
          .subscribe(transactions => {
            const transactionsUntilDate = transactions
              .filter(t => t.purchaseDate <= this.selectedStockQuoteDate!)
              .sort((a, b) => a.purchaseDate.localeCompare(b.purchaseDate));

            const holdingsMap = new Map<
              number,
              {
                stockId: number;
                stockName: string;
                stockColor: string;
                amount: number;
                totalPurchaseCost: number;
                earliestPurchaseDate: string;
              }
            >();

            transactionsUntilDate.forEach(transaction => {
              const existing = holdingsMap.get(transaction.stock.id);
              if (transaction.transactionType === 'BUY') {
                if (existing) {
                  if (existing.amount <= 0) {
                    existing.earliestPurchaseDate = transaction.purchaseDate;
                  }
                  existing.amount += transaction.amount;
                  existing.totalPurchaseCost += transaction.purchaseCost;
                  if (
                    transaction.purchaseDate < existing.earliestPurchaseDate
                  ) {
                    existing.earliestPurchaseDate = transaction.purchaseDate;
                  }
                } else {
                  holdingsMap.set(transaction.stock.id, {
                    stockId: transaction.stock.id,
                    stockName: transaction.stock.name,
                    stockColor: transaction.stock.color,
                    amount: transaction.amount,
                    totalPurchaseCost: transaction.purchaseCost,
                    earliestPurchaseDate: transaction.purchaseDate,
                  });
                }
              } else if (transaction.transactionType === 'SELL') {
                if (existing) {
                  existing.totalPurchaseCost =
                    existing.amount > 0
                      ? Math.max(
                          0,
                          existing.totalPurchaseCost *
                            (1 - transaction.amount / existing.amount)
                        )
                      : 0;
                  existing.amount -= transaction.amount;
                }
              }
            });

            const quotesByStockId = new Map(
              stockQuoteData.quotes.map(quote => [quote.stock.id, quote])
            );
            const assets = Array.from(holdingsMap.values())
              .map(holding => {
                if (holding.amount > 0) {
                  const quote = quotesByStockId.get(holding.stockId);
                  const value =
                    quote &&
                    quote.date.slice(0, 10) >= holding.earliestPurchaseDate
                      ? holding.amount * quote.value
                      : holding.totalPurchaseCost;
                  const profitPerYear = this.calculatePerYearProfitNumber(
                    value,
                    holding.totalPurchaseCost,
                    holding.earliestPurchaseDate,
                    this.selectedStockQuoteDate!
                  );
                  return {
                    id: holding.stockId,
                    name: holding.stockName,
                    value,
                    color: holding.stockColor,
                    purchaseCost: holding.totalPurchaseCost,
                    purchaseDate: holding.earliestPurchaseDate,
                    profitPerYear,
                  };
                }
                return null;
              })
              .filter((asset): asset is PortfolioAsset => asset !== null)
              .sort((a, b) => b.value - a.value);

            this.dataSource.data = assets;
            this.totalPortfolioValue = assets.reduce(
              (sum, asset) => sum + asset.value,
              0
            );

            this.httpClient
              .get<
                WalletValueInTime[]
              >(`/api/wallet/${this.selectedWallet!.id}/value-in-time`)
              .subscribe(valueInTime => {
                this.walletValueInTime = valueInTime;
              });
          });
      });
  }

  private calculatePerYearProfitNumber(
    value: number,
    purchaseCost: number,
    purchaseDateStr: string,
    valuationDateStr: string
  ): number {
    const purchaseDate = new Date(purchaseDateStr);
    const valuationDate = new Date(valuationDateStr);
    const diff = valuationDate.getTime() - purchaseDate.getTime();
    const rate = diff / 1000 / 60 / 60 / 24 / 365;
    if (rate < 1) {
      return ((value - purchaseCost) / purchaseCost) * 100;
    }
    return (((value - purchaseCost) / purchaseCost) * 100) / rate;
  }

  calculatePerYearProfit(asset: PortfolioAsset): string {
    return asset.profitPerYear.toFixed(2);
  }

  getProfitColor(profit: string): string {
    return parseFloat(profit) >= 0 ? 'green' : 'red';
  }
}
