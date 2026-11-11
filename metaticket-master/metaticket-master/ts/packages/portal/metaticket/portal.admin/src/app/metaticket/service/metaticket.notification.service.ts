import { Injectable } from '@angular/core';
import { NotificationService } from '../../@core/metaticket/data.service';
import { Observable } from 'rxjs';
import { MailTemplateModel } from '../../@core/metaticket/model';
import { NotificationGateway } from '../gateway/notification.gateway';

@Injectable()
export class MetaticketNotificationService extends NotificationService {
    constructor(private notificationGateway: NotificationGateway) {
        super();
    }
    getMailTemplates(): Observable<MailTemplateModel[]> {
        return this.notificationGateway.fetchMailTemplates();
    }
    getMailPlugins(): Observable<String[]> {
        return this.notificationGateway.fetchMailPlugins();
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
        return this.notificationGateway.createMailTemplate(
            id,
            root,
            customFieldItem,
            priceItem,
            promotionItem,
            ticketPdfTemplate,
            ticketPdfFileName,
        );
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
        return this.notificationGateway.updateMailTemplate(
            id,
            root,
            customFieldItem,
            priceItem,
            promotionItem,
            ticketPdfTemplate,
            ticketPdfFileName,
        );
    }

    getMailTemplate(id: string): Observable<MailTemplateModel> {
        return this.notificationGateway.fetchMailTemplate(id);
    }
}
