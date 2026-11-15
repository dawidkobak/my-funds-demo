import {ChangeDetectionStrategy} from '@angular/core';
import {Component, Inject, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {HttpClient} from '@angular/common/http';
import {Transaction, TransactionType} from '../../../api/transaction';
import {Stock} from '../../../api/stock';
import {Wallet} from '../../../api/wallet';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'transaction-modify-dialog',
  template: `
    <div class="transaction-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Edytuj transakcję</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Typ transakcji</mat-label>
          <mat-select [(ngModel)]="transactionType">
            <mat-option value="BUY">Kupno</mat-option>
            <mat-option value="SELL">Sprzedaż</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Portfel</mat-label>
          <mat-select [(ngModel)]="selectedWalletId">
            <mat-option *ngFor="let wallet of wallets" [value]="wallet.id">
              {{ wallet.name }}
            </mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Aktywo</mat-label>
          <mat-select [(ngModel)]="selectedStockId">
            <mat-option *ngFor="let stock of stocks" [value]="stock.id">
              {{ stock.name }}
            </mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Ilość</mat-label>
          <input matInput type="number" [(ngModel)]="amount" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{
            transactionType === 'BUY' ? 'Koszt zakupu' : 'Kwota transakcji'
          }}</mat-label>
          <input matInput type="number" [(ngModel)]="purchaseCost" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{
            transactionType === 'BUY' ? 'Data zakupu' : 'Data sprzedaży'
          }}</mat-label>
          <input matInput type="date" [(ngModel)]="purchaseDate" />
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
          Zapisz
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
export class TransactionModifyDialog implements OnInit {
  wallets: Wallet[] = [];
  stocks: Stock[] = [];
  selectedWalletId: number;
  selectedStockId: number;
  amount: number;
  purchaseCost: number;
  purchaseDate: string;
  transactionType: TransactionType;

  constructor(
    public dialogRef: MatDialogRef<TransactionModifyDialog>,
    @Inject(MAT_DIALOG_DATA) public data: Transaction,
    private httpClient: HttpClient
  ) {
    this.selectedWalletId = data.wallet.id;
    this.selectedStockId = data.stock.id;
    this.amount = data.amount;
    this.purchaseCost = data.purchaseCost;
    this.purchaseDate = data.purchaseDate;
    this.transactionType = data.transactionType;
  }

  ngOnInit(): void {
    this.httpClient
      .get<Wallet[]>('/api/wallet/list', {
        params: {'wallet.type': 'STOCK'},
      })
      .subscribe(wallets => {
        this.wallets = wallets;
      });

    this.httpClient.get<Stock[]>('/api/stock/list').subscribe(stocks => {
      this.stocks = stocks;
    });
  }

  isValid(): boolean {
    return (
      this.selectedWalletId !== null &&
      this.selectedStockId !== null &&
      this.amount > 0 &&
      this.purchaseCost > 0 &&
      this.purchaseDate !== ''
    );
  }

  onSave(): void {
    if (this.isValid()) {
      this.dialogRef.close({
        id: this.data.id,
        wallet: {
          id: this.selectedWalletId,
        },
        stock: {
          id: this.selectedStockId,
        },
        amount: this.amount,
        purchaseCost: this.purchaseCost,
        purchaseDate: this.purchaseDate,
        transactionType: this.transactionType,
      });
    }
  }
}
