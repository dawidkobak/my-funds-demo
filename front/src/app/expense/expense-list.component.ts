import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {NestedTreeControl} from '@angular/cdk/tree';
import {HttpClient} from '@angular/common/http';
import {Component, OnInit} from '@angular/core';
import {
  DateAdapter,
  MAT_DATE_FORMATS,
  MAT_DATE_LOCALE,
} from '@angular/material/core';
import {MatDialog} from '@angular/material/dialog';
import {
  MAT_MOMENT_DATE_ADAPTER_OPTIONS,
  MomentDateAdapter,
} from '@angular/material-moment-adapter';
import {PageEvent} from '@angular/material/paginator';
import {MatSnackBar} from '@angular/material/snack-bar';
import {Sort} from '@angular/material/sort';
import {MatTreeNestedDataSource} from '@angular/material/tree';
import {IconDefinition} from '@fortawesome/fontawesome-svg-core';
import {
  faBasketShopping,
  faCar,
  faEllipsis,
  faFileInvoiceDollar,
  faFilm,
  faGamepad,
  faGift,
  faHeartPulse,
  faHouse,
  faLaptop,
  faMobileScreenButton,
  faPiggyBank,
  faPlaneDeparture,
  faShirt,
  faUtensils,
} from '@fortawesome/free-solid-svg-icons';
import {Moment} from 'moment';
import {
  ExpenseBootstrap,
  ExpenseCountingStatus,
  ExpenseFolder,
  ExpenseFolderNode,
  ExpenseItem,
  ExpenseReportResponse,
  ExpenseSearchRequest,
  ExpenseSearchResponse,
} from '../../api/expense';
import {ExpenseCreateDialog} from './dialog/expense-create-dialog.component';
import {ExpenseDeleteDialog} from './dialog/expense-delete-dialog.component';
import {ExpenseImportDialog} from './dialog/expense-import-dialog.component';
import {ExpenseListCreateDialog} from './dialog/expense-list-create-dialog.component';
import {ExpenseManageDialog} from './dialog/expense-manage-dialog.component';

interface ExpenseExportResponse {
  fileName: string;
  mediaType: string;
  contentBase64: string;
}

const EXPENSE_FILTER_DATE_FORMATS = {
  parse: {
    dateInput: ['DD.MM.YYYY', 'YYYY-MM-DD'],
  },
  display: {
    dateInput: 'DD.MM.YYYY',
    monthYearLabel: 'MMMM YYYY',
    dateA11yLabel: 'LL',
    monthYearA11yLabel: 'MMMM YYYY',
  },
};

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'expense-list',
  templateUrl: './expense-list.component.html',
  styleUrls: ['./expense-list.component.scss'],
  providers: [
    {provide: MAT_DATE_LOCALE, useValue: 'pl'},
    {
      provide: MAT_MOMENT_DATE_ADAPTER_OPTIONS,
      useValue: {useUtc: true, strict: true},
    },
    {
      provide: DateAdapter,
      useClass: MomentDateAdapter,
      deps: [MAT_DATE_LOCALE, MAT_MOMENT_DATE_ADAPTER_OPTIONS],
    },
    {provide: MAT_DATE_FORMATS, useValue: EXPENSE_FILTER_DATE_FORMATS},
  ],
})
export class ExpenseListComponent implements OnInit {
  public readonly staticDemo = environment.staticDemo;
  private readonly polishMonthNames = [
    'styczeń',
    'luty',
    'marzec',
    'kwiecień',
    'maj',
    'czerwiec',
    'lipiec',
    'sierpień',
    'wrzesień',
    'październik',
    'listopad',
    'grudzień',
  ];
  readonly treeControl = new NestedTreeControl<ExpenseFolderNode>(
    folder => folder.children
  );
  readonly treeDataSource = new MatTreeNestedDataSource<ExpenseFolderNode>();
  readonly displayedColumns = [
    'date',
    'category',
    'name',
    'tags',
    'source',
    'amount',
    'actions',
  ];
  readonly pageSizeOptions = [10, 25, 50, 100];
  readonly hasChild = (_: number, folder: ExpenseFolderNode): boolean =>
    folder.children.length > 0;

