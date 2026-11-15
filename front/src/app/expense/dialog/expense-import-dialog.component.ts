import {ChangeDetectionStrategy} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Component, Inject, ViewChild} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {MatStepper} from '@angular/material/stepper';
import {
  ExpenseBootstrap,
  ExpenseCountingStatus,
  ExpenseImportPreview,
  ExpenseImportRow,
} from '../../../api/expense';

interface ExpenseImportDialogData {
  bootstrapData: ExpenseBootstrap;
  folderId: number | null;
}

interface ExpenseImportResult {
  batchId: number;
  imported: number;
  skipped: number;
}

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'expense-import-dialog',
  template: `
    <div class="import-dialog">
      <div mat-dialog-title class="dialog-title">
        <div>
          <h2>Import wydatków</h2>
          <p>
            XLSX historyczny, BGŻ/BNP lub Revolut; CSV Pekao; natywny JSON/CSV
          </p>
        </div>
        <button mat-icon-button [mat-dialog-close]="result !== null">
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <mat-horizontal-stepper #stepper [linear]="true">
        <mat-step label="Plik" [completed]="preview !== null">
          <div class="step-content file-step">
            <label class="file-drop">
              <fa-icon [icon]="['fas', 'file-excel']" size="2x"></fa-icon>
              <span>{{ fileName || 'Wybierz plik XLSX, JSON lub CSV' }}</span>
              <input
                type="file"
                accept=".xlsx,.json,.csv"
                (change)="selectFile($event)"
              />
            </label>
            <mat-progress-spinner
              *ngIf="isLoading"
              diameter="34"
              mode="indeterminate"
            ></mat-progress-spinner>
            <p class="error" *ngIf="errorMessage">{{ errorMessage }}</p>
          </div>
          <div class="step-actions">
            <button mat-button mat-dialog-close>Anuluj</button>
            <button
              mat-raised-button
              color="primary"
              [disabled]="!preview"
              matStepperNext
            >
              Dalej
            </button>
          </div>
        </mat-step>

        <mat-step label="Mapowanie" [completed]="mappingValid">
          <div class="step-content mapping-grid">
            <mat-form-field appearance="outline">
              <mat-label>Folder docelowy</mat-label>
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
              <mat-label>Źródło dla nierozpoznanych wierszy</mat-label>
              <mat-select [(ngModel)]="paymentSourceId">
                <mat-option
                  *ngFor="let source of activePaymentSources"
                  [value]="source.id"
                >
                  {{ source.name }}
                </mat-option>
              </mat-select>
            </mat-form-field>

            <div class="summary" *ngIf="preview">
              <span
                ><strong>{{ preview.rows.length }}</strong> wierszy</span
              >
              <span
                ><strong>{{ preview.duplicates }}</strong> duplikatów</span
              >
              <span
                ><strong>{{ unresolvedSources }}</strong> nierozpoznanych
                źródeł</span
              >
              <span *ngFor="let total of preview.totals">
                <strong>{{ total.amount | number: '1.2-2' }}</strong>
                {{ total.currency }}
              </span>
            </div>
            <p class="mapping-note">
              BGŻ jest mapowane na BNP Paribas. Importy bankowe zachowują datę
              operacji, pomijają wpływy, wymiany walut i rozpoznane przelewy
              między własnymi rachunkami. Historyczny XLSX pobiera okres z nazwy
              pliku.
            </p>
          </div>
          <div class="step-actions">
            <button mat-button matStepperPrevious>Wstecz</button>
            <button
              mat-raised-button
              color="primary"
              [disabled]="!mappingValid"
              matStepperNext
            >
              Podgląd
            </button>
          </div>
        </mat-step>

        <mat-step label="Podgląd" [completed]="result !== null">
          <div class="step-content preview-step" *ngIf="preview">
            <div class="preview-toolbar">
              <p>
                Zaznacz wiersze do zapisania. Duplikaty są domyślnie wyłączone i
                zostaną ponownie sprawdzone w transakcji.
              </p>
              <button mat-stroked-button (click)="downloadConvertedJson()">
                <fa-icon [icon]="['fas', 'download']"></fa-icon>
                Pobierz skonwertowany JSON
              </button>
            </div>
            <div class="preview-table-wrapper">
              <table mat-table [dataSource]="preview.rows">
                <ng-container matColumnDef="selected">
                  <th mat-header-cell *matHeaderCellDef></th>
                  <td mat-cell *matCellDef="let row">
                    <mat-checkbox [(ngModel)]="row.selected"></mat-checkbox>
                  </td>
                </ng-container>
                <ng-container matColumnDef="row">
                  <th mat-header-cell *matHeaderCellDef>Wiersz</th>
                  <td mat-cell *matCellDef="let row">{{ row.rowNumber }}</td>
                </ng-container>
                <ng-container matColumnDef="source">
                  <th mat-header-cell *matHeaderCellDef>Źródło</th>
                  <td mat-cell *matCellDef="let row">
                    {{ resolvedSourceName(row) }}
                    <small *ngIf="!row.paymentSourceId">z mapowania</small>
                  </td>
                </ng-container>
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Nazwa</th>
                  <td mat-cell *matCellDef="let row">
                    {{ row.name }}
                    <small *ngFor="let warning of row.warnings">{{
                      warning
                    }}</small>
                  </td>
                </ng-container>
                <ng-container matColumnDef="month">
                  <th mat-header-cell *matHeaderCellDef>Okres</th>
                  <td mat-cell *matCellDef="let row">
                    {{ row.date || (row.month | slice: 0 : 7) }}
                  </td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef>Kwota</th>
                  <td
                    mat-cell
                    *matCellDef="let row"
                    [class.negative]="row.amount < 0"
                  >
                    {{ row.amount | number: '1.2-2' }} {{ row.currency }}
                  </td>
                </ng-container>
                <ng-container matColumnDef="countingStatus">
                  <th mat-header-cell *matHeaderCellDef>Rozliczenie</th>
                  <td mat-cell *matCellDef="let row">
                    <mat-select
                      class="counting-status-select"
                      [(ngModel)]="row.countingStatus"
                    >
                      <mat-option
                        *ngFor="let option of countingStatusOptions"
                        [value]="option.value"
                      >
                        {{ option.label }}
                      </mat-option>
                    </mat-select>
                  </td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Status</th>
                  <td mat-cell *matCellDef="let row">
                    <span class="duplicate" *ngIf="row.duplicate"
                      >Duplikat</span
                    >
                    <span class="ready" *ngIf="!row.duplicate">Gotowy</span>
                  </td>
                </ng-container>
                <tr
                  mat-header-row
                  *matHeaderRowDef="[
                    'selected',
                    'row',
                    'source',
                    'name',
                    'month',
                    'amount',
                    'countingStatus',
                    'status'
                  ]"
                ></tr>
                <tr
                  mat-row
                  *matRowDef="
                    let row;
                    columns: [
                      'selected',
                      'row',
                      'source',
                      'name',
                      'month',
                      'amount',
                      'countingStatus',
                      'status'
                    ]
                  "
                ></tr>
              </table>
            </div>
            <p class="error" *ngIf="errorMessage">{{ errorMessage }}</p>
          </div>
          <div class="step-actions">
            <button mat-button matStepperPrevious [disabled]="isLoading">
              Wstecz
            </button>
            <button
              mat-raised-button
              color="primary"
              [disabled]="!canCommit || isLoading"
              (click)="commit()"
            >
              Zatwierdź {{ selectedRows }} wierszy
            </button>
          </div>
        </mat-step>

        <mat-step label="Wynik">
          <div class="step-content result-step" *ngIf="result">
            <fa-icon [icon]="['fas', 'circle-check']" size="3x"></fa-icon>
            <h3>Import zakończony</h3>
            <p>
              Dodano: <strong>{{ result.imported }}</strong>
            </p>
            <p>
              Pominięto: <strong>{{ result.skipped }}</strong>
            </p>
            <p>Numer partii: {{ result.batchId }}</p>
          </div>
          <div class="step-actions">
            <button mat-raised-button color="primary" (click)="close()">
              Zamknij
            </button>
          </div>
        </mat-step>
      </mat-horizontal-stepper>
    </div>
  `,
  styles: [
    `
      :host {
        .import-dialog {
          min-width: 0;
        }

        .dialog-title,
        .step-actions,
        .preview-toolbar,
        .summary {
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

        .step-content {
          min-height: 330px;
          padding: 24px 4px 10px;
        }

        .file-step,
        .result-step {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 16px;
        }

        .file-drop {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 12px;
          width: min(520px, 90%);
          border: 2px dashed #94a3b8;
          border-radius: 12px;
          background: #f8fafc;
          padding: 42px 20px;
          color: #334155;
          cursor: pointer;
        }

        .file-drop input {
          display: none;
        }

        .mapping-grid {
          display: grid;
          grid-template-columns: repeat(2, minmax(0, 1fr));
          gap: 14px;
        }

        .summary,
        .mapping-note {
          grid-column: span 2;
        }

        .summary {
          flex-wrap: wrap;
          gap: 10px;
        }

        .summary span {
          border-radius: 18px;
          background: #eef4ff;
          padding: 8px 12px;
        }

        .mapping-note {
          color: #64748b;
          font-size: 13px;
        }

        .preview-toolbar {
          justify-content: space-between;
          gap: 20px;
        }

        .preview-table-wrapper {
          max-height: 430px;
          overflow: auto;
          border: 1px solid #e5e7eb;
          border-radius: 8px;
        }

        table {
          width: 100%;
          min-width: 980px;
        }

        .counting-status-select {
          width: 145px;
          font-size: 12px;
        }

        td small {
          display: block;
          color: #b54708;
          font-size: 10px;
        }

        .negative {
          color: #b42318;
          font-weight: 600;
        }

        .duplicate,
        .ready {
          border-radius: 14px;
          padding: 4px 8px;
          font-size: 11px;
          font-weight: 600;
        }

        .duplicate {
          background: #fff1f0;
          color: #b42318;
        }

        .ready {
          background: #ecfdf3;
          color: #027a48;
        }

        .error {
          color: #b42318;
        }

        .step-actions {
          justify-content: flex-end;
          gap: 8px;
          padding-top: 12px;
        }

        .result-step fa-icon {
          color: #12b76a;
        }

        @media (max-width: 720px) {
          .mapping-grid {
            grid-template-columns: 1fr;
          }

          .summary,
          .mapping-note {
            grid-column: span 1;
          }
        }
      }
    `,
  ],
})
export class ExpenseImportDialog {
  @ViewChild('stepper') stepper?: MatStepper;

