import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MatDialogRef, MAT_DIALOG_DATA} from '@angular/material/dialog';
import {Expense} from '../../../api/expense';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'expense-modify-dialog',
  template: `
    <div class="expense-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Edytuj wydatek</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nazwa wydatku</mat-label>
          <input matInput [(ngModel)]="expenseName" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Kwota</mat-label>
          <input
            matInput
            type="number"
            [(ngModel)]="expenseValue"
            step="0.01"
          />
          <span matSuffix>zł</span>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Data (YYYY-MM-DD)</mat-label>
          <input matInput type="date" [(ngModel)]="expenseDate" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Kolor</mat-label>
          <input matInput type="color" [(ngModel)]="expenseColor" />
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
          Zapisz
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .expense-dialog {
          min-width: 450px;

          .dialog-content {
            padding: 20px 24px;
          }

          .full-width {
            width: 100%;
            margin-bottom: 16px;
          }

          input[type='color'] {
            height: 40px;
            cursor: pointer;
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
export class ExpenseModifyDialog {
  expenseName: string;
  expenseValue: number;
  expenseDate: string;
  expenseColor: string;

  constructor(
    public dialogRef: MatDialogRef<ExpenseModifyDialog>,
    @Inject(MAT_DIALOG_DATA) public data: Expense
  ) {
    this.expenseName = data.name;
    this.expenseValue = data.value;
    this.expenseDate = data.createdAt;
    this.expenseColor = data.color || '#4a90d9';
  }

  isValid(): boolean {
    return this.expenseName.trim() !== '' && this.expenseValue > 0;
  }

  onSave(): void {
    if (this.isValid()) {
      this.dialogRef.close({
        name: this.expenseName,
        value: this.expenseValue,
        createdAt: this.expenseDate,
        color: this.expenseColor,
      });
    }
  }
}