  bootstrapData: ExpenseBootstrap = {
    folders: [],
    categories: [],
    tags: [],
    paymentSources: [],
    currencies: [],
    legacyMigration: null,
  };
  selectedFolder: ExpenseFolder | null = null;
  expenseItems: ExpenseItem[] = [];
  totals: Array<{currency: string; amount: number}> = [];
  totalCount = 0;
  isLoading = false;
  selectedTabIndex = 0;
  report: ExpenseReportResponse | null = null;
  selectedReportCurrency = 'PLN';
  filters: ExpenseSearchRequest = this.emptyFilters();
  filterDateFrom: Moment | null = null;
  filterDateTo: Moment | null = null;

  constructor(
    private readonly httpClient: HttpClient,
    private readonly matDialog: MatDialog,
    private readonly matSnackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadBootstrap();
  }

  loadBootstrap(
    preserveFolder = true,
    selectedFolderId?: number,
    expandedFolderId?: number
  ): void {
    const previousFolderId =
      selectedFolderId ?? (preserveFolder ? this.selectedFolder?.id : null);
    this.httpClient
      .get<ExpenseBootstrap>('/api/expenses/bootstrap')
      .subscribe(bootstrapData => {
        this.bootstrapData = bootstrapData;
        this.treeDataSource.data = this.sortRootFolders(
          bootstrapData.folders.filter(folder => folder.parentId === null)
        ).map(folder => ({
          ...folder,
          children: this.sortChildFolders(
            bootstrapData.folders.filter(
              childFolder => childFolder.parentId === folder.id
            )
          ).map(child => ({...child, children: []})),
        }));
        this.treeControl.dataNodes = this.treeDataSource.data;
        this.treeControl.collapseAll();
        const folderToExpand = this.treeDataSource.data.find(
          folder => folder.id === expandedFolderId
        );
        if (folderToExpand) {
          this.treeControl.expand(folderToExpand);
        }
        this.selectedFolder =
          bootstrapData.folders.find(
            folder => folder.id === previousFolderId
          ) ??
          this.treeDataSource.data[0] ??
          null;
        this.loadExpenses(true);
        if (this.selectedTabIndex === 1) {
          this.loadReports();
        }
      });
  }

  private sortRootFolders(folders: ExpenseFolder[]): ExpenseFolder[] {
    return [...folders].sort((firstFolder, secondFolder) => {
      const firstFolderYear = /^\d{4}$/.test(firstFolder.name)
        ? Number(firstFolder.name)
        : null;
      const secondFolderYear = /^\d{4}$/.test(secondFolder.name)
        ? Number(secondFolder.name)
        : null;

      if (firstFolderYear !== null && secondFolderYear !== null) {
        return secondFolderYear - firstFolderYear;
      }
      if (firstFolderYear !== null) {
        return -1;
      }
      if (secondFolderYear !== null) {
        return 1;
      }
      return (
        firstFolder.sortOrder - secondFolder.sortOrder ||
        firstFolder.name.localeCompare(secondFolder.name, 'pl')
      );
    });
  }

  private sortChildFolders(folders: ExpenseFolder[]): ExpenseFolder[] {
    return [...folders].sort((firstFolder, secondFolder) => {
      const firstFolderMonth = this.polishMonthNames.indexOf(
        firstFolder.name.toLocaleLowerCase('pl')
      );
      const secondFolderMonth = this.polishMonthNames.indexOf(
        secondFolder.name.toLocaleLowerCase('pl')
      );

      if (firstFolderMonth >= 0 && secondFolderMonth >= 0) {
        return secondFolderMonth - firstFolderMonth;
      }
      if (firstFolderMonth >= 0) {
        return -1;
      }
      if (secondFolderMonth >= 0) {
        return 1;
      }
      return (
        firstFolder.sortOrder - secondFolder.sortOrder ||
        firstFolder.name.localeCompare(secondFolder.name, 'pl')
      );
    });
  }

