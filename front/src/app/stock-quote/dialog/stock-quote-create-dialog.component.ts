import {ChangeDetectionStrategy} from '@angular/core';
import {Component, OnInit} from '@angular/core';
import {MatDialogRef} from '@angular/material/dialog';
import {HttpClient} from '@angular/common/http';
import {Stock} from '../../../api/stock';

interface QuoteInput {
  stockId: number;
  name: string;
  value: number | null;
}

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'stock-quote-create-dialog',
  template: `
    <div class="stock-quote-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Nowe notowanie</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Data notowania</mat-label>
          <input
            matInput
            type="date"
            [(ngModel)]="date"
            placeholder="np. 2021-06-19"
          />
        </mat-form-field>

        <div
          *ngFor="let quote of quotes"
          fxLayout="row"
          fxLayoutGap="10px"
          class="quote-row"
        >
          <mat-form-field appearance="outline" fxFlex="50">
            <mat-label>Aktywo</mat-label>
            <input matInput [value]="quote.name" disabled />
          </mat-form-field>
          <mat-form-field appearance="outline" fxFlex="50">
            <mat-label>Wartość</mat-label>
            <input matInput type="number" [(ngModel)]="quote.value" />
          </mat-form-field>
        </div>
      </div>

      <div
        mat-dialog-actions
        fxLayout="row"
        fxLayoutGap="10px"
        fxLayoutAlign="end center"
      >
        <button mat-button mat-dialog-close class="cancel-button">
          Anuluj
        </button>
        <button
          mat-raised-button
          (click)="onSave()"
          class="save-button"
          [disabled]="!isValid()"
        >
          Utwórz
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .stock-quote-dialog {
          min-width: 500px;
          max-height: 600px;

          .dialog-content {
            padding: 20px 24px;
            max-height: 500px;
            overflow-y: auto;
          }

          .full-width {
            width: 100%;
            margin-bottom: 16px;
          }

          .quote-row {
            margin-bottom: 8px;
          }

          .cancel-button {
            color: #666 !important;
            border: 1px solid rgba(0, 0, 0, 0.12) !important;
          }

          .save-button {
            background-color: #0864dc !important;
            color: white !important;

            &:hover {
              background-color: #0652b8 !important;
            }

            &:disabled {
              background-color: #ccc !important;
              color: #888 !important;
            }
          }
        }
      }
    `,
  ],
})
export class StockQuoteCreateDialog implements OnInit {
  date = '';
  quotes: QuoteInput[] = [];

  constructor(
    public dialogRef: MatDialogRef<StockQuoteCreateDialog>,
    private httpClient: HttpClient
  ) {}

  ngOnInit(): void {
    this.httpClient.get<Stock[]>('/api/stock/list').subscribe(stocks => {
      this.quotes = stocks.map(stock => ({
        stockId: stock.id,
        name: stock.name,
        value: null,
      }));
    });
  }

  isValid(): boolean {
    return (
      this.date.trim() !== '' &&
      this.quotes.some(quote => quote.value !== null && quote.value > 0)
    );
  }

  onSave(): void {
    if (this.isValid()) {
      this.dialogRef.close({
        date: this.date,
        quotes: this.quotes.filter(quote => quote.value !== null),
      });
    }
  }
}
