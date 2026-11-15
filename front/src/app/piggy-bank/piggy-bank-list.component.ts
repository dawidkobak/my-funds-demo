import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {Component, Input} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {PiggyBank} from '../../api/piggy-bank';
import {PiggyBankCreateDialog} from './dialog/piggy-bank-create-dialog.component';
import {PiggyBankModifyDialog} from './dialog/piggy-bank-modify-dialog.component';
import {PiggyBankDeleteDialog} from './dialog/piggy-bank-delete-dialog.component';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'piggy-bank-list',
  templateUrl: './piggy-bank-list.component.html',
  styleUrls: ['./piggy-bank-list.component.scss'],
})
export class PiggyBankListComponent {
  public readonly staticDemo = environment.staticDemo;
  @Input() piggyBanks!: PiggyBank[];

  constructor(
    private httpClient: HttpClient,
    private matDialog: MatDialog,
    private matSnackBar: MatSnackBar
  ) {}

  calcRemainingDays(piggyBank: PiggyBank): number {
    const date = new Date();
    const dueDate = new Date(piggyBank.dueDate);
    const between = dueDate.getTime() - date.getTime();
    if (between <= 0) {
      return 0;
    } else {
      return between / 1000 / 60 / 60 / 24;
    }
  }

  openCreateDialog(): void {
    const dialogRef = this.matDialog.open(PiggyBankCreateDialog);

    dialogRef.afterClosed().subscribe(newPiggyBankData => {
      if (newPiggyBankData) {
        this.httpClient
          .post<PiggyBank>('/api/piggy-bank/create', newPiggyBankData)
          .subscribe(() => {
            this.refreshPiggyBankList();
            this.matSnackBar.open('Skarbonka została utworzona', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  openModifyDialog(piggyBank: PiggyBank): void {
    const dialogRef = this.matDialog.open(PiggyBankModifyDialog, {
      data: piggyBank,
    });

    dialogRef.afterClosed().subscribe(updatedPiggyBankData => {
      if (updatedPiggyBankData) {
        this.httpClient
          .patch<PiggyBank>('/api/piggy-bank/update', updatedPiggyBankData)
          .subscribe(() => {
            this.refreshPiggyBankList();
            this.matSnackBar.open('Skarbonka została zaktualizowana', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  openDeleteDialog(piggyBank: PiggyBank): void {
    const dialogRef = this.matDialog.open(PiggyBankDeleteDialog, {
      data: piggyBank,
    });

    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.httpClient
          .delete(`/api/piggy-bank/${piggyBank.id}`)
          .subscribe(() => {
            this.refreshPiggyBankList();
            this.matSnackBar.open('Skarbonka została usunięta', 'OK', {
              duration: 2000,
            });
          });
      }
    });
  }

  private refreshPiggyBankList(): void {
    this.httpClient
      .get<PiggyBank[]>('/api/piggy-bank/list')
      .subscribe(piggyBanks => {
        this.piggyBanks = piggyBanks;
      });
  }
}
