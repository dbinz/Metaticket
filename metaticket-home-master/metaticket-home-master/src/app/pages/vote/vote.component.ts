import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import {
  ButtonComponent,
  ImageComponent,
  LayoutComponent,
  NavbarComponent,
  PopupErrorComponent,
} from 'src/app/lib/components';
import { VoteOption } from 'src/app/lib/entities';
import { AuthService, VoteService } from 'src/app/lib/services';
import { Router } from '@angular/router';
import { routeName } from 'src/app/lib/constants';

@Component({
  selector: 'app-vote',
  standalone: true,
  imports: [LayoutComponent, NavbarComponent, ButtonComponent, ImageComponent, PopupErrorComponent, CommonModule],
  templateUrl: './vote.component.html',
})
export class VoteComponent implements OnInit {
  @ViewChild('popupError') popupError!: PopupErrorComponent;

  protected loading = true;
  protected submittingOptionId: string | null = null; // option đang gửi (chế độ chọn 1 -> vote ngay khi bấm)
  protected submitting = false; // đang gửi vote tổng hợp (chế độ chọn nhiều)
  protected multi = false;
  protected maxChoicePerVote = 1;
  protected totalVotes = 0;
  protected options: VoteOption[] = [];
  protected selectedOptionIds: string[] = []; // đang chọn (chưa vote) hoặc đã chọn thật (đã vote, chỉ để đọc)
  protected hasVoted = false;
  protected showLanding = true;

  constructor(
    private voteService: VoteService,
    private authService: AuthService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.showLanding = true;
  }

  protected goToVoting(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate([routeName.login], { queryParams: { returnUrl: `/${routeName.vote}` } });
      return;
    }
    this.showLanding = false;
    this.loadAll();
  }

  private loadAll(): void {
    this.voteService.getMyVote().subscribe((me) => {
      this.hasVoted = me.hasVoted;
      this.voteService.getOptions().subscribe((opts) => {
        this.multi = opts.multi;
        this.maxChoicePerVote = opts.maxChoicePerVote;
        this.totalVotes = opts.totalVotes;
        // Backend trả về mảng đã được sắp xếp theo số vote giảm dần
        this.options = opts.options;
        this.selectedOptionIds = this.hasVoted ? (me.optionIds ?? []) : [];
        this.loading = false;
      });
    });
  }

  protected isSelected(optionId: string): boolean {
    return this.selectedOptionIds.includes(optionId);
  }

  // Chế độ chọn nhiều (chưa vote): bấm vào 1 lựa chọn -> tick/bỏ tick, không tự gửi vote ngay.
  protected toggleOption(optionId: string): void {
    if (this.hasVoted || this.submitting) return;

    if (this.isSelected(optionId)) {
      this.selectedOptionIds = this.selectedOptionIds.filter((id) => id !== optionId);
      return;
    }

    if (this.selectedOptionIds.length >= this.maxChoicePerVote) {
      this.popupError.open(`Bạn chỉ được chọn tối đa ${this.maxChoicePerVote} lựa chọn`);
      return;
    }

    this.selectedOptionIds = [...this.selectedOptionIds, optionId];
  }

  // Chế độ chọn 1: bấm nút "Vote" cạnh lựa chọn -> gửi vote ngay, không cần bước xác nhận riêng.
  protected voteSingle(optionId: string): void {
    if (this.hasVoted || this.submittingOptionId) return;
    this.submittingOptionId = optionId;
    this.doVote([optionId]).add(() => (this.submittingOptionId = null));
  }

  // Chế độ chọn nhiều: bấm nút "Vote" tổng để gửi các lựa chọn đã tick.
  protected submitSelected(): void {
    if (this.selectedOptionIds.length === 0 || this.submitting) return;
    this.submitting = true;
    this.doVote(this.selectedOptionIds).add(() => (this.submitting = false));
  }

  private doVote(optionIds: string[]): Subscription {
    return this.voteService.submitVote({ optionIds }).subscribe({
      // Thành công hoặc 409 (đã vote rồi) đều load lại toàn bộ để đồng bộ đúng trạng thái + số vote mới nhất.
      next: () => this.loadAll(),
      error: () => this.loadAll(),
    });
  }
}
