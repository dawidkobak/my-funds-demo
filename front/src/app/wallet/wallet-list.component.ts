import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {Component, Input} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {Wallet} from '../../api/wallet';
import {WalletModifyDialog} from './dialog/wallet-modify-dialog.component';
import {WalletDeleteDialog} from './dialog/wallet-delete-dialog.component';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'wallet-list',
  templateUrl: './wallet-list.component.html',
  styleUrls: ['./wallet-list.component.scss'],
})
export class WalletListComponent {
  public readonly staticDemo = environment.staticDemo;
  @Input() wallets!: Wallet[];

  constructor(
    private httpClient: HttpClient,
    private matDialog: MatDialog,
    private matSnackBar: MatSnackBar
  ) {}

  openModifyDialog(wallet: Wallet): void {
    const dialogRef = this.matDialog.open(WalletModifyDialog, {
      data: wallet,
    });

    dialogRef.afterClosed().subscribe(newName => {
      if (newName && newName !== wallet.name) {
        this.httpClient
          .patch<Wallet>('/api/wallet/update', {
            id: wallet.id,
            name: newName,
          })
          .subscribe(() => {
            this.refreshWalletList();
            this.matSnackBar.open('Portfel został zaktualizowany', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  openDeleteDialog(wallet: Wallet): void {
    const dialogRef = this.matDialog.open(WalletDeleteDialog, {
      data: wallet,
    });

    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.httpClient.delete(`/api/wallet/${wallet.id}`).subscribe(() => {
          this.refreshWalletList();
          this.matSnackBar.open('Portfel został usunięty', 'OK', {
            duration: 2000,
          });
        });
      }
    });
  }

  private refreshWalletList(): void {
    this.httpClient.get<Wallet[]>('/api/wallet/list').subscribe(wallets => {
      this.wallets = wallets;
    });
  }
}
