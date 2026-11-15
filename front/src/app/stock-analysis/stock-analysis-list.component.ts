import {ChangeDetectionStrategy} from '@angular/core';
import {environment} from '../../environments/environment';
import {AfterViewInit, Component, Input, ViewChild} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {MatSort} from '@angular/material/sort';
import {MatTableDataSource} from '@angular/material/table';
import {Stock} from '../../api/stock';
import {
  StockAnalysis,
  StockAnalysisCreatePayload,
  StockAnalysisDecision,
  StockAnalysisStatus,
} from '../../api/stock-analysis';
import {StockAnalysisCreateDialog} from './dialog/stock-analysis-create-dialog.component';
import {StockAnalysisReportDialog} from './dialog/stock-analysis-report-dialog.component';

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'stock-analysis-list',
  templateUrl: './stock-analysis-list.component.html',
  styleUrls: ['./stock-analysis-list.component.scss'],
})
export class StockAnalysisListComponent implements AfterViewInit {
  public readonly staticDemo = environment.staticDemo;
  @ViewChild(MatSort) sort!: MatSort;

  @Input() stocks: Stock[] = [];

  @Input()
  set stockAnalyses(stockAnalyses: StockAnalysis[] | undefined) {
    this.dataSource.data = stockAnalyses ?? [];
  }

  dataSource = new MatTableDataSource<StockAnalysis>([]);
  displayedColumns: string[] = [
    'companyName',
    'ticker',
    'industry',
    'status',
    'totalScore',
    'decision',
    'createdAt',
    'completedAt',
    'actions',
  ];

  constructor(
    private httpClient: HttpClient,
    private matDialog: MatDialog,
    private matSnackBar: MatSnackBar
  ) {
    this.dataSource.sortingDataAccessor = (
      stockAnalysis,
      sortHeaderIdentifier
    ) => {
      switch (sortHeaderIdentifier) {
        case 'companyName':
          return stockAnalysis.companyName;
        case 'ticker':
          return stockAnalysis.ticker ?? '';
        case 'industry':
          return stockAnalysis.industry ?? '';
        case 'status':
          return stockAnalysis.status;
        case 'totalScore':
          return stockAnalysis.totalScore ?? -1;
        case 'decision':
          return stockAnalysis.decision ?? '';
        case 'createdAt':
          return stockAnalysis.createdAt;
        case 'completedAt':
          return stockAnalysis.completedAt ?? '';
        default:
          return '';
      }
    };
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
  }

  openCreateDialog(): void {
    const dialogRef = this.matDialog.open(StockAnalysisCreateDialog, {
      data: {
        stocks: this.stocks,
      },
    });

    dialogRef
      .afterClosed()
      .subscribe(
        (newStockAnalysisData: StockAnalysisCreatePayload | undefined) => {
          if (newStockAnalysisData) {
            this.httpClient
              .post<StockAnalysis>(
                '/api/stock-analysis/create',
                newStockAnalysisData
              )
              .subscribe(() => {
                this.refreshStockAnalysisList();
                this.matSnackBar.open(
                  'Analiza została dodana do kolejki',
                  'OK',
                  {
                    duration: 2000,
                  }
                );
              });
          }
        }
      );
  }

  openReportDialog(stockAnalysis: StockAnalysis): void {
    this.httpClient
      .get<StockAnalysis>(`/api/stock-analysis/${stockAnalysis.id}`)
      .subscribe(stockAnalysisDetails => {
        this.matDialog.open(StockAnalysisReportDialog, {
          data: stockAnalysisDetails,
          width: '980px',
          maxWidth: '92vw',
        });
      });
  }

  getStatusLabel(status: StockAnalysisStatus): string {
    switch (status) {
      case 'PENDING':
        return 'Oczekuje';
      case 'PROCESSING':
        return 'W trakcie';
      case 'COMPLETED':
        return 'Gotowa';
      case 'FAILED':
        return 'Błąd';
      default:
        return status;
    }
  }

  getDecisionLabel(decision: StockAnalysisDecision | null): string {
    switch (decision) {
      case 'ODRZUCIC':
        return 'Odrzucić';
      case 'OBSERWOWAC':
        return 'Obserwować';
      case 'GLEBSZA_ANALIZA':
        return 'Głębsza analiza';
      case 'KANDYDAT_DO_PORTFELA':
        return 'Kandydat';
      default:
        return '-';
    }
  }

  private refreshStockAnalysisList(): void {
    this.httpClient
      .get<StockAnalysis[]>('/api/stock-analysis/list')
      .subscribe(stockAnalyses => {
        this.dataSource.data = stockAnalyses;
      });
  }
}
