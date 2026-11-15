import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {Stock} from '../../../api/stock';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'stock-modify-dialog',
  template: `
    <div class="stock-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Edytuj aktywo</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nazwa aktywa</mat-label>
          <input matInput [(ngModel)]="stockName" />
        </mat-form-field>

        <div class="color-picker-container">
          <label class="color-label">Kolor</label>
          <div fxLayout="row" fxLayoutAlign="start center" fxLayoutGap="16px">
            <input type="color" [(ngModel)]="color" class="color-input" />
            <div
              class="color-preview"
              [ngStyle]="{'background-color': color}"
            ></div>
            <span class="color-value">{{ color }}</span>
          </div>
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
          Zapisz
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .stock-dialog {
          min-width: 450px;

          .dialog-content {
            padding: 20px 24px;
          }

          .full-width {
            width: 100%;
            margin-bottom: 16px;
          }

          .color-picker-container {
            margin-bottom: 16px;

            .color-label {
              display: block;
              font-size: 14px;
              color: rgba(0, 0, 0, 0.6);
              margin-bottom: 8px;
            }

            .color-input {
              width: 60px;
              height: 40px;
              border: 1px solid rgba(0, 0, 0, 0.12);
              border-radius: 4px;
              cursor: pointer;

              &::-webkit-color-swatch-wrapper {
                padding: 4px;
              }

              &::-webkit-color-swatch {
                border: none;
                border-radius: 2px;
              }
            }

            .color-preview {
              width: 40px;
              height: 40px;
              border: 1px solid rgba(0, 0, 0, 0.12);
              border-radius: 4px;
            }

            .color-value {
              font-family: monospace;
              font-size: 14px;
              color: rgba(0, 0, 0, 0.87);
            }
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
export class StockModifyDialog {
  stockName: string;
  color: string;

  constructor(
    public dialogRef: MatDialogRef<StockModifyDialog>,
    @Inject(MAT_DIALOG_DATA) public data: Stock
  ) {
    this.stockName = data.name;
    this.color = data.color;
  }

  isValid(): boolean {
    return this.stockName.trim() !== '' && this.color.trim() !== '';
  }

  onSave(): void {
    if (this.isValid()) {
      this.dialogRef.close({
        id: this.data.id,
        name: this.stockName,
        color: this.color,
      });
    }
  }
}
