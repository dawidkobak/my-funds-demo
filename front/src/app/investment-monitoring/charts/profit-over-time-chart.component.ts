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
import {Chart, ChartConfiguration, registerables} from 'chart.js';
import 'chartjs-adapter-date-fns';

Chart.register(...registerables);

interface WalletValueInTime {
  date: string;
  value: number;
  ownContribution: number;
}

@Component({
  standalone: false,
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'profit-over-time-chart',
  template: `
    <div class="chart-container">
      <canvas
        #chart
        role="img"
        aria-label="Zysk lub strata portfela w czasie"
      ></canvas>
    </div>
    <p class="chart-note">
      Zysk / strata = wartość aktywów − (zakupy − wpływy ze sprzedaży). Do
      pierwszego notowania po zakupie aktywo jest wyceniane po koszcie zakupu.
    </p>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
        min-width: 0;
      }

      .chart-container {
        position: relative;
        width: 100%;
        height: 400px;
        overflow: hidden;
      }

      canvas {
        display: block;
        max-width: 100% !important;
        max-height: 100% !important;
      }

      .chart-note {
        color: #666;
        font-size: 12px;
        line-height: 1.4;
        margin: 8px 0 0;
      }
    `,
  ],
})
export class ProfitOverTimeChartComponent
  implements AfterViewInit, OnChanges, OnDestroy
{
  @ViewChild('chart') chart!: ElementRef<HTMLCanvasElement>;
  @Input() walletValueInTime: WalletValueInTime[] = [];
  @Input() selectedDate: string | null = null;

  private chartInstance: Chart | null = null;

  ngAfterViewInit(): void {
    this.renderChart();
  }

  ngOnChanges(): void {
    if (this.chart) {
      this.renderChart();
    }
  }

  ngOnDestroy(): void {
    this.chartInstance?.destroy();
  }

  private renderChart(): void {
    const visibleWalletValueInTime = this.walletValueInTime.filter(
      item => !this.selectedDate || item.date <= this.selectedDate
    );
    const currencyFormatter = new Intl.NumberFormat('pl-PL', {
      style: 'currency',
      currency: 'PLN',
    });
    const configuration: ChartConfiguration = {
      type: 'line',
      data: {
        labels: visibleWalletValueInTime.map(item => item.date),
        datasets: [
          {
            label: 'Zysk / strata',
            data: visibleWalletValueInTime.map(
              item => item.value - item.ownContribution
            ),
            fill: {
              target: 'origin',
              above: 'rgba(22,163,74,0.12)',
              below: 'rgba(220,38,38,0.12)',
            },
            borderColor: 'rgb(22,163,74)',
            segment: {
              borderColor: context =>
                (context.p1.parsed.y ?? 0) < 0
                  ? 'rgb(220,38,38)'
                  : 'rgb(22,163,74)',
            },
            borderWidth: 2,
            pointRadius: visibleWalletValueInTime.length > 40 ? 0 : 2,
            pointHoverRadius: 4,
            tension: 0.1,
          },
        ],
      },
      options: {
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {
            beginAtZero: true,
            title: {
              display: true,
              text: 'Zysk / strata (PLN)',
            },
          },
          x: {
            type: 'time',
            max: this.selectedDate || undefined,
            time: {
              parser: 'yyyy-MM-dd',
              unit: 'month',
              displayFormats: {
                month: 'MMM yyyy',
              },
              tooltipFormat: 'dd MMM yyyy',
            },
            title: {
              display: true,
              text: 'Data',
            },
          },
        },
        plugins: {
          title: {
            text: 'Zysk / strata portfela w czasie',
            display: true,
            padding: {
              bottom: 15,
            },
            font: {
              size: 16,
            },
          },
          legend: {
            display: false,
          },
          tooltip: {
            callbacks: {
              label: context =>
                `Zysk / strata: ${currencyFormatter.format(context.parsed.y ?? 0)}`,
              afterLabel: context => [
                `Wartość: ${currencyFormatter.format(visibleWalletValueInTime[context.dataIndex].value)}`,
                `Wkład netto: ${currencyFormatter.format(visibleWalletValueInTime[context.dataIndex].ownContribution)}`,
              ],
            },
          },
        },
      },
    };

    this.chartInstance?.destroy();
    this.chartInstance = new Chart(this.chart.nativeElement, configuration);
  }
}
