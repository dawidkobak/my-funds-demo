import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';

interface ExpenseFolderDialogData {
  title: string;
  label: string;
  name: string;
  parentName: string | null;
}

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'expense-list-create-dialog',
  template: `
    <div class="folder-dialog">
      <div mat-dialog-title class="dialog-title">
        <h2>{{ data.title }}</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>
      <div mat-dialog-content>
        <p *ngIf="data.parentName">
          Folder nadrzędny: <strong>{{ data.parentName }}</strong>
        </p>
        <mat-form-field appearance="outline">
          <mat-label>{{ data.label }}</mat-label>
          <input matInput [(ngModel)]="name" (keyup.enter)="save()" />
        </mat-form-field>
      </div>
      <div mat-dialog-actions class="dialog-actions">
        <button mat-button mat-dialog-close>Anuluj</button>
        <button
          mat-raised-button
          color="primary"
          [disabled]="!name.trim()"
          (click)="save()"
        >
          Zapisz
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .folder-dialog {
          min-width: 380px;
        }

        .dialog-title,
        .dialog-actions {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }

        mat-form-field {
          width: 100%;
        }

        .dialog-actions {
          justify-content: flex-end;
          gap: 8px;
        }
      }
    `,
  ],
})
export class ExpenseListCreateDialog {
  name: string;

  constructor(
    private readonly dialogReference: MatDialogRef<ExpenseListCreateDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: ExpenseFolderDialogData
  ) {
    this.name = data.name;
  }

  save(): void {
    const normalizedName = this.name.trim();
    if (normalizedName) {
      this.dialogReference.close(normalizedName);
    }
  }
}
