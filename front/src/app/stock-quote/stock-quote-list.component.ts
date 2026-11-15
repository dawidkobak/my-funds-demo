import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {Component, OnInit} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {StockQuote} from '../../api/core/stock-quote';
import {StockQuoteCreateDialog} from './dialog/stock-quote-create-dialog.component';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'stock-quote-list',
  templateUrl: './stock-quote-list.component.html',
  styleUrls: ['./stock-quote-list.component.scss'],
})
export class StockQuoteListComponent implements OnInit {
  public readonly staticDemo = environment.staticDemo;
  public stockQuoteList: string[] = [];
  public selectedStockQuote: string | null = null;
  public stockQuote: StockQuote | null = null;

  constructor(
    private httpClient: HttpClient,
    private matDialog: MatDialog,
    private matSnackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.httpClient
      .get<string[]>('/api/stock-quote/list')
      .subscribe(stockQuoteList => {
        this.stockQuoteList = stockQuoteList;
        if (stockQuoteList.length > 0) {
          this.selectedStockQuote = stockQuoteList[0];
          this.fetchStockQuote(stockQuoteList[0]);
        }
      });
  }

  fetchStockQuote(stockQuote: string): void {
    this.httpClient
      .get<StockQuote>(`/api/stock-quote/${stockQuote}`)
      .subscribe(response => {
        this.stockQuote = response;
      });
  }

  openCreateDialog(): void {
    const dialogRef = this.matDialog.open(StockQuoteCreateDialog);

    dialogRef.afterClosed().subscribe(newStockQuoteData => {
      if (newStockQuoteData) {
        this.httpClient
          .post<StockQuote>('/api/stock-quote/create', newStockQuoteData)
          .subscribe(() => {
            this.refreshStockQuoteList();
            this.matSnackBar.open('Notowanie zostało utworzone', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  private refreshStockQuoteList(): void {
    this.httpClient
      .get<string[]>('/api/stock-quote/list')
      .subscribe(stockQuoteList => {
        this.stockQuoteList = stockQuoteList;
        if (stockQuoteList.length > 0) {
          this.selectedStockQuote = stockQuoteList[0];
          this.fetchStockQuote(stockQuoteList[0]);
        }
      });
  }
}