  fileName = '';
  preview: ExpenseImportPreview | null = null;
  folderId: number | null;
  paymentSourceId: number | null = null;
  result: ExpenseImportResult | null = null;
  isLoading = false;
  errorMessage = '';
  readonly countingStatusOptions: Array<{
    value: ExpenseCountingStatus;
    label: string;
  }> = [
    {value: 'included', label: 'Uwzględnij'},
    {value: 'excluded_internal_transfer', label: 'Przelew własny'},
    {value: 'excluded_business', label: 'Działalność'},
    {value: 'excluded_investment', label: 'Inwestycja'},
    {value: 'excluded_cash_transfer', label: 'Gotówka'},
    {value: 'excluded_reimbursed', label: 'Zwrócony'},
    {value: 'excluded_duplicate', label: 'Duplikat'},
  ];

  constructor(
    private readonly httpClient: HttpClient,
    private readonly dialogReference: MatDialogRef<ExpenseImportDialog>,
    @Inject(MAT_DIALOG_DATA) readonly data: ExpenseImportDialogData
  ) {
    this.folderId = data.folderId;
    this.paymentSourceId =
      data.bootstrapData.paymentSources.find(source => source.type === 'legacy')
        ?.id ??
      data.bootstrapData.paymentSources[0]?.id ??
      null;
  }

