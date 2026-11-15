import {Observable, of, Subject, timer} from 'rxjs';
import {
  map,
  shareReplay,
  startWith,
  switchMap,
  take,
  tap,
} from 'rxjs/operators';

interface LoadingStateSnapshot {
  action: 'start' | 'complete' | 'set' | 'stop' | 'increment' | null;
  value: number;
  initialValue: number;
}

export class LoadingProgressState {
  private state: LoadingStateSnapshot = {
    action: null,
    value: 0,
    initialValue: 0,
  };
  private requests = 0;
  private disabled = false;
  private stream$ = new Subject<LoadingStateSnapshot>();

  observeProgress(): Observable<number> {
    return this.stream$.asObservable().pipe(
      switchMap(s => this.animateProgress(s)),
      startWith({
        action: null,
        value: 0,
      }),
      // @ts-ignore
      shareReplay(),
      map((s: LoadingStateSnapshot) => s.value)
    );
  }

  begin(initialValue = 2): void {
    if (this.disabled) {
      return;
    }

    this.updateState({action: 'start', initialValue});
  }

  halt(): void {
    this.updateState({action: 'stop'});
  }

  finish(): void {
    this.updateState({action: 'complete'});
  }

  disable(): void {
    this.disabled = true;
  }

  setProgress(value: number): void {
    this.updateState({action: 'set', value});
  }

  advance(value = 0): void {
    this.updateState({action: 'increment', value});
  }

  private updateState(state: Partial<LoadingStateSnapshot>, emitEvent = true): void {
    switch (state.action) {
      case 'start':
        this.requests = (this.requests || 0) + 1;
        break;
      case 'complete':
        this.requests = (this.requests || 1) - 1;
        if (this.requests > 0) {
          return;
        }

        break;
      case 'stop':
        this.requests = 0;
        break;
      case 'increment':
        state.value = this.nextProgress(state.value);
        break;
    }

    this.state = {
      ...this.state,
      action: null,
      ...state,
    };
    if (emitEvent) {
      this.stream$.next(this.state);
    }
  }

  private animateProgress = (s: LoadingStateSnapshot) => {
    let state$: Observable<Partial<LoadingStateSnapshot>> = of(s);
    switch (s.action) {
      case 'start':
      case 'increment':
      case 'set': {
        if (this.requests > 0) {
          state$ = timer(0, 250).pipe(
            map(t =>
              t === 0
                ? {...s, value: this.state.value || s.initialValue}
                : {...s, value: this.nextProgress()}
            )
          );
        }
        break;
      }
      case 'complete':
      case 'stop': {
        // Attempt to aggregate any start/complete calls within 500ms:
        state$ =
          s.value === 0
            ? of({...s})
            : timer(0, 500).pipe(
                take(2),
                map(t => ({value: t === 0 ? 100 : 0}))
              );
        break;
      }
    }

    return state$.pipe(
      map(next => ({...next, action: 'set'}) as LoadingStateSnapshot),
      tap(next => this.updateState(next, false))
    );
  };

  private nextProgress(rnd = 0): number {
    const stat = this.state.value;
    if (stat >= 99) {
      rnd = 0;
    }

    if (rnd === 0) {
      if (stat >= 0 && stat < 25) {
        // Start out between 3 - 6% increments
        rnd = Math.random() * (5 - 3 + 1) + 3;
      } else if (stat >= 25 && stat < 65) {
        // increment between 0 - 3%
        rnd = Math.random() * 3;
      } else if (stat >= 65 && stat < 90) {
        // increment between 0 - 2%
        rnd = Math.random() * 2;
      } else if (stat >= 90 && stat < 99) {
        // finally, increment it .5 %
        rnd = 0.5;
      } else {
        // after 99%, don't increment:
        rnd = 0;
      }
    }

    return rnd + stat;
  }
}
