import {ChangeDetectionStrategy} from '@angular/core';
import {Component} from '@angular/core';
import {MatDialogRef} from '@angular/material/dialog';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'piggy-bank-create-dialog',
  template: `
    <div class="piggy-bank-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Nowa skarbonka</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Cel / Nazwa skarbonki</mat-label>
          <input matInput [(ngModel)]="piggyBankName" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Kwota do zebrania</mat-label>
          <input matInput type="number" [(ngModel)]="aimAmount" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Zebrano</mat-label>
          <input matInput type="number" [(ngModel)]="currentAmount" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Data końcowa (YYYY-MM-DD)</mat-label>
          <input matInput type="date" [(ngModel)]="dueDate" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Link (opcjonalnie)</mat-label>
          <input matInput [(ngModel)]="link" />
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
        .piggy-bank-dialog {
          min-width: 450px;

          .dialog-content {
            padding: 20px 24px;
          }

          .full-width {
            width: 100%;
            margin-bottom: 16px;
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
export class PiggyBankCreateDialog {
  piggyBankName = '';
  aimAmount = 0;
  currentAmount = 0;
  dueDate = '';
  link = '';

  constructor(public dialogRef: MatDialogRef<PiggyBankCreateDialog>) {}

  isValid(): boolean {
    return (
      this.piggyBankName.trim() !== '' &&
      this.aimAmount > 0 &&
      this.currentAmount >= 0 &&
      this.dueDate !== ''
    );
  }

  onSave(): void {
    if (this.isValid()) {
      this.dialogRef.close({
        name: this.piggyBankName,
        aimAmount: this.aimAmount,
        currentAmount: this.currentAmount,
        dueDate: this.dueDate,
        link: this.link || undefined,
      });
    }
  }
}
