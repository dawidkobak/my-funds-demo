import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
  ExpenseBootstrap,
  ExpenseCountingStatus,
  ExpenseItem,
} from '../../../api/expense';

interface ExpenseDialogData {
  bootstrapData: ExpenseBootstrap;
  folderId: number | null;
  expenseItem: ExpenseItem | null;
}

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'expense-create-dialog',
  template: `
    <div class="expense-dialog">
      <div mat-dialog-title class="dialog-title">
        <h2>{{ data.expenseItem ? 'Edytuj wydatek' : 'Nowy wydatek' }}</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <div class="form-grid">
          <mat-form-field appearance="outline" class="span-two">
            <mat-label>Nazwa</mat-label>
            <input matInput [(ngModel)]="name" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Kwota ze znakiem</mat-label>
            <input matInput type="number" step="0.01" [(ngModel)]="amount" />
            <mat-hint>Wartość ujemna zmniejsza wydatki</mat-hint>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Waluta</mat-label>
            <mat-select [(ngModel)]="currencyId">
              <mat-option
                *ngFor="let currency of data.bootstrapData.currencies"
                [value]="currency.id"
              >
                {{ currency.code }} — {{ currency.name }}
              </mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Folder</mat-label>
            <mat-select [(ngModel)]="folderId">
              <mat-option
                *ngFor="let folder of data.bootstrapData.folders"
                [value]="folder.id"
              >
                {{ folder.depth === 1 ? '— ' : '' }}{{ folder.name }}
              </mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Źródło płatności</mat-label>
            <mat-select [(ngModel)]="paymentSourceId">
              <mat-option
                *ngFor="let source of activePaymentSources"
                [value]="source.id"
              >
                {{ source.name }}
              </mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Kategoria</mat-label>
            <mat-select [(ngModel)]="categoryId">
              <mat-option [value]="null">Bez kategorii</mat-option>
              <mat-option
                *ngFor="let category of activeCategories"
                [value]="category.id"
              >
                {{ category.name }}
              </mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Tagi</mat-label>
            <mat-select [(ngModel)]="tagIds" multiple>
              <mat-option *ngFor="let tag of activeTags" [value]="tag.id">
                {{ tag.name }}
              </mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Miesiąc</mat-label>
            <input matInput type="month" [(ngModel)]="month" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Dokładna data (opcjonalnie)</mat-label>
            <input matInput type="date" [(ngModel)]="date" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Typ wpisu</mat-label>
            <mat-select [(ngModel)]="entryType">
              <mat-option value="expense">Wydatek</mat-option>
              <mat-option value="refund">Zwrot</mat-option>
              <mat-option value="adjustment">Korekta</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Uwzględnianie w zestawieniach</mat-label>
            <mat-select [(ngModel)]="countingStatus">
              <mat-option value="included">Uwzględniaj</mat-option>
              <mat-option value="excluded_internal_transfer"
                >Przelew własny</mat-option
              >
              <mat-option value="excluded_business"
                >Koszt działalności</mat-option
              >
              <mat-option value="excluded_investment">Inwestycja</mat-option>
              <mat-option value="excluded_cash_transfer"
                >Wypłata gotówki</mat-option
              >
              <mat-option value="excluded_reimbursed"
                >Wydatek zwrócony</mat-option
              >
              <mat-option value="excluded_duplicate">Duplikat</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="span-two">
            <mat-label>Notatka</mat-label>
            <textarea matInput rows="3" [(ngModel)]="note"></textarea>
          </mat-form-field>
        </div>
      </div>

      <div mat-dialog-actions class="dialog-actions">
        <button mat-button mat-dialog-close>Anuluj</button>
        <button
          mat-raised-button
          color="primary"
          [disabled]="!isValid"
          (click)="save()"
        >
          {{ data.expenseItem ? 'Zapisz' : 'Dodaj' }}
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .expense-dialog {
          min-width: 0;
        }

        .dialog-title,
        .dialog-actions {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }

        .dialog-title h2 {
          margin: 0;
        }

        .dialog-content {
          padding-top: 12px;
        }

        .form-grid {
          display: grid;
          grid-template-columns: repeat(2, minmax(0, 1fr));
          gap: 8px 14px;
        }

        .span-two {
          grid-column: span 2;
        }

        .dialog-actions {
          justify-content: flex-end;
          gap: 8px;
        }

        @media (max-width: 620px) {
          .form-grid {
            grid-template-columns: 1fr;
          }

          .span-two {
            grid-column: span 1;
          }
        }
      }
    `,
  ],
})
export class ExpenseCreateDialog {
  name = '';
  note = '';
  amount = 0;
  currencyId: number | null = null;
  folderId: number | null = null;
  paymentSourceId: number | null = null;
  categoryId: number | null = null;
  tagIds: number[] = [];
  month = new Date().toISOString().slice(0, 7);
  date = '';
  entryType: 'expense' | 'refund' | 'adjustment' = 'expense';
  countingStatus: ExpenseCountingStatus = 'included';

  constructor(
    readonly dialogReference: MatDialogRef<ExpenseCreateDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: ExpenseDialogData
  ) {
    const expenseItem = data.expenseItem;
    this.folderId = expenseItem?.folder.id ?? data.folderId;
    this.currencyId =
      expenseItem?.currency.id ??
      data.bootstrapData.currencies.find(currency => currency.code === 'PLN')
        ?.id ??
      data.bootstrapData.currencies[0]?.id ??
      null;
    this.paymentSourceId =
      expenseItem?.paymentSource.id ??
      data.bootstrapData.paymentSources.find(source => source.type === 'manual')
        ?.id ??
      data.bootstrapData.paymentSources.find(source => source.type === 'legacy')
        ?.id ??
      data.bootstrapData.paymentSources[0]?.id ??
      null;
    if (expenseItem) {
      this.name = expenseItem.name;
      this.note = expenseItem.note ?? '';
      this.amount = expenseItem.amount;
      this.categoryId = expenseItem.category?.id ?? null;
      this.tagIds = expenseItem.tags.map(tag => tag.id);
      this.month = expenseItem.month.slice(0, 7);
      this.date = expenseItem.date ?? '';
      this.entryType = expenseItem.type;
      this.countingStatus = expenseItem.countingStatus;
    }
  }

  get activeCategories() {
    return this.data.bootstrapData.categories.filter(
      category => category.isActive || category.id === this.categoryId
    );
  }

  get activeTags() {
    return this.data.bootstrapData.tags.filter(
      tag => tag.isActive || this.tagIds.includes(tag.id)
    );
  }

  get activePaymentSources() {
    return this.data.bootstrapData.paymentSources.filter(
      source => source.isActive || source.id === this.paymentSourceId
    );
  }

  get isValid(): boolean {
    return (
      this.name.trim().length > 0 &&
      Number.isFinite(this.amount) &&
      this.amount !== 0 &&
      this.folderId !== null &&
      this.paymentSourceId !== null &&
      this.currencyId !== null &&
      /^\d{4}-\d{2}$/.test(this.month)
    );
  }

  save(): void {
    if (!this.isValid) {
      return;
    }
    this.dialogReference.close({
      folderId: this.folderId,
      paymentSourceId: this.paymentSourceId,
      categoryId: this.categoryId,
      currencyId: this.currencyId,
      name: this.name.trim(),
      note: this.note.trim() || null,
      amount: this.amount,
      month: `${this.month}-01`,
      date: this.date || null,
      type: this.entryType,
      countingStatus: this.countingStatus,
      tagIds: this.tagIds,
    });
  }
}
