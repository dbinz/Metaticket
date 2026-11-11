import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import QrScanner from 'qr-scanner';
import { tap, timeout } from 'rxjs/operators';
import { QRCodeService } from '../../metaticket/service/metaticket.service.qrcode';
import { PopupErrorComponent } from '../popup/error/popup.error.component';
import { QRCodeTicketInfoModel } from '../model';
import { PopupOrderInfoComponent } from '../popup/order-info/popup.order-info.component';
import { describeScanFailure, ScanFailure } from '../scan-result';

/** A scan normally answers in < 1s; past this the gate staff should be told to retry instead of waiting forever. */
const SCAN_TIMEOUT_MS = 15000;

const INVALID_FORMAT: ScanFailure = {
    outcome: 'rejected',
    title: 'Mã QR không đúng định dạng',
    message: 'Đây không phải mã vé MetaTicket. Vui lòng quét mã QR trên vé điện tử của khách.',
    status: 0,
};

@Component({
    templateUrl: 'qrcode-scanner.component.html',
    styleUrls: ['./qrcode-scanner.component.scss'],
    selector: 'qrcode-scanner',
})
export class QrcodeScannerComponent implements OnInit, OnDestroy {
    private readonly prefixQRCode = 'MTTKQR:';
    private readonly prefixSlotQRCode = 'MTTKSLOTQR:';
    private readonly privateCode: string;
    private readonly session: string;
    private qrScanner: QrScanner;
    flash: 'on' | 'off' | undefined;

    loading: number = 0;
    hasPermission: boolean = false;
    private currQrCode: string;

    // Scan mode and barcode device properties
    scanMode: 'none' | 'camera' | 'device' = 'none';
    barcodeValue: string = '';

    @ViewChild('videoElem') protected videoElem: ElementRef<HTMLVideoElement>;
    @ViewChild('qrcodeElem') protected qrcodeElem: ElementRef<HTMLDivElement>;
    @ViewChild('barcodeInput') protected readonly barcodeInput: ElementRef<HTMLInputElement>;

    constructor(
        private route: ActivatedRoute,
        private qrcodeS: QRCodeService,
        private toastrService: NbToastrService,
        private dialogService: NbDialogService,
        activatedRoute: ActivatedRoute,
    ) {
        this.session = this.route.snapshot.paramMap.get('sessionId');
        this.privateCode = activatedRoute.snapshot.queryParams['privateCode'];
    }

    async ngOnInit() {
        this.loading += 1;
        await this.qrcodeS
            .verifyPermission(this.session, this.privateCode)
            .pipe(
                tap(
                    e => {
                        this.loading -= 1;
                    },
                    e => {
                        this.loading -= 1;
                    },
                ),
            )
            .toPromise()
            .then(_ => (this.hasPermission = true))
            .catch((error: HttpErrorResponse) => {
                const failure = describeScanFailure(error);
                const message =
                    failure.status === 403 ? 'Link quét vé không hợp lệ hoặc đã thay đổi.' : failure.message;
                this.toastrService.show(message, 'Không mở được trang quét vé', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'danger',
                    duration: 3000000,
                });
                console.log(error);
            });

