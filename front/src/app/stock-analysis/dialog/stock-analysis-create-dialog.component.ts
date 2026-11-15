import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {Stock} from '../../../api/stock';
import {StockAnalysisCreatePayload} from '../../../api/stock-analysis';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'stock-analysis-create-dialog',
  template: `
    <div class="stock-analysis-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Nowa analiza</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Aktywo z portfela</mat-label>
          <mat-select
            [(ngModel)]="selectedStockId"
            (ngModelChange)="onStockChange()"
          >
            <mat-option [value]="null">Brak</mat-option>
            <mat-option *ngFor="let stock of data.stocks" [value]="stock.id">
              {{ stock.name }}
            </mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nazwa spółki</mat-label>
          <input matInput [(ngModel)]="companyName" />
        </mat-form-field>

        <div fxLayout="row" fxLayoutGap="16px">
          <mat-form-field appearance="outline" class="half-width">
            <mat-label>Ticker</mat-label>
            <input matInput [(ngModel)]="ticker" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="half-width">
            <mat-label>Kraj</mat-label>
            <input matInput [(ngModel)]="country" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Branża</mat-label>
          <input matInput [(ngModel)]="industry" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Dane wejściowe Markdown</mat-label>
          <textarea matInput rows="14" [(ngModel)]="sourceMarkdown"></textarea>
        </mat-form-field>
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
          Dodaj
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .stock-analysis-dialog {
          width: min(760px, 90vw);

          .dialog-content {
            padding: 20px 24px;
          }

          .full-width {
            width: 100%;
            margin-bottom: 16px;
          }

          .half-width {
            width: 50%;
            margin-bottom: 16px;
          }

          textarea {
            font-family: monospace;
            line-height: 1.45;
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
export class StockAnalysisCreateDialog {
  selectedStockId: number | null = null;
  companyName = '';
  ticker = '';
  country = '';
  industry = '';
  sourceMarkdown = '';

  constructor(
    public dialogRef: MatDialogRef<StockAnalysisCreateDialog>,
    @Inject(MAT_DIALOG_DATA) public data: {stocks: Stock[]}
  ) {}

  onStockChange(): void {
    const selectedStock = this.data.stocks.find(
      stock => stock.id === this.selectedStockId
    );

    if (selectedStock && this.companyName.trim() === '') {
      this.companyName = selectedStock.name;
    }
  }

  isValid(): boolean {
    return this.companyName.trim() !== '' && this.sourceMarkdown.trim() !== '';
  }

  onSave(): void {
    if (this.isValid()) {
      const payload: StockAnalysisCreatePayload = {
        stock: this.selectedStockId ? {id: this.selectedStockId} : null,
        companyName: this.companyName.trim(),
        ticker: this.ticker.trim() || null,
        country: this.country.trim() || null,
        industry: this.industry.trim() || null,
        sourceMarkdown: this.sourceMarkdown,
      };

      this.dialogRef.close(payload);
    }
  }
}
