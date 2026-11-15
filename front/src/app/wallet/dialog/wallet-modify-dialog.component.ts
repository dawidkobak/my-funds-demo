import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {Wallet} from '../../../api/wallet';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'wallet-modify-dialog',
  template: `
    <div class="wallet-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Edytuj portfel</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nazwa portfela</mat-label>
          <input matInput [(ngModel)]="walletName" />
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
          [mat-dialog-close]="walletName"
          class="save-button"
          [disabled]="!walletName || walletName.trim() === ''"
        >
          Zapisz
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

          .full-width {
            width: 100%;
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
export class WalletModifyDialog {
  walletName: string;

  constructor(
    public dialogRef: MatDialogRef<WalletModifyDialog>,
    @Inject(MAT_DIALOG_DATA) public data: Wallet
  ) {
    this.walletName = data.name;
  }
}
