import {Inject, Injectable, PLATFORM_ID} from '@angular/core';
import {isPlatformBrowser} from '@angular/common';
import {LoadingProgressState} from './loading-progress-state';

@Injectable({providedIn: 'root'})
export class LoadingProgressService {
  private state = new LoadingProgressState();
  readonly progress$ = this.state.observeProgress();

  constructor(@Inject(PLATFORM_ID) private platformId: object) {
    if (!isPlatformBrowser(this.platformId)) {
      this.state.disable();
    }
  }

  begin(initialValue = 2): void {
    this.state.begin(initialValue);
  }

  halt(): void {
    this.state.halt();
  }

  finish(): void {
    this.state.finish();
  }

  setProgress(value: number): void {
    this.state.setProgress(value);
  }

  advance(value?: number): void {
    this.state.advance(value);
  }
}
