import {ChangeDetectionStrategy} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
  ExpenseBootstrap,
  ExpenseCategory,
  ExpensePaymentSource,
  ExpenseTag,
} from '../../../api/expense';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'expense-manage-dialog',
  template: `
    <div class="manage-dialog">
      <div mat-dialog-title class="dialog-title">
        <div>
          <h2>Kategorie, tagi i źródła</h2>
          <p>Nieaktywne pozycje pozostają przy historycznych wydatkach.</p>
        </div>
        <button mat-icon-button (click)="close()">
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <mat-tab-group>
        <mat-tab label="Kategorie">
          <div class="tab-content">
            <div
              class="configuration-row category-row"
              *ngFor="let category of categories"
            >
              <input
                class="color-input"
                type="color"
                [(ngModel)]="category.color"
              />
              <mat-form-field appearance="outline">
                <mat-label>Nazwa</mat-label>
                <input matInput [(ngModel)]="category.name" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Ikona FontAwesome</mat-label>
                <input matInput [(ngModel)]="category.icon" />
              </mat-form-field>
              <mat-checkbox [(ngModel)]="category.isActive"
                >Aktywna</mat-checkbox
              >
              <button
                mat-icon-button
                matTooltip="Zapisz"
                (click)="saveCategory(category)"
              >
                <fa-icon [icon]="['fas', 'floppy-disk']"></fa-icon>
              </button>
              <button
                mat-icon-button
                color="warn"
                matTooltip="Dezaktywuj"
                [disabled]="!category.isActive"
                (click)="deactivate('category', category.id)"
              >
                <fa-icon [icon]="['fas', 'trash']"></fa-icon>
              </button>
            </div>
            <div class="configuration-row category-row new-row">
              <input
                class="color-input"
                type="color"
                [(ngModel)]="newCategory.color"
              />
              <mat-form-field appearance="outline">
                <mat-label>Nowa kategoria</mat-label>
                <input matInput [(ngModel)]="newCategory.name" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Ikona</mat-label>
                <input matInput [(ngModel)]="newCategory.icon" />
              </mat-form-field>
              <span></span>
              <button
                mat-raised-button
                color="primary"
                [disabled]="!newCategory.name.trim()"
                (click)="addCategory()"
              >
                Dodaj
              </button>
            </div>
          </div>
        </mat-tab>

        <mat-tab label="Tagi">
          <div class="tab-content">
            <div class="configuration-row simple-row" *ngFor="let tag of tags">
              <mat-form-field appearance="outline">
                <mat-label>Nazwa</mat-label>
                <input matInput [(ngModel)]="tag.name" />
              </mat-form-field>
              <mat-checkbox [(ngModel)]="tag.isActive">Aktywny</mat-checkbox>
              <button
                mat-icon-button
                matTooltip="Zapisz"
                (click)="saveTag(tag)"
              >
                <fa-icon [icon]="['fas', 'floppy-disk']"></fa-icon>
              </button>
              <button
                mat-icon-button
                color="warn"
                [disabled]="!tag.isActive"
                (click)="deactivate('tag', tag.id)"
              >
                <fa-icon [icon]="['fas', 'trash']"></fa-icon>
              </button>
            </div>
            <div class="configuration-row simple-row new-row">
              <mat-form-field appearance="outline">
                <mat-label>Nowy tag</mat-label>
                <input matInput [(ngModel)]="newTagName" />
              </mat-form-field>
              <span></span>
              <button
                mat-raised-button
                color="primary"
                [disabled]="!newTagName.trim()"
                (click)="addTag()"
              >
                Dodaj
              </button>
            </div>
          </div>
        </mat-tab>

        <mat-tab label="Źródła płatności">
          <div class="tab-content">
            <div
              class="configuration-row source-row"
              *ngFor="let source of paymentSources"
            >
              <mat-form-field appearance="outline">
                <mat-label>Nazwa</mat-label>
                <input matInput [(ngModel)]="source.name" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Typ</mat-label>
                <mat-select [(ngModel)]="source.type">
                  <mat-option value="bank">Bank</mat-option>
                  <mat-option value="cash">Gotówka</mat-option>
                  <mat-option value="manual">Ręczne</mat-option>
                  <mat-option value="legacy">Legacy</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-checkbox [(ngModel)]="source.isActive">Aktywne</mat-checkbox>
              <button
                mat-icon-button
                matTooltip="Zapisz"
                (click)="savePaymentSource(source)"
              >
                <fa-icon [icon]="['fas', 'floppy-disk']"></fa-icon>
              </button>
              <button
                mat-icon-button
                color="warn"
                [disabled]="!source.isActive"
                (click)="deactivate('payment-source', source.id)"
              >
                <fa-icon [icon]="['fas', 'trash']"></fa-icon>
              </button>
            </div>
            <div class="configuration-row source-row new-row">
              <mat-form-field appearance="outline">
                <mat-label>Nowe źródło</mat-label>
                <input matInput [(ngModel)]="newPaymentSource.name" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Typ</mat-label>
                <mat-select [(ngModel)]="newPaymentSource.type">
                  <mat-option value="bank">Bank</mat-option>
                  <mat-option value="cash">Gotówka</mat-option>
                  <mat-option value="manual">Ręczne</mat-option>
                  <mat-option value="legacy">Legacy</mat-option>
                </mat-select>
              </mat-form-field>
              <span></span>
              <button
                mat-raised-button
                color="primary"
                [disabled]="!newPaymentSource.name.trim()"
                (click)="addPaymentSource()"
              >
                Dodaj
              </button>
            </div>
          </div>
        </mat-tab>
      </mat-tab-group>

      <div mat-dialog-actions class="dialog-actions">
        <button mat-raised-button color="primary" (click)="close()">
          Gotowe
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        .manage-dialog {
          min-width: 0;
        }

        .dialog-title,
        .dialog-actions,
        .configuration-row {
          display: flex;
          align-items: center;
        }

        .dialog-title {
          justify-content: space-between;
        }

        .dialog-title h2,
        .dialog-title p {
          margin: 0;
        }

        .dialog-title p {
          color: #64748b;
          font-size: 12px;
        }

        .tab-content {
          min-height: 360px;
          max-height: 530px;
          overflow-y: auto;
          padding: 18px 4px;
        }

        .configuration-row {
          gap: 10px;
          border-bottom: 1px solid #eef0f2;
          padding: 7px 0;
        }

        .category-row {
          display: grid;
          grid-template-columns: 42px minmax(150px, 1.3fr) minmax(150px, 1fr) 95px 42px 42px;
        }

        .simple-row {
          display: grid;
          grid-template-columns: minmax(200px, 1fr) 110px 42px 42px;
        }

        .source-row {
          display: grid;
          grid-template-columns: minmax(190px, 1fr) 150px 100px 42px 42px;
        }

        mat-form-field {
          width: 100%;
        }

        .color-input {
          width: 38px;
          height: 38px;
          border: 0;
          background: transparent;
        }

        .new-row {
          margin-top: 12px;
          border: 1px dashed #94a3b8;
          border-radius: 8px;
          background: #f8fafc;
          padding: 10px;
        }

        .dialog-actions {
          justify-content: flex-end;
        }

        @media (max-width: 760px) {
          .category-row,
          .simple-row,
          .source-row {
            grid-template-columns: 1fr;
          }
        }
      }
    `,
  ],
})
export class ExpenseManageDialog {
  categories: ExpenseCategory[];
  tags: ExpenseTag[];
  paymentSources: ExpensePaymentSource[];
  newCategory = {name: '', color: '#78716C', icon: 'ellipsis'};
  newTagName = '';
  newPaymentSource: {name: string; type: ExpensePaymentSource['type']} = {
    name: '',
    type: 'manual',
  };
  changed = false;