  selectFolder(folder: ExpenseFolder): void {
    this.selectedFolder = folder;
    this.loadExpenses(true);
    if (this.selectedTabIndex === 1) {
      this.loadReports();
    }
  }

  loadExpenses(resetPage = false): void {
    if (resetPage) {
      this.filters.page = 0;
    }
    this.isLoading = true;
    this.httpClient
      .post<ExpenseSearchResponse>('/api/expenses/search', {
        ...this.filters,
        folderId: this.selectedFolder?.id ?? null,
      })
      .subscribe({
        next: response => {
          this.expenseItems = response.items;
          this.totalCount = response.totalCount;
          this.totals = response.totals;
          this.isLoading = false;
        },
        error: () => {
          this.isLoading = false;
        },
      });
  }

  applyFilters(): void {
    this.loadExpenses(true);
  }

  resetFilters(): void {
    this.filters = this.emptyFilters();
    this.filterDateFrom = null;
    this.filterDateTo = null;
    this.loadExpenses(true);
  }

  setFilterDateFrom(selectedDate: Moment | null): void {
    this.filterDateFrom = selectedDate;
    this.filters.dateFrom = this.formatFilterDate(selectedDate);
  }

  setFilterDateTo(selectedDate: Moment | null): void {
    this.filterDateTo = selectedDate;
    this.filters.dateTo = this.formatFilterDate(selectedDate);
  }

  onSortChange(sort: Sort): void {
    this.filters.sort = sort.active || 'date';
    this.filters.direction = sort.direction === 'asc' ? 'asc' : 'desc';
    this.loadExpenses(true);
  }

  onPageChange(pageEvent: PageEvent): void {
    this.filters.page = pageEvent.pageIndex;
    this.filters.pageSize = pageEvent.pageSize;
    this.loadExpenses();
  }

  openExpenseDialog(expenseItem?: ExpenseItem): void {
    const dialogReference = this.matDialog.open(ExpenseCreateDialog, {
      width: '680px',
      maxWidth: '95vw',
      data: {
        bootstrapData: this.bootstrapData,
        folderId: expenseItem?.folder.id ?? this.selectedFolder?.id ?? null,
        expenseItem: expenseItem ?? null,
      },
    });
    dialogReference.afterClosed().subscribe(expensePayload => {
      if (!expensePayload) {
        return;
      }
      const request = expenseItem
        ? this.httpClient.patch(
            `/api/expenses/item/${expenseItem.id}`,
            expensePayload
          )
        : this.httpClient.post('/api/expenses/item', expensePayload);
      request.subscribe(() => {
        this.matSnackBar.open(
          expenseItem
            ? 'Wydatek został zaktualizowany'
            : 'Wydatek został dodany',
          'OK',
          {duration: 2500}
        );
        this.loadExpenses();
        if (this.selectedTabIndex === 1) {
          this.loadReports();
        }
      });
    });
  }

  deleteExpense(expenseItem: ExpenseItem): void {
    this.matDialog
      .open(ExpenseDeleteDialog, {
        data: {
          title: 'Usuń wydatek',
          name: expenseItem.name,
          details: `${expenseItem.amount.toFixed(2)} ${expenseItem.currency.code}`,
        },
      })
      .afterClosed()
      .subscribe(confirmed => {
        if (!confirmed) {
          return;
        }
        this.httpClient
          .delete(`/api/expenses/item/${expenseItem.id}`)
          .subscribe(() => {
            this.matSnackBar.open('Wydatek został usunięty', 'OK', {
              duration: 2500,
            });
            this.loadExpenses();
          });
      });
  }

