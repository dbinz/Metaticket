import * as ExcelJS from 'exceljs';
import { saveAs } from 'file-saver';

/** Snapshotted line used to build dynamic product columns. */
export interface AccessoryExcelLine {
    accessoryId: string;
    sku: string;
    name: string;
    variantKey?: string | null;
    variantLabel?: string | null;
    quantity: number;
}

/** Column key = accessoryId; header = product name (disambiguate with SKU on collision). */
export interface AccessoryExcelProductColumn {
    accessoryId: string;
    header: string;
}

/** Product row from show catalog (used for Excel column headers). */
export interface AccessoryExcelCatalogProduct {
    accessoryId: string;
    sku: string;
    name: string;
}

/**
 * Builds product columns from the show catalog (active + inactive), not from order lines.
 * Duplicate names are disambiguated with SKU.
 */
export function productColumnsFromCatalog(
    products: AccessoryExcelCatalogProduct[],
): AccessoryExcelProductColumn[] {
    const nameCount = new Map<string, number>();
    for (const p of products || []) {
        nameCount.set(p.name, (nameCount.get(p.name) || 0) + 1);
    }
    return (products || []).map(p => ({
        accessoryId: p.accessoryId,
        header: (nameCount.get(p.name) || 0) > 1 ? `${p.name} (${p.sku})` : p.name,
    }));
}

/** Total quantity of one product (all variants) in an order. */
export function productQty(lines: AccessoryExcelLine[], accessoryId: string): number {
    return (lines || []).filter(l => l.accessoryId === accessoryId).reduce((sum, l) => sum + (l.quantity || 0), 0);
}

/**
 * Multiline variant breakdown, e.g. `1 - TFSHIRT / M`.
 * Only includes lines that carry a variant key/label.
 */
export function variantDetailNotes(lines: AccessoryExcelLine[]): string {
    return (lines || [])
        .filter(l => !!(l.variantKey || l.variantLabel))
        .map(l => {
            const variant = (l.variantLabel || l.variantKey || '').trim();
            return `${l.quantity} - ${l.sku}${variant ? ` / ${variant}` : ''}`;
        })
        .join('\r\n');
}

/** Writes workbook buffer and triggers browser download. */
export async function saveWorkbook(workbook: ExcelJS.Workbook, fileName: string): Promise<void> {
    const buffer = await workbook.xlsx.writeBuffer();
    saveAs(
        new Blob([buffer], {
            type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        }),
        fileName.endsWith('.xlsx') ? fileName : `${fileName}.xlsx`,
    );
}

/** Applies wrap-text to variant-detail cells. */
export function wrapCells(row: ExcelJS.Row, colIndexes1Based: number[]): void {
    for (const idx of colIndexes1Based) {
        row.getCell(idx).alignment = { wrapText: true, vertical: 'top' };
    }
}