  get activePaymentSources() {
    return this.data.bootstrapData.paymentSources.filter(
      source => source.isActive
    );
  }

  get unresolvedSources(): number {
    return (
      this.preview?.rows.filter(row => row.paymentSourceId === null).length ?? 0
    );
  }

  get mappingValid(): boolean {
    return (
      this.folderId !== null &&
      (this.unresolvedSources === 0 || this.paymentSourceId !== null)
    );
  }

  get selectedRows(): number {
    return this.preview?.rows.filter(row => row.selected).length ?? 0;
  }

  get canCommit(): boolean {
    return this.mappingValid && this.selectedRows > 0;
  }

  selectFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.fileName = file.name;
    this.preview = null;
    this.errorMessage = '';
    this.isLoading = true;
    const fileReader = new FileReader();
    fileReader.onload = () => {
      const dataUrl = String(fileReader.result);
      const contentBase64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
      this.httpClient
        .post<ExpenseImportPreview>('/api/expenses/import/preview', {
          fileName: file.name,
          contentBase64,
        })
        .subscribe({
          next: preview => {
            this.preview = preview;
            this.isLoading = false;
          },
          error: () => {
            this.errorMessage =
              'Nie udało się odczytać pliku. Sprawdź jego format i nazwę.';
            this.isLoading = false;
          },
        });
    };
    fileReader.onerror = () => {
      this.errorMessage = 'Nie udało się odczytać pliku.';
      this.isLoading = false;
    };
    fileReader.readAsDataURL(file);
  }

  resolvedSourceName(row: ExpenseImportRow): string {
    const sourceId = row.paymentSourceId ?? this.paymentSourceId;
    return (
      this.data.bootstrapData.paymentSources.find(
        source => source.id === sourceId
      )?.name ??
      row.paymentSource ??
      'Brak'
    );
  }

  downloadConvertedJson(): void {
    if (!this.preview) {
      return;
    }
    const downloadUrl = URL.createObjectURL(
      new Blob([JSON.stringify(this.preview.convertedJson, null, 2)], {
        type: 'application/json',
      })
    );
    const downloadLink = document.createElement('a');
    downloadLink.href = downloadUrl;
    downloadLink.download =
      this.preview.fileName.replace(/\.[^.]+$/, '') + '.json';
    downloadLink.click();
    URL.revokeObjectURL(downloadUrl);
  }

  commit(): void {
    if (!this.preview || !this.canCommit) {
      return;
    }
    this.isLoading = true;
    this.errorMessage = '';
    this.httpClient
      .post<ExpenseImportResult>('/api/expenses/import/commit', {
        format: this.preview.format,
        fileName: this.preview.fileName,
        checksum: this.preview.checksum,
        folderId: this.folderId,
        paymentSourceId: this.paymentSourceId,
        rows: this.preview.rows.map(row => ({
          ...row,
          folderId: row.folder ? row.folderId ?? null : this.folderId,
          paymentSourceId: row.paymentSource
            ? row.paymentSourceId
            : this.paymentSourceId,
        })),
      })
      .subscribe({
        next: result => {
          this.result = result;
          this.isLoading = false;
          this.stepper?.next();
        },
        error: () => {
          this.errorMessage =
            'Import nie został zapisany. Żaden wiersz nie został dodany.';
          this.isLoading = false;
        },
      });
  }

  close(): void {
    this.dialogReference.close(true);
  }
}