  createFolder(): void {
    this.openExpenseLocationDialog(
      null,
      'Nowy folder',
      'Nazwa folderu',
      'Folder został utworzony'
    );
  }

  createExpenseList(parentFolder?: ExpenseFolder): void {
    this.openExpenseLocationDialog(
      parentFolder ?? null,
      parentFolder ? 'Nowa lista w folderze' : 'Nowa lista wydatków',
      'Nazwa listy',
      'Lista wydatków została utworzona'
    );
  }

  private openExpenseLocationDialog(
    parentFolder: ExpenseFolder | null,
    title: string,
    label: string,
    successMessage: string
  ): void {
    this.matDialog
      .open(ExpenseListCreateDialog, {
        data: {
          title,
          label,
          name: '',
          parentName: parentFolder?.name ?? null,
        },
      })
      .afterClosed()
      .subscribe(folderName => {
        if (!folderName) {
          return;
        }
        this.httpClient
          .post<{id: number}>('/api/expenses/folder', {
            name: folderName,
            parentId: parentFolder?.id ?? null,
            sortOrder: this.bootstrapData.folders.length,
          })
          .subscribe(response => {
            this.matSnackBar.open(successMessage, 'OK', {duration: 2500});
            this.loadBootstrap(false, response.id, parentFolder?.id);
          });
      });
  }

  renameFolder(folder: ExpenseFolder): void {
    this.matDialog
      .open(ExpenseListCreateDialog, {
        data: {
          title: 'Zmień nazwę folderu',
          label: 'Nazwa folderu',
          name: folder.name,
          parentName: null,
        },
      })
      .afterClosed()
      .subscribe(folderName => {
        if (!folderName || folderName === folder.name) {
          return;
        }
        this.httpClient
          .patch(`/api/expenses/folder/${folder.id}`, {
            name: folderName,
            sortOrder: folder.sortOrder,
          })
          .subscribe(() => this.loadBootstrap());
      });
  }

  deleteFolder(folder: ExpenseFolder): void {
    this.matDialog
      .open(ExpenseDeleteDialog, {
        data: {
          title: 'Usuń folder',
          name: folder.name,
          details:
            'Folder można usunąć tylko wtedy, gdy nie zawiera wpisów ani podfolderów.',
        },
      })
      .afterClosed()
      .subscribe(confirmed => {
        if (!confirmed) {
          return;
        }
        this.httpClient
          .delete<{
            deleted: number;
            blocked: boolean;
          }>(`/api/expenses/folder/${folder.id}`)
          .subscribe(response => {
            if (response.blocked) {
              this.matSnackBar.open(
                'Folder nie jest pusty i nie może zostać usunięty',
                'OK',
                {duration: 3500}
              );
            } else {
              this.loadBootstrap(false);
            }
          });
      });
  }

  openImportDialog(): void {
    this.matDialog
      .open(ExpenseImportDialog, {
        width: '1100px',
        maxWidth: '96vw',
        disableClose: true,
        data: {
          bootstrapData: this.bootstrapData,
          folderId: this.selectedFolder?.id ?? null,
        },
      })
      .afterClosed()
      .subscribe(imported => {
        if (imported) {
          this.loadExpenses(true);
          this.matSnackBar.open('Import został zapisany', 'OK', {
            duration: 3000,
          });
        }
      });
  }

  openManageDialog(): void {
    this.matDialog
      .open(ExpenseManageDialog, {
        width: '900px',
        maxWidth: '96vw',
        data: this.bootstrapData,
      })
      .afterClosed()
      .subscribe(changed => {
        if (changed) {
          this.loadBootstrap();
        }
      });
  }

