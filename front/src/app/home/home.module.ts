import {NgModule} from '@angular/core';
import {HttpClientModule} from '@angular/common/http';
import {LayoutModule} from '../layout/layout.module';
import {FlexLayoutModule} from '@ngbracket/ngx-layout';
import {MatSidenavModule} from '@angular/material/sidenav';
import {MatCardModule} from '@angular/material/card';
import {CommonModule} from '@angular/common';
import {FontAwesomeModule} from '@fortawesome/angular-fontawesome';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatDividerModule} from '@angular/material/divider';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatListModule} from '@angular/material/list';
import {MatLineModule, MatOptionModule} from '@angular/material/core';
import {HomeRoutingModule} from './home-routing.module';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {DateValueModule} from '../core/date/date-value.module';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatSlideToggleModule} from '@angular/material/slide-toggle';
import {MatMenuModule} from '@angular/material/menu';
import {HomeComponent} from './index/home.component';
import {MatTableModule} from '@angular/material/table';
import {MatTabsModule} from '@angular/material/tabs';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatDialogModule} from '@angular/material/dialog';
import {MatSnackBarModule} from '@angular/material/snack-bar';
import {A11yModule} from '@angular/cdk/a11y';
import {MatGridListModule} from '@angular/material/grid-list';
import {MatSortModule} from '@angular/material/sort';
import {MatPaginatorModule} from '@angular/material/paginator';
import {MatTreeModule} from '@angular/material/tree';
import {MatChipsModule} from '@angular/material/chips';
import {MatStepperModule} from '@angular/material/stepper';
import {WalletListComponent} from '../wallet/wallet-list.component';
import {WalletModifyDialog} from '../wallet/dialog/wallet-modify-dialog.component';
import {WalletDeleteDialog} from '../wallet/dialog/wallet-delete-dialog.component';
import {StockListComponent} from '../stock/stock-list.component';
import {StockCreateDialog} from '../stock/dialog/stock-create-dialog.component';
import {StockModifyDialog} from '../stock/dialog/stock-modify-dialog.component';
import {StockDeleteDialog} from '../stock/dialog/stock-delete-dialog.component';
import {TransactionListComponent} from '../transaction/transaction-list.component';
import {TransactionCreateDialog} from '../transaction/dialog/transaction-create-dialog.component';
import {TransactionModifyDialog} from '../transaction/dialog/transaction-modify-dialog.component';
import {TransactionDeleteDialog} from '../transaction/dialog/transaction-delete-dialog.component';
import {PiggyBankListComponent} from '../piggy-bank/piggy-bank-list.component';
import {PiggyBankCreateDialog} from '../piggy-bank/dialog/piggy-bank-create-dialog.component';
import {PiggyBankModifyDialog} from '../piggy-bank/dialog/piggy-bank-modify-dialog.component';
import {PiggyBankDeleteDialog} from '../piggy-bank/dialog/piggy-bank-delete-dialog.component';
import {StockQuoteListComponent} from '../stock-quote/stock-quote-list.component';
import {StockQuoteCreateDialog} from '../stock-quote/dialog/stock-quote-create-dialog.component';
import {InvestmentMonitoringComponent} from '../investment-monitoring/investment-monitoring.component';
import {PortfolioCompositionChartComponent} from '../investment-monitoring/charts/portfolio-composition-chart.component';
import {ValueOverTimeChartComponent} from '../investment-monitoring/charts/value-over-time-chart.component';
import {ProfitOverTimeChartComponent} from '../investment-monitoring/charts/profit-over-time-chart.component';
import {ExpenseListComponent} from '../expense/expense-list.component';
import {ExpenseListCreateDialog} from '../expense/dialog/expense-list-create-dialog.component';
import {ExpenseCreateDialog} from '../expense/dialog/expense-create-dialog.component';
import {ExpenseModifyDialog} from '../expense/dialog/expense-modify-dialog.component';
import {ExpenseDeleteDialog} from '../expense/dialog/expense-delete-dialog.component';
import {ExpenseChartComponent} from '../expense/chart/expense-chart.component';
import {ExpenseImportDialog} from '../expense/dialog/expense-import-dialog.component';
import {ExpenseManageDialog} from '../expense/dialog/expense-manage-dialog.component';
import {StockAnalysisListComponent} from '../stock-analysis/stock-analysis-list.component';
import {StockAnalysisCreateDialog} from '../stock-analysis/dialog/stock-analysis-create-dialog.component';
import {StockAnalysisReportDialog} from '../stock-analysis/dialog/stock-analysis-report-dialog.component';
import {BondPositionListComponent} from '../bond/bond-position-list.component';

@NgModule({
  declarations: [
    HomeComponent,
    WalletListComponent,
    WalletModifyDialog,
    WalletDeleteDialog,
    StockListComponent,
    StockCreateDialog,
    StockModifyDialog,
    StockDeleteDialog,
    TransactionListComponent,
    TransactionCreateDialog,
    TransactionModifyDialog,
    TransactionDeleteDialog,
    PiggyBankListComponent,
    PiggyBankCreateDialog,
    PiggyBankModifyDialog,
    PiggyBankDeleteDialog,
    StockQuoteListComponent,
    StockQuoteCreateDialog,
    InvestmentMonitoringComponent,
    PortfolioCompositionChartComponent,
    ValueOverTimeChartComponent,
    ProfitOverTimeChartComponent,
    ExpenseListComponent,
    ExpenseListCreateDialog,
    ExpenseCreateDialog,
    ExpenseModifyDialog,
    ExpenseDeleteDialog,
    ExpenseChartComponent,
    ExpenseImportDialog,
    ExpenseManageDialog,
    StockAnalysisListComponent,
    StockAnalysisCreateDialog,
    StockAnalysisReportDialog,
    BondPositionListComponent,
  ],
  imports: [
    HomeRoutingModule,
    HttpClientModule,
    LayoutModule,
    FlexLayoutModule,
    MatSidenavModule,
    MatCardModule,
    CommonModule,
    FontAwesomeModule,
    MatFormFieldModule,
    MatDividerModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatTooltipModule,
    MatListModule,
    MatLineModule,
    MatInputModule,
    MatOptionModule,
    MatSelectModule,
    DateValueModule,
    MatDatepickerModule,
    MatSlideToggleModule,
    MatMenuModule,
    MatTableModule,
    MatTabsModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    MatSnackBarModule,
    A11yModule,
    MatGridListModule,
    MatSortModule,
    MatPaginatorModule,
    MatTreeModule,
    MatChipsModule,
    MatStepperModule,
    FormsModule,
  ],
})
export class HomeModule {}
