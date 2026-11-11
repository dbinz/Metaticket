export interface NewsPageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface NewsArticleRow {
    id: number;
    title: string;
    slug: string;
    visible: boolean;
    pinOrder: number | null;
    categories: string[];
    modifiedAt: string;
}

export interface NewsArticleForm {
    id: number;
    title: string;
    slug: string;
    excerpt: string;
    contentHtml: string;
    visible: boolean;
    pinOrder: number | null;
    categoryIds: number[];
}

export interface NewsCategoryOption {
    id: number;
    name: string;
    slug?: string;
}

export interface SaveNewsArticleRequest {
    title: string;
    slug: string;
    excerpt: string;
    contentHtml: string;
    visible: boolean;
    pinOrder: number | null;
    categoryIds: number[];
}

/** Compact article card on the news pin board / picker. */
export interface NewsPinSummary {
    id: number;
    title: string;
    slug: string;
    status: string;
    thumbnailUrl: string;
    modifiedAt: string;
}

export interface NewsPinSlot {
    pinOrder: number;
    article: NewsPinSummary | null;
}

export interface NewsPinBoard {
    slots: NewsPinSlot[];
    maxSlots: number;
}

export interface NewsPinCandidatePage {
    content: NewsPinSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
