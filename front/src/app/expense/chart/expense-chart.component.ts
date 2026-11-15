import {ChangeDetectionStrategy} from '@angular/core';
import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {Chart, registerables} from 'chart.js';
import {ExpenseReportResponse} from '../../../api/expense';

Chart.register(...registerables);

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'expense-chart',
  template: `
    <div class="charts-grid">
      <mat-card>
        <mat-card-header>
          <mat-card-title>
            {{
              report?.periodGranularity === 'day'
                ? 'Wydatki dzienne'
                : 'Wydatki miesięczne'
            }}
          </mat-card-title>
        </mat-card-header>
        <mat-card-content><canvas #monthlyChart></canvas></mat-card-content>
      </mat-card>
      <mat-card>
        <mat-card-header>
          <mat-card-title>Struktura kategorii</mat-card-title>
        </mat-card-header>
        <mat-card-content><canvas #categoryChart></canvas></mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }

      :host .charts-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 16px;
      }

      :host mat-card-content {
        position: relative;
        height: 330px;
        padding-top: 16px;
      }

      @media (max-width: 1050px) {
        :host .charts-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class ExpenseChartComponent
  implements AfterViewInit, OnChanges, OnDestroy
{
  @ViewChild('monthlyChart') monthlyChart?: ElementRef<HTMLCanvasElement>;
  @ViewChild('categoryChart') categoryChart?: ElementRef<HTMLCanvasElement>;
  @Input() report: ExpenseReportResponse | null = null;
  @Input() currency = 'PLN';

  private monthlyChartInstance?: Chart;
  private categoryChartInstance?: Chart;
  private viewInitialized = false;
  private readonly centerTextPlugin = {
    id: 'expenseCenterText',
    beforeDraw: (chart: any) => {
      if (chart.config.type !== 'doughnut') {
        return;
      }
      const text = chart.config.options.plugins.centerText?.text ?? '';
      if (!text) {
        return;
      }
      const chartArea = chart.chartArea;
      chart.ctx.save();
      chart.ctx.font = 'bold 24px sans-serif';
      chart.ctx.textBaseline = 'middle';
      chart.ctx.textAlign = 'center';
      chart.ctx.fillStyle = '#333';
      chart.ctx.fillText(
        text,
        (chartArea.left + chartArea.right) / 2,
        (chartArea.top + chartArea.bottom) / 2
      );
      chart.ctx.restore();
    },
  };

  ngAfterViewInit(): void {
    this.viewInitialized = true;
    this.renderCharts();
  }

  ngOnChanges(): void {
    if (this.viewInitialized) {
      this.renderCharts();
    }
  }

  ngOnDestroy(): void {
    this.monthlyChartInstance?.destroy();
    this.categoryChartInstance?.destroy();
  }

  private renderCharts(): void {
    if (!this.report || !this.monthlyChart || !this.categoryChart) {
      return;
    }
    this.monthlyChartInstance?.destroy();
    this.categoryChartInstance?.destroy();

    const monthlyPoints = this.report.monthly.filter(
      point => point.currency === this.currency
    );
    this.monthlyChartInstance = new Chart(this.monthlyChart.nativeElement, {
      type: 'bar',
      data: {
        labels: monthlyPoints.map(point =>
          this.report?.periodGranularity === 'day'
            ? point.month?.slice(8, 10) ?? ''
            : point.month?.slice(0, 7) ?? ''
        ),
        datasets: [
          {
            label: this.currency,
            data: monthlyPoints.map(point => point.amount),
            backgroundColor: '#0864dc',
            borderRadius: 5,
          },
        ],
      },
      options: {
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {beginAtZero: true},
        },
        plugins: {
          legend: {display: false},
        },
      },
    });

    const categoryPoints = this.report.categories.filter(
      point => point.currency === this.currency && point.amount > 0
    );
    this.categoryChartInstance = new Chart(this.categoryChart.nativeElement, {
      type: 'doughnut',
      data: {
        labels: categoryPoints.map(point => point.name ?? 'Bez kategorii'),
        datasets: [
          {
            data: categoryPoints.map(point => point.amount),
            backgroundColor: categoryPoints.map(
              point => point.color ?? '#78716C'
            ),
            borderWidth: 2,
          },
        ],
      },
      options: {
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        onHover: (event: any, chartElements: any[], chart: any) => {
          if (chartElements.length === 1) {
            if (event.native?.target) {
              event.native.target.style.cursor = 'pointer';
            }
            const dataset = chart.data.datasets[0];
            const value = dataset.data[chartElements[0].index] as number;
            const total = dataset.data.reduce(
              (sum: number, current: any) =>
                sum + (typeof current === 'number' ? current : 0),
              0
            );
            (chart.config.options.plugins as any).centerText = {
              text: total > 0 ? `${((value / total) * 100).toFixed(2)}%` : '',
            };
            chart.update('none');
          } else {
            if (event.native?.target) {
              event.native.target.style.cursor = 'default';
            }
            (chart.config.options.plugins as any).centerText = {text: ''};
            chart.update('none');
          }
        },
        plugins: {
          legend: {
            position: 'bottom',
            labels: {usePointStyle: true},
          },
        },
      },
      plugins: [this.centerTextPlugin],
    });
  }
}
