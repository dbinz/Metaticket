import * as ExcelJS from 'exceljs';
import { saveWorkbook } from './accessory-order-excel.utils';

/** Flat discount row used by template / export / import. */
export interface AccessoryDiscountExcelRow {
    code: string;
    type: string;
    value: number;
    rewardType: string;
    maxDiscountAmount: number | null;
    minOrderAmount: number;
    usageLimit: number | null;
    startAt: number | null;
    endAt: number | null;
    active: boolean;
    /** JSON string for Tiered campaigns; empty otherwise. */
    tiersJson: string;
}

export const DISCOUNT_EXCEL_SHEET = 'discounts';

export const DISCOUNT_EXCEL_HEADERS = [
    'Code',
    'Type',
    'Value',
    'RewardType',
    'MaxDiscountAmount',
    'MinOrderAmount',
    'UsageLimit',
    'StartAt',
    'EndAt',
    'Active',
    'TiersJson',
] as const;

const HEADER_ROW = 2;
const DATA_START_ROW = 3;

/** Formats epoch millis as `yyyy-MM-dd HH:mm` (local Vietnam-friendly plain text). */
export function formatDiscountExcelDate(epochMs: number | null | undefined): string {
    if (epochMs == null || !Number.isFinite(epochMs)) {
        return '';
    }
    const d = new Date(epochMs);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
        d.getMinutes(),
    )}`;
}

/**
 * Parses `yyyy-MM-dd HH:mm`, ISO, or Excel serial date into epoch millis.
 * Returns null for blank cells.
 */
export function parseDiscountExcelDate(raw: unknown): number | null {
    if (raw == null || raw === '') {
        return null;
    }
    if (typeof raw === 'number' && Number.isFinite(raw)) {
        // Excel serial day (approx)
        if (raw > 20000 && raw < 100000) {
            const utc = Math.round((raw - 25569) * 86400 * 1000);
            return utc;
        }
        return Math.trunc(raw);
    }
    const text = String(raw).trim();
    if (!text) {
        return null;
    }
    const localMatch = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?$/.exec(text);
    if (localMatch) {
        const [, y, m, d, hh, mm, ss] = localMatch;
        return new Date(
            Number(y),
            Number(m) - 1,
            Number(d),
            Number(hh),
            Number(mm),
            ss ? Number(ss) : 0,
            0,
        ).getTime();
    }
    const parsed = Date.parse(text);
    return Number.isNaN(parsed) ? null : parsed;
}

/** Builds a downloadable template with headers, notes, and two sample Percentage rows. */
export async function downloadDiscountTemplate(): Promise<void> {
    const workbook = buildDiscountWorkbook([
        {
            code: 'DEMO-AAAA01',
            type: 'Percentage',
            value: 10,
            rewardType: '',
            maxDiscountAmount: 50000,
            minOrderAmount: 0,
            usageLimit: 1,
            startAt: null,
            endAt: null,
            active: true,
            tiersJson: '',
        },
        {
            code: 'DEMO-BBBB02',
            type: 'FixedAmount',
            value: 20000,
            rewardType: '',
            maxDiscountAmount: null,
            minOrderAmount: 100000,
            usageLimit: null,
            startAt: null,
            endAt: null,
            active: true,
            tiersJson: '',
        },
    ], /* withNotes */ true);
    await saveWorkbook(workbook, 'accessory-discount-template.xlsx');
}

/** Exports existing discount rows to Excel. */
export async function downloadDiscountExport(
    rows: AccessoryDiscountExcelRow[],
    fileName: string,
): Promise<void> {
    const workbook = buildDiscountWorkbook(rows, /* withNotes */ false);
    await saveWorkbook(workbook, fileName.endsWith('.xlsx') ? fileName : `${fileName}.xlsx`);
}

/** Parses an uploaded workbook into discount rows (skips blank Code lines). */
export async function parseDiscountImportFile(file: ArrayBuffer): Promise<AccessoryDiscountExcelRow[]> {
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(file);
    const sheet = workbook.getWorksheet(DISCOUNT_EXCEL_SHEET) || workbook.worksheets[0];
    if (!sheet) {
        throw new Error('Workbook không có sheet');
    }
    const rows: AccessoryDiscountExcelRow[] = [];
    sheet.eachRow((row, rowNumber) => {
        if (rowNumber <= HEADER_ROW) {
            return;
        }
        const code = cellText(row.getCell(1)).toUpperCase();
        if (!code) {
            return;
        }
        rows.push({
            code,
            type: cellText(row.getCell(2)) || 'Percentage',
            value: cellNumber(row.getCell(3)) ?? 0,
            rewardType: cellText(row.getCell(4)),
            maxDiscountAmount: cellNumber(row.getCell(5)),
            minOrderAmount: cellNumber(row.getCell(6)) ?? 0,
            usageLimit: cellNumber(row.getCell(7)),
            startAt: parseDiscountExcelDate(row.getCell(8).value),
            endAt: parseDiscountExcelDate(row.getCell(9).value),
            active: cellBool(row.getCell(10), true),
            tiersJson: cellText(row.getCell(11)),
        });
    });
    return rows;
}

/** Generates `{PREFIX}{RANDOM6}` codes, avoiding [exclude] and within-batch duplicates. */
export function generateDiscountCodes(prefix: string, count: number, exclude: Set<string> = new Set()): string[] {
    const safePrefix = (prefix || '')
        .trim()
        .toUpperCase()
        .replace(/[^A-Z0-9_-]/g, '')
        .replace(/-+/g, '-');
    if (!safePrefix) {
        throw new Error('Prefix không hợp lệ');
    }
    if (count < 1 || count > 200) {
        throw new Error('Số lượng phải trong khoảng 1..200');
    }
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    const result: string[] = [];
    const used = new Set<string>([...exclude].map(c => c.trim().toUpperCase()));
    let guard = 0;
    while (result.length < count && guard < count * 50) {
        guard += 1;
        let rand = '';
        for (let i = 0; i < 6; i++) {
            rand += alphabet[Math.floor(Math.random() * alphabet.length)];
        }
        const code = `${safePrefix}${rand}`;
        if (used.has(code)) {
            continue;
        }
        used.add(code);
        result.push(code);
    }
    if (result.length < count) {
        throw new Error('Không generate đủ code (trùng nhiều). Thử lại hoặc đổi prefix.');
    }
    return result;
}

/** Creates workbook with optional instruction row above headers. */
function buildDiscountWorkbook(rows: AccessoryDiscountExcelRow[], withNotes: boolean): ExcelJS.Workbook {
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet(DISCOUNT_EXCEL_SHEET);
    sheet.getCell('A1').value = withNotes
        ? 'Hướng dẫn: Code bắt buộc. Type=Percentage|FixedAmount|Tiered|EarlyBird. Thời gian: yyyy-MM-dd HH:mm. UsageLimit trống = không giới hạn. TiersJson là JSON mảng cho Tiered. Xóa 2 dòng DEMO trước khi import thật.'
        : 'Accessory discount export';
    sheet.mergeCells('A1', 'K1');
    sheet.getRow(HEADER_ROW).values = [...DISCOUNT_EXCEL_HEADERS];
    sheet.getRow(HEADER_ROW).font = { bold: true };
    rows.forEach((r, idx) => {
        const row = sheet.getRow(DATA_START_ROW + idx);
        row.values = [
            r.code,
            r.type,
            r.value,
            r.rewardType || '',
            r.maxDiscountAmount ?? '',
            r.minOrderAmount,
            r.usageLimit ?? '',
            formatDiscountExcelDate(r.startAt),
            formatDiscountExcelDate(r.endAt),
            r.active ? 'TRUE' : 'FALSE',
            r.tiersJson || '',
        ];
    });
    sheet.columns = DISCOUNT_EXCEL_HEADERS.map((h, i) => ({
        header: h,
        key: h,
        width: i === 0 || i === 10 ? 22 : 16,
    }));
    return workbook;
}

function cellText(cell: ExcelJS.Cell): string {
    const v = cell?.value;
    if (v == null) {
        return '';
    }
    if (typeof v === 'object' && 'text' in (v as object)) {
        return String((v as { text?: string }).text || '').trim();
    }
    if (typeof v === 'object' && 'result' in (v as object)) {
        return String((v as { result?: unknown }).result ?? '').trim();
    }
    return String(v).trim();
}

function cellNumber(cell: ExcelJS.Cell): number | null {
    const text = cellText(cell);
    if (!text) {
        return null;
    }
    const n = Number(text.replace(/,/g, ''));
    return Number.isFinite(n) ? n : null;
}

function cellBool(cell: ExcelJS.Cell, defaultValue: boolean): boolean {
    const text = cellText(cell).toLowerCase();
    if (!text) {
        return defaultValue;
    }
    if (['1', 'true', 'yes', 'y', 'x'].includes(text)) {
        return true;
    }
    if (['0', 'false', 'no', 'n'].includes(text)) {
        return false;
    }
    return defaultValue;
}
