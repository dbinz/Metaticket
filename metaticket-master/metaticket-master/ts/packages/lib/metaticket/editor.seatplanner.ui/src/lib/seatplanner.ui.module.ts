import { NgModule } from '@angular/core';
import { SeatplannerUiComponent } from './seatplanner.ui.component';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';



@NgModule({
  declarations: [
    SeatplannerUiComponent
  ],
  imports: [
    CommonModule, FormsModule
  ],
  exports: [
    SeatplannerUiComponent
  ]
})
export class SeatplannerUiModule { }
