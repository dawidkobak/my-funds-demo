import {Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AppService {
  private inactivityTimer!: any;
  private maxInactivityTime = 60 * 60 * 1000;

  private appOpenTimer!: any;
  private maxAppOpenTime = 6 * 60 * 60 * 1000;

  private lastInteractionTime: Date;

  constructor() {
    this.lastInteractionTime = new Date();

    this.startAppOpenTimer();

    // List of events to listen for user activity
    const events = ['mousemove', 'keydown', 'touchstart', 'scroll'];

    // Add event listeners to track the last interaction time
    events.forEach(event =>
      document.addEventListener(event, () => this.updateLastInteractionTime())
    );
  }

  private updateLastInteractionTime() {
    this.lastInteractionTime = new Date();
    this.resetInactivityTimer();
  }

  private startInactivityTimer() {
    this.inactivityTimer = setTimeout(() => {
      this.checkForRefresh();
    }, this.maxInactivityTime);
  }

  private resetInactivityTimer() {
    clearTimeout(this.inactivityTimer);
    this.startInactivityTimer();
  }

  private startAppOpenTimer() {
    this.appOpenTimer = setTimeout(() => {
      this.checkForRefresh();
    }, this.maxAppOpenTime);
  }

  private checkForRefresh() {
    const currentTime = new Date();
    const timeSinceLastInteraction =
      currentTime.getTime() - this.lastInteractionTime.getTime();

    // Check if the app has been open for the max time AND the user has been inactive for the max inactivity time
    if (timeSinceLastInteraction >= this.maxInactivityTime) {
      window.location.reload();
    } else {
      // User was active within the inactivity period, so we start the app open timer again
      this.startAppOpenTimer();
    }
  }
}
