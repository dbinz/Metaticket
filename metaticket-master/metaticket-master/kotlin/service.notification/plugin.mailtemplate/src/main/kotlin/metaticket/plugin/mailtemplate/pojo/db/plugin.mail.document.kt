package metaticket.plugin.mailtemplate.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.Binary

data class PDFAttachmentDocument constructor(
    val filename: String,

    val template: String,

    // Map<target, replace-body>
    val replacement: Map<String, String> = emptyMap(),
)

data class FileAttachmentDocument constructor(
    val filename: String,
    val data: Binary,
    val type: String,// – the MIME type
)

data class AttachmentDocument constructor(
    val ticketPdf: PDFAttachmentDocument? = null,
    val files: List<FileAttachmentDocument>? = null,
)

data class MailTemplateDocument constructor(
    @BsonId @BsonRepresentation(BsonType.STRING)
    val id: String,

    // store all template content that used in this template
    // Map<template id, template content>
    val templates: Map<String, String>,

    var attachments: AttachmentDocument? = null
)
