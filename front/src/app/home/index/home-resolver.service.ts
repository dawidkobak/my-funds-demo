import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {forkJoin, Observable} from 'rxjs';
import {Wallet} from '../../../api/wallet';
import {Stock} from '../../../api/stock';
import {Transaction} from '../../../api/transaction';
import {PiggyBank} from '../../../api/piggy-bank';
import {StockAnalysis} from '../../../api/stock-analysis';

export type Data = {
  wallets: Wallet[];
  stocks: Stock[];
  transactions: Transaction[];
  stockAnalyses: StockAnalysis[];
  piggyBanks: PiggyBank[];
  stockQuoteList: string[];
};

@Injectable({
  providedIn: 'root',
})
export class HomeResolver {
  constructor(private httpClient: HttpClient) {}

  resolve(): Observable<Data> {
    return forkJoin({
      wallets: this.httpClient.get<Wallet[]>('/api/wallet/list'),
      stocks: this.httpClient.get<Stock[]>('/api/stock/list'),
      transactions: this.httpClient.get<Transaction[]>('/api/transaction/list'),
      stockAnalyses: this.httpClient.get<StockAnalysis[]>(
        '/api/stock-analysis/list'
      ),
      piggyBanks: this.httpClient.get<PiggyBank[]>('/api/piggy-bank/list'),
      stockQuoteList: this.httpClient.get<string[]>('/api/stock-quote/list'),
    });
  }
}
