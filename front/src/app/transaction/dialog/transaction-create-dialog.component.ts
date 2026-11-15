import {ChangeDetectionStrategy} from '@angular/core';
import {Component, OnInit} from '@angular/core';
import {MatDialogRef} from '@angular/material/dialog';
import {HttpClient} from '@angular/common/http';
import {Stock} from '../../../api/stock';
import {Wallet} from '../../../api/wallet';
import {Transaction, TransactionType} from '../../../api/transaction';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'transaction-create-dialog',
  template: `
    <div class="transaction-dialog">
      <div mat-dialog-title fxLayout="row" fxLayoutAlign="space-between center">
        <h2>Nowa transakcja</h2>
        <button mat-icon-button mat-dialog-close>
          <fa-icon [icon]="['fas', 'times']"></fa-icon>
        </button>
      </div>

      <div mat-dialog-content class="dialog-content">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Typ transakcji</mat-label>
          <mat-select
            [(ngModel)]="transactionType"
            (ngModelChange)="onTransactionTypeChange()"
          >
            <mat-option value="BUY">Kupno</mat-option>
            <mat-option value="SELL">Sprzedaż</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Portfel</mat-label>
          <mat-select
            [(ngModel)]="selectedWalletId"
            (ngModelChange)="onWalletOrStockChange()"
          >
            <mat-option *ngFor="let wallet of wallets" [value]="wallet.id">
              {{ wallet.name }}
            </mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Aktywo</mat-label>
          <mat-select
            [(ngModel)]="selectedStockId"
            (ngModelChange)="onWalletOrStockChange()"
          >
            <mat-option *ngFor="let stock of stocks" [value]="stock.id">
              {{ stock.name }}
            </mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Ilość</mat-label>
          <input
            matInput
            type="number"
            [(ngModel)]="amount"
            [max]="transactionType === 'SELL' ? maxSellAmount : null"
          />
          <mat-hint *ngIf="transactionType === 'SELL' && maxSellAmount > 0"
            >Maksymalnie: {{ maxSellAmount }}</mat-hint
          >
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
          Dodaj
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
export class TransactionCreateDialog implements OnInit {
  wallets: Wallet[] = [];
  stocks: Stock[] = [];
  transactions: Transaction[] = [];
  selectedWalletId: number | null = null;
  selectedStockId: number | null = null;
  amount = 0;
  purchaseCost = 0;
  purchaseDate = '';
  transactionType: TransactionType = 'BUY';
  maxSellAmount = 0;

  constructor(
    public dialogRef: MatDialogRef<TransactionCreateDialog>,
    private httpClient: HttpClient
  ) {}

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

    this.httpClient
      .get<Transaction[]>('/api/transaction/list')
      .subscribe(transactions => {
        this.transactions = transactions;
      });
  }

  onTransactionTypeChange(): void {
    this.calculateMaxSellAmount();
  }

  onWalletOrStockChange(): void {
    this.calculateMaxSellAmount();
  }

  calculateMaxSellAmount(): void {
    if (
      this.transactionType === 'SELL' &&
      this.selectedWalletId &&
      this.selectedStockId
    ) {
      const relevantTransactions = this.transactions.filter(
        t =>
          t.wallet.id === this.selectedWalletId &&
          t.stock.id === this.selectedStockId
      );
      const bought = relevantTransactions
        .filter(t => t.transactionType === 'BUY')
        .reduce((sum, t) => sum + t.amount, 0);
      const sold = relevantTransactions
        .filter(t => t.transactionType === 'SELL')
        .reduce((sum, t) => sum + t.amount, 0);
      this.maxSellAmount = bought - sold;
    } else {
      this.maxSellAmount = 0;
    }
  }

  isValid(): boolean {
    const baseValid =
      this.selectedWalletId !== null &&
      this.selectedStockId !== null &&
      this.amount > 0 &&
      this.purchaseCost > 0 &&
      this.purchaseDate !== '';

    if (this.transactionType === 'SELL') {
      return baseValid && this.amount <= this.maxSellAmount;
    }
    return baseValid;
  }

  onSave(): void {
    if (this.isValid()) {
      this.dialogRef.close({
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
