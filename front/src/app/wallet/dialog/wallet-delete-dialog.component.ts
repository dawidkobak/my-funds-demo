import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {Wallet} from '../../../api/wallet';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'wallet-delete-dialog',
  template: `
    <div class="wallet-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Usuń portfel</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <p>
          Czy na pewno chcesz usunąć portfel <strong>{{ data.name }}</strong
          >?
        </p>
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
        .wallet-dialog {
          min-width: 450px;

          .dialog-content {
            padding: 20px 24px;
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
export class WalletDeleteDialog {
  constructor(
    public dialogRef: MatDialogRef<WalletDeleteDialog>,
    @Inject(MAT_DIALOG_DATA) public data: Wallet
  ) {}
}
