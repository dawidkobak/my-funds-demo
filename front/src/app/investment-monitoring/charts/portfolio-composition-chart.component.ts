import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {Chart, ChartConfiguration, registerables} from 'chart.js';
import {Subject} from 'rxjs';
import ChartDataLabels from 'chartjs-plugin-datalabels';

Chart.register(...registerables);
Chart.unregister(ChartDataLabels);

interface PortfolioAsset {
  id: number;
  name: string;
  value: number;
  color: string;
  purchaseCost: number;
  purchaseDate: string;
}

@Component({
  standalone: false,
  selector: 'portfolio-composition-chart',
  template: `
    <div class="chart-layout">
      <div #chartContainer class="chart-container">
        <canvas #chart></canvas>
      </div>
      <ul class="chart-legend" aria-label="Skład portfela">
        <li class="chart-legend-item" *ngFor="let asset of assets">
          <span class="chart-legend-label">
            <span
              class="chart-legend-color"
              [style.background-color]="asset.color"
            >
            </span>
            <span>{{ asset.name }}</span>
          </span>
          <span class="chart-legend-value">{{
            formatCurrency(asset.value)
          }}</span>
        </li>
      </ul>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
        min-width: 0;
      }

      .chart-layout {
        display: grid;
        grid-template-columns: minmax(0, 1fr) minmax(230px, 0.9fr);
        align-items: center;
        gap: 16px;
        width: 100%;
        min-width: 0;
      }

      .chart-container {
        position: relative;
        width: 100%;
        min-width: 0;
        max-width: 100%;
        height: 400px;
        overflow: hidden;
      }

      canvas {
        display: block;
        max-width: 100% !important;
        max-height: 100% !important;
      }

      .chart-legend {
        display: grid;
        gap: 10px;
        min-width: 0;
        margin: 0;
        padding: 0;
        list-style: none;
      }

      .chart-legend-item {
        display: grid;
        grid-template-columns: minmax(0, 1fr) max-content;
        align-items: center;
        gap: 16px;
        min-width: 0;
      }

      .chart-legend-label {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
      }

      :host .chart-legend-label > span:last-child {
        min-width: 0;
        overflow-wrap: anywhere;
      }

      .chart-legend-color {
        flex: 0 0 32px;
        height: 12px;
      }

      .chart-legend-value {
        text-align: right;
        white-space: nowrap;
        font-variant-numeric: tabular-nums;
      }

      @media (max-width: 900px) {
        .chart-layout {
          grid-template-columns: 1fr;
        }

        .chart-container {
          height: 320px;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioCompositionChartComponent
  implements AfterViewInit, OnChanges, OnDestroy
{
  @ViewChild('chart') chart!: ElementRef<HTMLCanvasElement>;
  @ViewChild('chartContainer') chartContainer!: ElementRef<HTMLDivElement>;
  @Input() assets!: PortfolioAsset[];

  private config!: ChartConfiguration;
  private chartInstance!: Chart;
  private configChange$ = new Subject<void>();
  private centerTextPlugin: any;

  constructor() {
    this.centerTextPlugin = {
      id: 'centerText',
      beforeDraw: (chart: any) => {
        if (chart.config.type !== 'doughnut') {
          return;
        }
        const ctx = chart.ctx;
        const chartArea = chart.chartArea;

        ctx.save();

        const centerX = (chartArea.left + chartArea.right) / 2;
        const centerY = (chartArea.top + chartArea.bottom) / 2;

        const text = chart.config.options.plugins.centerText?.text || '';
        if (!text) {
          ctx.restore();
          return;
        }

        ctx.font = 'bold 24px sans-serif';
        ctx.textBaseline = 'middle';
        ctx.textAlign = 'center';
        ctx.fillStyle = '#333';

        ctx.fillText(text, centerX, centerY);
        ctx.restore();
      },
    };
  }

  ngOnChanges(): void {
    this.config = {
      type: 'doughnut',
      data: {
        labels: this.assets?.map(asset => asset.name) || [],
        datasets: [
          {
            label: 'Wartość',
            data: this.assets?.map(asset => asset.value) || [],
            backgroundColor: this.assets?.map(asset => asset.color) || [],
            borderColor: this.assets?.map(asset => asset.color) || [],
            borderWidth: 2,
            hoverOffset: 10,
          },
        ],
      },
      options: {
        animation: false,
        layout: {
          padding: {
            top: 20,
            bottom: 20,
          },
        },
        responsive: true,
        maintainAspectRatio: false,
        onHover: (event: any, chartElement: any, chart: any) => {
          if (chartElement.length === 1) {
            event.native.target.style.cursor = 'pointer';
            const index = chartElement[0].index;
            const dataset = chart.data.datasets[0];
            const value = dataset.data[index] as number;
            const total = dataset.data.reduce((sum: number, current: any) => {
              const currentValue = typeof current === 'number' ? current : 0;
              return sum + currentValue;
            }, 0);
            const percentage = ((value / total) * 100).toFixed(2);
            (chart.config.options.plugins as any).centerText = {
              text: `${percentage}%`,
            };
            chart.update('none');
          } else if (chartElement.length === 0) {
            event.native.target.style.cursor = 'default';
            (chart.config.options.plugins as any).centerText = {text: ''};
            chart.update('none');
          }
        },
        plugins: {
          title: {
            text: 'Skład portfela',
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
              label: context => {
                return `${context.label}: ${this.formatCurrency(
                  context.parsed as number
                )}`;
              },
            },
          },
          datalabels: {
            display: context => {
              const labels = context.chart?.data?.labels || [];
              return labels.length <= 6;
            },
            color: 'white',
            font: {
              weight: 'bold',
              size: 12,
            },
            formatter: (value, context) => {
              const dataset = context.chart.data.datasets[0].data;
              const total = dataset.reduce((sum: number, current) => {
                const currentValue = typeof current === 'number' ? current : 0;
                return sum + currentValue;
              }, 0);

              if (total > 0 && typeof value === 'number') {
                const percentage = ((value / total) * 100).toFixed(1);
                return value > 0 ? `${percentage}%` : '';
              }
              return '';
            },
          },
        },
      },
      plugins: [ChartDataLabels, this.centerTextPlugin],
    };
    this.configChange$.next();
  }

  ngAfterViewInit(): void {
    this.chartInstance = new Chart(this.chart.nativeElement, this.config);

    this.configChange$.subscribe(() => {
      this.chartInstance.destroy();
      requestAnimationFrame(() => {
        this.chartInstance = new Chart(this.chart.nativeElement, this.config);
      });
    });
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('pl-PL', {
      style: 'currency',
      currency: 'PLN',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
      .format(value)
      .replace(/^(-?\d+)(?=,)/, integerPart =>
        integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0')
      );
  }

  ngOnDestroy(): void {
    if (this.chartInstance) {
      this.chartInstance.destroy();
    }
  }
}