        if (!this.hasPermission) return;
    }

    private async initCameraScanner() {
        this.qrScanner = new QrScanner(this.videoElem.nativeElement, result => this.onDecodeResult(result), {
            highlightCodeOutline: true,
            highlightScanRegion: true,
            preferredCamera: 'environment',
            maxScansPerSecond: 10,
            calculateScanRegion: video => this._calculateScanRegion(video),
        });
        this.qrcodeElem.nativeElement.append(this.qrScanner.$canvas);
        this.qrcodeElem.nativeElement.style.display = 'flex';
        this.qrScanner.$canvas.style.width = '100%';
        this.qrScanner.setInversionMode('both');
        this.qrScanner.hasFlash().then(f => {
            if (this.qrScanner.isFlashOn()) this.flash = 'on';
            else this.flash = 'off';
        });

        this.qrScanner.start();
    }

    private _calculateScanRegion(video: HTMLVideoElement): QrScanner.ScanRegion {
        // Default scan region calculation. Note that this can be overwritten in the constructor.
        const smallestDimension = Math.min(video.videoWidth, video.videoHeight);
        const scanRegionSize = Math.round(smallestDimension);
        return {
            x: Math.round(video.videoWidth - scanRegionSize),
            y: Math.round(video.videoHeight - scanRegionSize),
            width: scanRegionSize / 2,
            height: scanRegionSize / 2,
            downScaledWidth: scanRegionSize,
            downScaledHeight: scanRegionSize,
        };
    }

    async onDecodeResult(result: QrScanner.ScanResult) {
        if (!this.hasPermission || this.currQrCode || this.loading > 0) return;
        this.currQrCode = result.data;
        this.qrScanner.stop();
        if (!result.data.startsWith(this.prefixQRCode) && !result.data.startsWith(this.prefixSlotQRCode)) {
            // don't know, but on mobile need this to show dialog
            this.qrScanner.start().then(() => this.qrScanner.stop());
            this.dialogService
                .open(PopupErrorComponent, {
                    closeOnBackdropClick: false,
                    context: { ...INVALID_FORMAT },
                })
                .onClose.subscribe(_ => {
                    this.qrScanner.start();
                    this.currQrCode = undefined;
                });
            return;
        }
        this.currQrCode = result.data;
        this.loadTicketInfo(result.data);
    }

    ngOnDestroy(): void {
        this.qrScanner?.stop();
        this.qrScanner?.destroy();
    }

    loadTicketInfo(verifyCode: string) {
        this.loading += 1;
        this.qrcodeS
            .scanCode(this.session, this.privateCode, verifyCode)
            .pipe(
                timeout(SCAN_TIMEOUT_MS),
                tap(
                    e => {
                        this.loading -= 1;
                    },
                    e => {
                        this.loading -= 1;
                    },
                ),
            )
            .subscribe(
                (ticketInfo: QRCodeTicketInfoModel) => {
                    console.log(`qrcode info`, ticketInfo);
                    // don't know, but on mobile need this to show dialog
                    if (this.scanMode === 'camera') {
                        this.qrScanner.start().then(() => this.qrScanner.stop());
                    }
                    this.dialogService
                        .open(PopupOrderInfoComponent, {
                            closeOnBackdropClick: false,
                            context: {
                                ticketInfo: ticketInfo,
                            },
                        })
                        .onClose.subscribe(_ => {
                            if (this.scanMode === 'camera') {
                                this.qrScanner.start();
                            }
                            if (this.scanMode === 'device') {
                                this.resetBarcodeInput();
                            }
                            this.currQrCode = undefined;
                        });
                },
                (error: unknown) => {
                    console.error(`Tải đơn hàng thất bại`, error);
                    // don't know, but on mobile need this to show dialog
                    if (this.scanMode === 'camera') {
                        this.qrScanner.start().then(() => this.qrScanner.stop());
                    }
                    this.dialogService
                        .open(PopupErrorComponent, {
                            closeOnBackdropClick: false,
                            context: { ...describeScanFailure(error) },
                        })
                        .onClose.subscribe(_ => {
                            if (this.scanMode === 'camera') {
                                this.qrScanner.start();
                            } else {
                                this.resetBarcodeInput();
                            }
                            this.currQrCode = undefined;
                        });
                },
            );
    }

    async turnFlashOff() {
        await this.qrScanner.turnFlashOff();
        if (this.qrScanner.isFlashOn()) this.flash = 'on';
        else this.flash = 'off';
    }

    async turnFlashOn() {
        await this.qrScanner.turnFlashOn();
        if (this.qrScanner.isFlashOn()) this.flash = 'on';
        else this.flash = 'off';
    }

    // Barcode scanner device methods
    async toggleScanMode(mode: 'camera' | 'device') {
        this.scanMode = mode;

        // Initialize new mode
        if (mode === 'camera') {
            await this.initCameraScanner();
        } else if (mode === 'device') {
            setTimeout(() => {
                if (this.barcodeInput) {
                    this.barcodeInput.nativeElement.focus();
                }
            }, 300);
        }
    }

    private focusBarcodeInput() {
        if (this.barcodeInput) {
            this.barcodeInput.nativeElement.focus();
        }
    }

    @HostListener('window:click')
    windowClick() {
        if (this.scanMode === 'device') {
            this.focusBarcodeInput();
        }
    }

    processDeviceInput() {
        if (!this.barcodeValue || this.loading > 0 || this.currQrCode) return;

        const qrString = this.barcodeValue.trim();
        this.currQrCode = qrString;

        // Check if the barcode value has the prefix, if not, add it
        if (!qrString.startsWith(this.prefixQRCode) && !qrString.startsWith(this.prefixSlotQRCode)) {
            this.dialogService
                .open(PopupErrorComponent, {
                    closeOnBackdropClick: false,
                    context: { ...INVALID_FORMAT },
                })
                .onClose.subscribe(_ => {
                    this.resetBarcodeInput();
                    // Without this every later scan is ignored (processDeviceInput returns while currQrCode is set).
                    this.currQrCode = undefined;
                });
            return;
        }

        this.loadTicketInfo(this.currQrCode);
    }

    private resetBarcodeInput() {
        this.barcodeValue = '';
        if (this.barcodeInput) {
            this.barcodeInput.nativeElement.focus();
        }
    }
}
