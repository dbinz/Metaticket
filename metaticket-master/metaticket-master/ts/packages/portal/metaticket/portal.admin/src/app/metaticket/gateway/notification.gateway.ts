import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { MailTemplateModel } from '../../@core/metaticket/model';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi } from '../../../environments/environment';

@Injectable()
export class NotificationGateway {
    constructor(private http: HttpClient) {}

    fetchMailTemplates(): Observable<MailTemplateModel[]> {
        return this.http.get<MailTemplateModel[]>(privateApi(`/notification/mail/templates/fetch`)).pipe(take(1));
    }

    fetchMailTemplate(id: string): Observable<MailTemplateModel> {
        return this.http.get<MailTemplateModel>(privateApi(`/notification/mail/template/fetch?id=${id}`)).pipe(take(1));
    }
    fetchMailPlugins(): Observable<String[]> {
        return this.http.get<String[]>(privateApi(`/notification/mail/plugins/fetch`)).pipe(take(1));
    }

    createMailTemplate(
        id: string,
        root: string,
        customFieldItem: string,
        priceItem: string,
        promotionItem: string,
        ticketPdfTemplate?: string,
        ticketPdfFileName?: string,
    ): Observable<MailTemplateModel> {
        return this.http
            .post<MailTemplateModel>(
                privateApi('/notification/mail/template/create'),
                {
                    id: id,
                    root: root,
                    customFieldItem: customFieldItem,
                    priceItem: priceItem,
                    promotionItem: promotionItem,
                    ticketPdfTemplate: ticketPdfTemplate,
                    ticketPdfFileName: ticketPdfFileName,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    updateMailTemplate(
        id: string,
        root: string,
        customFieldItem: string,
        priceItem: string,
        promotionItem: string,
        ticketPdfTemplate?: string,
        ticketPdfFileName?: string,
    ): Observable<MailTemplateModel> {
        return this.http
            .post<MailTemplateModel>(
                privateApi('/notification/mail/template/update'),
                {
                    id: id,
                    root: root,
                    customFieldItem: customFieldItem,
                    priceItem: priceItem,
                    promotionItem: promotionItem,
                    ticketPdfTemplate: ticketPdfTemplate,
                    ticketPdfFileName: ticketPdfFileName,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }
}
