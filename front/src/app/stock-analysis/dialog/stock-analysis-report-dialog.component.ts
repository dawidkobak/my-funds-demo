import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {marked} from 'marked';
import {StockAnalysis} from '../../../api/stock-analysis';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'stock-analysis-report-dialog',
  template: `
    <div class="stock-analysis-report-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <div>
          <h2>{{ data.companyName }}</h2>
          <div class="subtitle">
            {{ data.ticker || 'Brak tickera' }} · {{ data.status }}
          </div>
        </div>
        <button mat-icon-button mat-dialog-close aria-label="Zamknij raport">
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <div
          *ngIf="data.reportMarkdown; else emptyReport"
          class="analysis-report"
          [innerHTML]="reportHtml"
        ></div>

        <ng-template #emptyReport>
          <div class="empty-report">Brak gotowego raportu dla tej analizy.</div>
        </ng-template>
      </div>

      <div mat-dialog-actions fxLayout="row" fxLayoutAlign="end center">
        <button mat-raised-button mat-dialog-close class="close-button">
          Zamknij
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;

        .stock-analysis-report-dialog {
          width: 100%;

          .subtitle {
            color: rgba(0, 0, 0, 0.55);
            font-size: 13px;
            margin-top: 2px;
          }

          .dialog-content {
            max-height: 70vh;
            padding: 20px 24px;
          }

          .analysis-report {
            color: rgba(0, 0, 0, 0.87);
            line-height: 1.55;
            overflow-wrap: anywhere;

            ::ng-deep {
              h1,
              h2,
              h3 {
                margin: 22px 0 10px;
              }

              h1:first-child,
              h2:first-child,
              h3:first-child {
                margin-top: 0;
              }

              table {
                width: 100%;
                border-collapse: collapse;
                margin: 16px 0;
              }

              th,
              td {
                border: 1px solid #e0e0e0;
                padding: 8px 10px;
                text-align: left;
              }

              th {
                background-color: #f7f7f7;
              }

              code {
                background-color: #f4f4f4;
                padding: 2px 4px;
                border-radius: 4px;
              }
            }
          }

          .empty-report {
            color: rgba(0, 0, 0, 0.6);
            padding: 32px 0;
            text-align: center;
          }

          .close-button {
            background-color: #0864dc !important;
            color: white !important;
          }
        }
      }
    `,
  ],
})
export class StockAnalysisReportDialog {
  reportHtml: string;

  constructor(@Inject(MAT_DIALOG_DATA) public data: StockAnalysis) {
    this.reportHtml = marked.parse(data.reportMarkdown || '', {
      async: false,
    }) as string;
  }
}
