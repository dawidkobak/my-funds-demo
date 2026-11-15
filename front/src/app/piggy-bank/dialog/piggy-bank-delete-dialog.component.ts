import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {PiggyBank} from '../../../api/piggy-bank';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'piggy-bank-delete-dialog',
  template: `
    <div class="piggy-bank-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Usuń skarbonkę</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <p>
          Czy na pewno chcesz usunąć skarbonkę <strong>{{ data.name }}</strong
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
        .piggy-bank-dialog {
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
export class PiggyBankDeleteDialog {
  constructor(
    public dialogRef: MatDialogRef<PiggyBankDeleteDialog>,
    @Inject(MAT_DIALOG_DATA) public data: PiggyBank
  ) {}
}