  exportExpenses(format: 'json' | 'csv'): void {
    this.httpClient
      .post<ExpenseExportResponse>('/api/expenses/export', {
        ...this.filters,
        folderId: this.selectedFolder?.id ?? null,
        format,
      })
      .subscribe(response => {
        const bytes = Uint8Array.from(atob(response.contentBase64), character =>
          character.charCodeAt(0)
        );
        const downloadUrl = URL.createObjectURL(
          new Blob([bytes], {type: response.mediaType})
        );
        const downloadLink = document.createElement('a');
        downloadLink.href = downloadUrl;
        downloadLink.download = response.fileName;
        downloadLink.click();
        URL.revokeObjectURL(downloadUrl);
      });
  }

  onTabChange(index: number): void {
    this.selectedTabIndex = index;
    if (index === 1) {
      this.loadReports();
    }
  }

  loadReports(): void {
    this.httpClient
      .post<ExpenseReportResponse>('/api/expenses/reports', {
        ...this.filters,
        includeExcluded: false,
        folderId: this.selectedFolder?.id ?? null,
        periodGranularity: this.selectedFolder?.depth === 1 ? 'day' : 'month',
      })
      .subscribe(report => {
        this.report = report;
        const currencies = this.reportCurrencies;
        if (!currencies.includes(this.selectedReportCurrency)) {
          this.selectedReportCurrency = currencies[0] ?? 'PLN';
        }
      });
  }

  get reportCurrencies(): string[] {
    if (!this.report) {
      return [];
    }
    return Array.from(
      new Set(
        [
          ...this.report.monthly,
          ...this.report.categories,
          ...this.report.paymentSources,
          ...this.report.tags,
          ...this.report.totals,
        ].map(item => item.currency)
      )
    ).sort();
  }

  get selectedFolderPath(): string {
    if (!this.selectedFolder) {
      return 'Wszystkie foldery';
    }
    const parent = this.bootstrapData.folders.find(
      folder => folder.id === this.selectedFolder?.parentId
    );
    return parent
      ? `${parent.name} / ${this.selectedFolder.name}`
      : this.selectedFolder.name;
  }

  categoryIcon(iconName: string | undefined): IconDefinition {
    const icons: Record<string, IconDefinition> = {
      utensils: faUtensils,
      'basket-shopping': faBasketShopping,
      'mobile-screen-button': faMobileScreenButton,
      gift: faGift,
      'plane-departure': faPlaneDeparture,
      house: faHouse,
      car: faCar,
      'heart-pulse': faHeartPulse,
      shirt: faShirt,
      film: faFilm,
      gamepad: faGamepad,
      laptop: faLaptop,
      'piggy-bank': faPiggyBank,
      'file-invoice-dollar': faFileInvoiceDollar,
      ellipsis: faEllipsis,
    };
    return icons[iconName ?? 'ellipsis'] ?? faEllipsis;
  }

  reportEntries(
    type: 'paymentSources' | 'tags'
  ): Array<{name?: string; amount: number; currency: string}> {
    return (this.report?.[type] ?? []).filter(
      item => item.currency === this.selectedReportCurrency
    );
  }

  countingStatusLabel(countingStatus: ExpenseCountingStatus): string {
    const labels: Record<ExpenseCountingStatus, string> = {
      included: 'Uwzględniany',
      excluded_internal_transfer: 'Przelew własny',
      excluded_business: 'Koszt działalności',
      excluded_investment: 'Inwestycja',
      excluded_cash_transfer: 'Wypłata gotówki',
      excluded_reimbursed: 'Wydatek zwrócony',
      excluded_duplicate: 'Duplikat',
    };
    return labels[countingStatus];
  }

  private emptyFilters(): ExpenseSearchRequest {
    return {
      folderId: null,
      search: '',
      dateFrom: '',
      dateTo: '',
      categoryIds: [],
      tagIds: [],
      paymentSourceIds: [],
      currencyCodes: [],
      includeExcluded: false,
      sort: 'date',
      direction: 'desc',
      page: 0,
      pageSize: 25,
    };
  }

  private formatFilterDate(selectedDate: Moment | null): string {
    return selectedDate?.isValid() ? selectedDate.format('YYYY-MM-DD') : '';
  }
}
