import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {Component, Input} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {Stock} from '../../api/stock';
import {StockCreateDialog} from './dialog/stock-create-dialog.component';
import {StockModifyDialog} from './dialog/stock-modify-dialog.component';
import {StockDeleteDialog} from './dialog/stock-delete-dialog.component';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'stock-list',
  templateUrl: './stock-list.component.html',
  styleUrls: ['./stock-list.component.scss'],
})
export class StockListComponent {
  public readonly staticDemo = environment.staticDemo;
  @Input() stocks!: Stock[];

  constructor(
    private httpClient: HttpClient,
    private matDialog: MatDialog,
    private matSnackBar: MatSnackBar
  ) {}

  openCreateDialog(): void {
    const dialogRef = this.matDialog.open(StockCreateDialog);

    dialogRef.afterClosed().subscribe(newStockData => {
      if (newStockData) {
        this.httpClient
          .post<Stock>('/api/stock/create', newStockData)
          .subscribe(() => {
            this.refreshStockList();
            this.matSnackBar.open('Aktywo zostało utworzone', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  openModifyDialog(stock: Stock): void {
    const dialogRef = this.matDialog.open(StockModifyDialog, {
      data: stock,
    });

    dialogRef.afterClosed().subscribe(updatedStockData => {
      if (updatedStockData) {
        this.httpClient
          .patch<Stock>('/api/stock/update', updatedStockData)
          .subscribe(() => {
            this.refreshStockList();
            this.matSnackBar.open('Aktywo zostało zaktualizowane', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  openDeleteDialog(stock: Stock): void {
    const dialogRef = this.matDialog.open(StockDeleteDialog, {
      data: stock,
    });

    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.httpClient.delete(`/api/stock/${stock.id}`).subscribe(() => {
          this.refreshStockList();
          this.matSnackBar.open('Aktywo zostało usunięte', 'OK', {
            duration: 2000,
          });
        });
      }
    });
  }

  private refreshStockList(): void {
    this.httpClient.get<Stock[]>('/api/stock/list').subscribe(stocks => {
      this.stocks = stocks;
    });
  }
}
