import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {Transaction} from '../../../api/transaction';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'transaction-delete-dialog',
  template: `
    <div class="transaction-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Usuń transakcję</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <p>Czy na pewno chcesz usunąć transakcję?</p>
        <div class="transaction-details">
          <p><strong>Aktywo:</strong> {{ data.stock.name }}</p>
          <p><strong>Portfel:</strong> {{ data.wallet.name }}</p>
          <p><strong>Ilość:</strong> {{ data.amount }}</p>
          <p><strong>Koszt zakupu:</strong> {{ data.purchaseCost }}</p>
        </div>
      </div>

      <div
        mat-dialog-actions
        fxLayout="row"
        fxLayoutGap="10px"
        fxLayoutAlign="end center"
      >
        <button mat-button mat-dialog-close class="cancel-button">
          Zamknij
        </button>
        <button
          mat-raised-button
          [mat-dialog-close]="true"
          class="delete-button"
        >
          Usuń
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .transaction-dialog {
          min-width: 450px;

          .dialog-content {
            padding: 20px 24px;

            .transaction-details {
              margin-top: 16px;
              padding: 16px;
              background-color: #f5f5f5;
              border-radius: 4px;

              p {
                margin: 8px 0;
              }
            }
          }

          .cancel-button {
            color: #666 !important;
            border: 1px solid rgba(0, 0, 0, 0.12) !important;
          }

          .delete-button {
            background-color: #dc3545 !important;
            color: white !important;

            &:hover {
              background-color: #c82333 !important;
            }
          }
        }
      }
    `,
  ],
})
export class TransactionDeleteDialog {
  constructor(
    public dialogRef: MatDialogRef<TransactionDeleteDialog>,
    @Inject(MAT_DIALOG_DATA) public data: Transaction
  ) {}
}
