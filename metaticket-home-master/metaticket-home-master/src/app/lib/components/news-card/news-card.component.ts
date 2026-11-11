import { NgIf } from '@angular/common';
import { Component, Input } from '@angular/core';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NewsItem } from 'src/app/lib/entities/news.response';
import { ImageComponent } from 'src/app/lib/components/image/image.component';
import { newsArticleHref } from 'src/app/lib/constants/news.constants';

@Component({
  selector: 'app-news-card',
  standalone: true,
  imports: [ImageComponent, NgIf, NzTypographyModule],
  templateUrl: './news-card.component.html',
  styleUrls: ['./news-card.component.scss'],
})
export class NewsCardComponent {
  @Input({ required: true }) newsItem!: NewsItem;

  protected readonly newsArticleHref = newsArticleHref;
}
