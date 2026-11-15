import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {AfterViewInit, Component, Input, ViewChild} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatSort} from '@angular/material/sort';
import {MatTableDataSource} from '@angular/material/table';
import {Transaction} from '../../api/transaction';
import {TransactionCreateDialog} from './dialog/transaction-create-dialog.component';
import {TransactionModifyDialog} from './dialog/transaction-modify-dialog.component';
import {TransactionDeleteDialog} from './dialog/transaction-delete-dialog.component';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'transaction-list',
  templateUrl: './transaction-list.component.html',
  styleUrls: ['./transaction-list.component.scss'],
})
export class TransactionListComponent implements AfterViewInit {
  public readonly staticDemo = environment.staticDemo;
  @ViewChild(MatSort) sort!: MatSort;

  dataSource = new MatTableDataSource<Transaction>([]);
  displayedColumns: string[] = [
    'transactionType',
    'stockName',
    'walletName',
    'amount',
    'purchaseCost',
    'purchaseDate',
    'modifications',
  ];

  @Input()
  set transactions(transactions: Transaction[] | undefined) {
    this.dataSource.data = transactions ?? [];
  }

  constructor(
    private httpClient: HttpClient,
    private matDialog: MatDialog,
    private matSnackBar: MatSnackBar
  ) {
    this.dataSource.sortingDataAccessor = (
      transaction,
      sortHeaderIdentifier
    ) => {
      switch (sortHeaderIdentifier) {
        case 'transactionType':
          return transaction.transactionType;
        case 'stockName':
          return transaction.stock.name;
        case 'walletName':
          return transaction.wallet.name;
        case 'amount':
          return transaction.amount;
        case 'purchaseCost':
          return transaction.purchaseCost;
        case 'purchaseDate':
          return transaction.purchaseDate;
        default:
          return '';
      }
    };
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
  }

  openCreateDialog(): void {
    const dialogRef = this.matDialog.open(TransactionCreateDialog);

    dialogRef.afterClosed().subscribe(newTransactionData => {
      if (newTransactionData) {
        this.httpClient
          .post<Transaction>('/api/transaction/create', newTransactionData)
          .subscribe(() => {
            this.refreshTransactionList();
            this.matSnackBar.open('Transakcja została utworzona', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  openModifyDialog(transaction: Transaction): void {
    const dialogRef = this.matDialog.open(TransactionModifyDialog, {
      data: transaction,
    });

    dialogRef.afterClosed().subscribe(updatedTransactionData => {
      if (updatedTransactionData) {
        this.httpClient
          .patch<Transaction>('/api/transaction/update', updatedTransactionData)
          .subscribe(() => {
            this.refreshTransactionList();
            this.matSnackBar.open('Transakcja została zaktualizowana', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  openDeleteDialog(transaction: Transaction): void {
    const dialogRef = this.matDialog.open(TransactionDeleteDialog, {
      data: transaction,
    });

    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.httpClient
          .delete(`/api/transaction/${transaction.id}`)
          .subscribe(() => {
            this.refreshTransactionList();
            this.matSnackBar.open('Transakcja została usunięta', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  private refreshTransactionList(): void {
    this.httpClient
      .get<Transaction[]>('/api/transaction/list')
      .subscribe(transactions => {
        this.dataSource.data = transactions;
      });
  }
}
