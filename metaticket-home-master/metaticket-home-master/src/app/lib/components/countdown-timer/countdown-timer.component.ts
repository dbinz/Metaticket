import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-countdown-timer',
  templateUrl: './countdown-timer.component.html',
  standalone: true,
})
export class CountdownTimerComponent implements OnInit {
  @Input() minutes: number = 1;
  @Input() seconds: number = 0;

  timer: any;

  ngOnInit(): void {
    this.startTimer();
  }

  startTimer(): void {
    this.timer = setInterval(() => {
      this.updateTimer();
    }, 1000);
  }

  updateTimer(): void {
    if (this.minutes === 0 && this.seconds === 0) {
      this.stopTimer();
    } else {
      if (this.seconds === 0) {
        this.minutes--;
        this.seconds = 59;
      } else {
        this.seconds--;
      }
    }
  }

  stopTimer(): void {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
    clearInterval(this.timer);
  }
}
