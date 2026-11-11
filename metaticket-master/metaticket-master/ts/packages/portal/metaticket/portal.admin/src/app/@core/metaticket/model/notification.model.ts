export interface MailTemplateModel {
    id: string;
    templates: Map<string, string>;
    attachments?: Map<string, string>;
}
