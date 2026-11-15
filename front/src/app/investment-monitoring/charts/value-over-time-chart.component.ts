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
import 'chartjs-adapter-date-fns';

Chart.register(...registerables);

interface WalletValueInTime {
  date: string;
  value: number;
  ownContribution: number;
}

@Component({
  standalone: false,
  selector: 'value-over-time-chart',
  template: `
    <div #chartContainer class="chart-container">
      <canvas #chart></canvas>
    </div>
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
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ValueOverTimeChartComponent
  implements AfterViewInit, OnChanges, OnDestroy
{
  @ViewChild('chart') chart!: ElementRef<HTMLCanvasElement>;
  @ViewChild('chartContainer') chartContainer!: ElementRef<HTMLDivElement>;
  @Input() walletValueInTime!: WalletValueInTime[];
  @Input() walletName!: string;
  @Input() selectedDate: string | null = null;
  @Input() steppedValueLine = false;

  // Approximately 80% of the 121 net-worth points available on 2026-08-21.
  private readonly maximumDataPoints = 97;
  private config!: ChartConfiguration;
  private chartInstance!: Chart;
  private configChange$ = new Subject<void>();

  ngOnChanges(): void {
    const visibleWalletValueInTime = this.selectVisibleDataPoints(
      (this.walletValueInTime || []).filter(
        item => !this.selectedDate || item.date <= this.selectedDate
      )
    );

    this.config = {
      type: 'line',
      data: {
        labels: visibleWalletValueInTime.map(item => item.date),
        datasets: [
          {
            label: this.walletName || 'Portfel',
            data: visibleWalletValueInTime.map(item => item.value),
            fill: true,
            borderColor: 'rgb(115,230,10)',
            backgroundColor: 'rgba(115,230,10,0.1)',
            borderWidth: 2,
            tension: this.steppedValueLine ? 0 : 0.1,
            stepped: this.steppedValueLine,
            order: this.steppedValueLine ? 2 : 0,
          },
          {
            label: 'Wkład własny',
            data: visibleWalletValueInTime.map(item => item.ownContribution),
            fill: !this.steppedValueLine,
            borderColor: 'rgb(54,162,235)',
            backgroundColor: this.steppedValueLine
              ? 'rgb(54,162,235)'
              : 'rgba(54,162,235,0.1)',
            borderWidth: this.steppedValueLine ? 3 : 2,
            borderDash: this.steppedValueLine ? [6, 4] : [],
            tension: 0,
            stepped: true,
            order: this.steppedValueLine ? 1 : 0,
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
              text: 'Wartość (PLN)',
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
            text: 'Zmiana wartości portfela w czasie',
            display: true,
            padding: {
              bottom: 15,
            },
            font: {
              size: 16,
            },
          },
          legend: {
            display: true,
            position: 'top',
          },
        },
      },
    };
    this.configChange$.next();
  }

  private selectVisibleDataPoints(
    walletValueInTime: WalletValueInTime[]
  ): WalletValueInTime[] {
    if (walletValueInTime.length <= this.maximumDataPoints) {
      return walletValueInTime;
    }

    const contributionChangeIndexes = walletValueInTime
      .map((item, index) => ({
        index,
        change:
          index === 0
            ? 0
            : Math.abs(
                item.ownContribution -
                  walletValueInTime[index - 1].ownContribution
              ),
      }))
      .filter(item => item.index > 0 && item.change > 0)
      .sort((firstItem, secondItem) => secondItem.change - firstItem.change)
      .slice(0, this.maximumDataPoints - 2)
      .map(item => item.index);

    const retainedIndexes = walletValueInTime.map((item, index) => index);
    const requiredIndexes = new Set<number>([
      0,
      walletValueInTime.length - 1,
      ...contributionChangeIndexes,
    ]);
    const firstDate = new Date(walletValueInTime[0].date).getTime();
    const dateRange =
      new Date(walletValueInTime[walletValueInTime.length - 1].date).getTime() -
        firstDate || 1;
    const minimumValue = Math.min(...walletValueInTime.map(item => item.value));
    const valueRange =
      Math.max(...walletValueInTime.map(item => item.value)) - minimumValue ||
      1;

    while (retainedIndexes.length > this.maximumDataPoints) {
      let leastImportantPosition = -1;
      let leastImportantArea = Number.POSITIVE_INFINITY;

      for (
        let position = 1;
        position < retainedIndexes.length - 1;
        position++
      ) {
        const currentIndex = retainedIndexes[position];
        if (requiredIndexes.has(currentIndex)) {
          continue;
        }

        const previousItem = walletValueInTime[retainedIndexes[position - 1]];
        const currentItem = walletValueInTime[currentIndex];
        const nextItem = walletValueInTime[retainedIndexes[position + 1]];
        const previousDate =
          (new Date(previousItem.date).getTime() - firstDate) / dateRange;
        const currentDate =
          (new Date(currentItem.date).getTime() - firstDate) / dateRange;
        const nextDate =
          (new Date(nextItem.date).getTime() - firstDate) / dateRange;
        const previousValue = (previousItem.value - minimumValue) / valueRange;
        const currentValue = (currentItem.value - minimumValue) / valueRange;
        const nextValue = (nextItem.value - minimumValue) / valueRange;
        const triangleArea = Math.abs(
          (previousDate - nextDate) * (currentValue - previousValue) -
            (previousDate - currentDate) * (nextValue - previousValue)
        );

        if (triangleArea < leastImportantArea) {
          leastImportantArea = triangleArea;
          leastImportantPosition = position;
        }
      }

      if (leastImportantPosition === -1) {
        break;
      }
      retainedIndexes.splice(leastImportantPosition, 1);
    }

    return retainedIndexes.map(index => walletValueInTime[index]);
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

  ngOnDestroy(): void {
    if (this.chartInstance) {
      this.chartInstance.destroy();
    }
  }
}