  constructor(
    private readonly httpClient: HttpClient,
    private readonly dialogReference: MatDialogRef<ExpenseManageDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: ExpenseBootstrap
  ) {
    this.categories = data.categories.map(category => ({...category}));
    this.tags = data.tags.map(tag => ({...tag}));
    this.paymentSources = data.paymentSources.map(source => ({...source}));
  }

  saveCategory(category: ExpenseCategory): void {
    this.httpClient.post('/api/expenses/category', category).subscribe(() => {
      this.changed = true;
    });
  }

  addCategory(): void {
    this.httpClient
      .post<{id: number}>('/api/expenses/category', {
        ...this.newCategory,
        isActive: true,
      })
      .subscribe(response => {
        this.categories.push({
          id: response.id,
          ...this.newCategory,
          isActive: true,
        });
        this.newCategory = {name: '', color: '#78716C', icon: 'ellipsis'};
        this.changed = true;
      });
  }

  saveTag(tag: ExpenseTag): void {
    this.httpClient.post('/api/expenses/tag', tag).subscribe(() => {
      this.changed = true;
    });
  }

  addTag(): void {
    this.httpClient
      .post<{id: number}>('/api/expenses/tag', {
        name: this.newTagName.trim(),
        isActive: true,
      })
      .subscribe(response => {
        this.tags.push({
          id: response.id,
          name: this.newTagName.trim(),
          isActive: true,
        });
        this.newTagName = '';
        this.changed = true;
      });
  }

  savePaymentSource(paymentSource: ExpensePaymentSource): void {
    this.httpClient
      .post('/api/expenses/payment-source', paymentSource)
      .subscribe(() => {
        this.changed = true;
      });
  }

  addPaymentSource(): void {
    this.httpClient
      .post<{id: number}>('/api/expenses/payment-source', {
        ...this.newPaymentSource,
        isActive: true,
      })
      .subscribe(response => {
        this.paymentSources.push({
          id: response.id,
          ...this.newPaymentSource,
          isActive: true,
        });
        this.newPaymentSource = {name: '', type: 'manual'};
        this.changed = true;
      });
  }

  deactivate(
    type: 'category' | 'tag' | 'payment-source',
    configurationId: number
  ): void {
    this.httpClient
      .delete(`/api/expenses/${type}/${configurationId}`)
      .subscribe(() => {
        if (type === 'category') {
          const category = this.categories.find(
            item => item.id === configurationId
          );
          if (category) category.isActive = false;
        } else if (type === 'tag') {
          const tag = this.tags.find(item => item.id === configurationId);
          if (tag) tag.isActive = false;
        } else {
          const source = this.paymentSources.find(
            item => item.id === configurationId
          );
          if (source) source.isActive = false;
        }
        this.changed = true;
      });
  }

  close(): void {
    this.dialogReference.close(this.changed);
  }
}
