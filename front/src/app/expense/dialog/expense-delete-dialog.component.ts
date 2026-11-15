import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';

interface ExpenseDeleteDialogData {
  title: string;
  name: string;
  details: string;
}

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'expense-delete-dialog',
  template: `
    <div class="delete-dialog">
      <div mat-dialog-title class="dialog-title">
        <h2>{{ data.title }}</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>
      <div mat-dialog-content>
        <p>
          Czy na pewno chcesz usunąć <strong>{{ data.name }}</strong
          >?
        </p>
        <p class="details">{{ data.details }}</p>
      </div>
      <div mat-dialog-actions class="dialog-actions">
        <button mat-button mat-dialog-close>Anuluj</button>
        <button mat-raised-button color="warn" (click)="confirm()">Usuń</button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .delete-dialog {
          min-width: 400px;
        }

        .dialog-title,
        .dialog-actions {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }

        .details {
          color: #64748b;
          font-size: 13px;
        }

        .dialog-actions {
          justify-content: flex-end;
          gap: 8px;
        }
      }
    `,
  ],
})
export class ExpenseDeleteDialog {
  constructor(
    private readonly dialogReference: MatDialogRef<ExpenseDeleteDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: ExpenseDeleteDialogData
  ) {}

  confirm(): void {
    this.dialogReference.close(true);
  }
}
