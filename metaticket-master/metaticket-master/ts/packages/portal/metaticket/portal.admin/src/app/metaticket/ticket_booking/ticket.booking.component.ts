import { AsyncPipe, NgForOf } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
    NbAutocompleteModule,
    NbButtonModule,
    NbCardModule,
    NbGlobalPhysicalPosition,
    NbInputModule,
    NbOptionModule,
    NbSpinnerModule,
    NbToastrService,
} from '@nebular/theme';
import { AdminService, ShowService } from '../../@core/metaticket/data.service';
import {
    BookingUploadErrorResponse,
    BookingUploadSuccessResponse,
    OrderSummaryModel,
    ShowModel,
    ShowSessionModel,
    TicketReservationModel,
} from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { OrderGateway } from '../gateway/order.gateway';
import { TicketGateway } from '../gateway/ticket.gateway';
import { Angular2SmartTableModule, LocalDataSource, Settings, IColumnType } from 'angular2-smart-table';
import removeAccents from 'remove-accents';
import { of, ReplaySubject, Subject } from 'rxjs';
import { catchError, finalize, map, switchMap, tap } from 'rxjs/operators';
import { TicketBookingDataSource } from './ticket.booking.model';
import { ThemeModule } from '../../@theme/theme.module';

@Component({
    selector: 'app-ticket-booking',
    templateUrl: './ticket.booking.component.html',
    styleUrls: ['./ticket.booking.component.scss'],
    standalone: true,
    imports: [
        ThemeModule,
        NbCardModule,
        NbSpinnerModule,
        AsyncPipe,
        FormsModule,
        NbInputModule,
        NbOptionModule,
        Angular2SmartTableModule,
        NgForOf,
        NbAutocompleteModule,
        NbButtonModule,
        ReactiveFormsModule,
    ],
})
export class TicketBookingComponent {
    protected readonly source: LocalDataSource = new LocalDataSource();

    private showList: ShowModel[] = [];
    private sessionList: ShowSessionModel[] = [];
    protected filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    protected filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    @ViewChild('autoInputShow') private readonly inputShow!: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') private readonly inputSession!: ElementRef<HTMLInputElement>;
    @ViewChild('fileInput') private readonly fileInputRef!: ElementRef<HTMLInputElement>;

    protected uploadForm: FormGroup;
    protected selectedFile: File | null = null;
    protected isLoading = false;
    protected uploadStatus: 'idle' | 'success' | 'partial_success' | 'error' = 'idle';
    protected feedbackMessage: string | null = null;
    protected errorDetails: string[] | null = null;
    protected successDetails: string[] | null = null;
    protected failedBookingDetails: { [key: string]: string } | null = null;

    // Allowed file extensions from backend
    protected readonly allowedExtensions = ['.csv', '.xlsx', '.xls'];
    protected readonly allowedMimeTypes = [
        'text/csv',
        'application/vnd.ms-excel', // .xls
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', // .xlsx
    ]; // Mime types are generally more reliable but check browser compatibility

    protected loading: number = 0;
    protected selectedShow?: ShowModel;
    protected selectedSession?: ShowSessionModel;

    protected settings: Settings = {
        hideSubHeader: false,
        actions: {
            add: false,
            edit: false,
            delete: false,
            position: 'left',
        },
        columns: {
            sid: {
                title: 'sid',
                type: IColumnType.Text,
            },
            priceCat: {
                title: 'Hạng vé',
                type: IColumnType.Text,
            },
            quantity: {
                title: 'Số lượng',
                type: IColumnType.Text,
            },
            promotionCode: {
                title: 'Mã khuyến mãi',
                type: IColumnType.Text,
            },
            reservationStatus: {
                title: 'Trạng thái đặt vé',
                type: IColumnType.Text,
            },
            orderStatus: {
                title: 'Trạng thái đơn hàng',
                type: IColumnType.Text,
            },
            orderCode: {
                title: 'Mã đơn hàng',
                type: IColumnType.Text,
            },
            customerName: {
                title: 'Tên khách hàng',
                type: IColumnType.Text,
            },
            customerPhone: {
                title: 'Số DT khách hàng',
                type: IColumnType.Text,
            },
            customerEmail: {
                title: 'Email khách hàng',
                type: IColumnType.Text,
            },
            deliveryName: {
                title: 'Tên người nhận',
                type: IColumnType.Text,
            },
            deliveryPhone: {
                title: 'Số DT người nhận',
                type: IColumnType.Text,
            },
            deliveryAddress: {
                title: 'Địa chỉ người nhận',
                type: IColumnType.Text,
            },
        },
    };

    constructor(
        readonly showService: ShowService,
        private readonly ticketGateway: TicketGateway,
        private readonly orderGateway: OrderGateway,
        private readonly toastrService: NbToastrService,
        private readonly fb: FormBuilder,
        private readonly adminService: AdminService,
    ) {
        this.loading += 1;
        showService
            .getShows$()
            .pipe(
                tap(e => {
                    this.loading -= 1;
                }),
            )
            .subscribe(
                shows => {
                    if (!shows) return;
                    this.showList = shows.sort((s1, s2) => (s1.createdAt > s2.createdAt ? -1 : 1));
                    this.filteredShowList$.next(this.showList);
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Tải show thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
        this.uploadForm = this.fb.group({
            showId: ['', Validators.required],
            sessionId: ['', Validators.required],
            fileInput: [null, Validators.required], // For validation feedback, actual file stored separately
            customerUserId: [''],
        });
    }

    onInputShowChange(value: string) {
        this.filteredShowList$.next(this.filterShow(value));
    }

    async onInputSessionChange(value: string) {
        this.filteredSessionList$.next(this.filterSession(value));
    }

    onSelectionShowChange($event: string) {
        if (!this.inputShow) return;

        this.filteredShowList$.next(this.showList.filter(s => s.id === $event));
        this.selectedShow = this.showList.filter(s => s.id === $event)[0];
        this.inputShow.nativeElement.value = this.selectedShow?.showName;

        this.inputSession.nativeElement.value = '';
        this.source.load([]);
        this.onSelectionSessionChange(undefined);
        this.clearFeedback(); // Clear feedback when show changes

        this.sessionList = this.selectedShow.sessions;
        this.filteredSessionList$.next(this.sessionList);
        if (this.sessionList.length === 1) {
            this.inputSession.nativeElement.value = this.sessionList[0].id;
            this.uploadForm.controls["sessionId"].setValue(this.sessionList[0].id);
            this.onSelectionSessionChange(this.sessionList[0].id);
        }
    }

    getSessionName(session: ShowSessionModel): string {
        let name: string = '';
        if (session) {
            if (session.code?.trim()?.length > 0 && session.sessionName)
                name = `${session.code} - ${session.sessionName}`;
            else if (session.code?.trim()?.length > 0 && !session.sessionName) name = session.code;
            else if (session.sessionName && session.code?.trim()?.length <= 0) name = session.sessionName;
        }
        return name;
    }

    onSelectionSessionChange(sessionId?: string) {
        this.clearFeedback(); // Clear feedback when session changes
        this.filteredSessionList$.next(this.sessionList.filter(s => s.id === sessionId));
        this.selectedSession = this.sessionList.filter(s => s.id === sessionId)[0];
        if (this.inputSession) {
            this.inputSession.nativeElement.value = this.getSessionName(this.selectedSession);
        }
        if (!this.selectedSession) return;

        this.loadTicketBookingData();
    }

    private loadTicketBookingData() {
        this.loading += 1;
        this.adminService
            .loadTicketBooking(this.selectedShow!.id, this.selectedSession!.id)
            .pipe(
                // 1. Map initial booking data
                map(resp =>
                    resp
                        .map(
                            e =>
                                <TicketBookingDataSource>{
                                    id: e.id,
                                    sid: e.sid,
                                    priceCat: e.priceCat, // Added missing field from settings
                                    quantity: e.quantity,
                                    customerName: e.customerName,
                                    customerPhone: e.customerPhone,
                                    customerEmail: e.customerEmail,
                                    deliveryName: e.deliveryName,
                                    deliveryPhone: e.deliveryPhone,
                                    // Combine address fields if needed by table, or keep separate
                                    deliveryAddress:
                                        `${e.address || ''}${e.wardName ? ', ' + e.wardName : ''}${e.districtName ? ', ' + e.districtName : ''}${e.provinceName ? ', ' + e.provinceName : ''}`.replace(
                                            /^, /,
                                            '',
                                        ), // Construct full address
                                    provinceName: e.provinceName,
                                    districtName: e.districtName,
                                    wardName: e.wardName,
                                    address: e.address,
                                    customFields: e.customFields,
                                    promotionCode: e.promotionCode,
                                    reservationStatus: 'Đang tải...',
                                    orderStatus: 'Đang tải...',
                                    paymentStatus: '_',
                                    reservationId: e.reservationId,
                                    orderId: e.orderId,
                                    paymentId: e.paymentId,
                                    version: e.version,
                                    lastUpdated: e.lastUpdated,
                                    createdAt: e.createdAt,
                                },
                        )
                        .sort((a, b) => b.createdAt - a.createdAt),
                ),
                // 2. Fetch reservation statuses if needed
                switchMap((initialBookings: TicketBookingDataSource[]) => {
                    const reservationIds = initialBookings.map(b => b.reservationId).filter((id): id is string => !!id); // Filter out null/undefined and type guard

                    if (reservationIds.length === 0) {
                        // No reservations to fetch, update status and return
                        const updatedBookings = initialBookings.map(b => ({
                            ...b,
                            // Status if ID exists but not found vs no ID
                            reservationStatus: b.reservationId ? 'Không tìm thấy' : 'Không có',
                        }));
                        return of(updatedBookings); // Wrap in 'of' to return an Observable
                    }

                    // Fetch reservations
                    return this.ticketGateway.fetchReservationByIds(reservationIds).pipe(
                        map((reservations: TicketReservationModel[]) => {
                            // Create a map for efficient lookup: reservationId -> status
                            const statusMap = new Map<string, string>();
                            reservations.forEach(res => {
                                if (res && res.id && res.status) {
                                    statusMap.set(res.id, res.status);
                                }
                            });

                            // Update the initial bookings with fetched statuses
                            return initialBookings.map(booking => {
                                let newStatus = 'Không có'; // Default if no reservationId
                                if (booking.reservationId) {
                                    newStatus = statusMap.get(booking.reservationId) ?? 'Không tìm thấy';
                                }
                                return { ...booking, reservationStatus: newStatus };
                            }); // Return the merged data
                        }),
                        catchError(err => {
                            console.error('Error fetching reservation statuses:', err);
                            this.toastrService.show(
                                'Không thể tải trạng thái reservation.',
                                'Lỗi phụ', // Indicate it's a secondary error
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'warning', duration: 5000 },
                            );
                            // Return initial bookings but mark status as error
                            const errorBookings = initialBookings.map(b => ({
                                ...b,
                                reservationStatus: b.reservationId ? 'Lỗi tải status' : 'Không có',
                            }));
                            return of(errorBookings); // Still proceed with the initial data
                        }),
                    );
                }),
                // 3. Fetch order statuses if needed
                switchMap((initialBookings: TicketBookingDataSource[]) => {
                    const reservationIds = initialBookings.map(b => b.reservationId).filter((id): id is string => !!id); // Filter out null/undefined and type guard

                    if (reservationIds.length === 0) {
                        const updatedBookings = initialBookings.map(b => ({
                            ...b,
                            orderStatus: b.orderId ? 'Không tìm thấy' : 'Không có',
                            orderCode: 'Không có',
                        }));
                        return of(updatedBookings); // Wrap in 'of' to return an Observable
                    }

                    // Fetch order
                    return this.orderGateway
                        .fetchOrderByReservation(this.selectedShow!.id, this.selectedSession!.id, reservationIds)
                        .pipe(
                            map((orders: OrderSummaryModel[]) => {
                                // Create a map for efficient lookup: orderId -> status
                                const orderMap = new Map<string, OrderSummaryModel>();
                                orders.forEach(res => {
                                    if (res && res.orderId && res.orderStatus) {
                                        orderMap.set(res.orderId, res);
                                    }
                                });

                                // Update the initial bookings with fetched statuses
                                return initialBookings.map(booking => {
                                    let newStatus = 'Không có';
                                    let newOrderCode = 'Không có';
                                    if (booking.orderId) {
                                        newStatus = orderMap.get(booking.orderId)?.orderStatus ?? 'Không tìm thấy';
                                        newOrderCode = orderMap.get(booking.orderId)?.orderCode ?? 'Không có';
                                    }
                                    return { ...booking, orderStatus: newStatus, orderCode: newOrderCode };
                                }); // Return the merged data
                            }),
                            catchError(err => {
                                console.error('Error fetching reservation statuses:', err);
                                this.toastrService.show(
                                    'Không thể tải trạng thái reservation.',
                                    'Lỗi phụ', // Indicate it's a secondary error
                                    { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'warning', duration: 5000 },
                                );
                                // Return initial bookings but mark status as error
                                const errorBookings = initialBookings.map(b => ({
                                    ...b,
                                    reservationStatus: b.reservationId ? 'Lỗi tải status' : 'Không có',
                                }));
                                return of(errorBookings); // Still proceed with the initial data
                            }),
                        );
                }),
                // 3. Finalize: Ensure loading indicator is decremented
                finalize(() => {
                    this.loading -= 1;
                }),
            )
            .subscribe({
                next: (finalTicketsBooking: TicketBookingDataSource[]) => {
                    this.source.load(finalTicketsBooking);
                },
                error: error => {
                    // Error handled in finalize for loading indicator
                    this.toastrService.show(
                        error.error?.message || error.message || 'Unknown error',
                        'Load tickets thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                    this.source.load([]); // Clear table on primary error
                },
            });
    }

    private filterShow(input: string): ShowModel[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.showList.filter(
            optionValue =>
                !input || input === '' || removeAccents(optionValue.showName.toLowerCase()).indexOf(filterValue) > -1,
        );
    }

    private filterSession(input: string): ShowSessionModel[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.sessionList.filter(
            optionValue =>
                !input ||
                input === '' ||
                removeAccents(optionValue.sessionName?.toLowerCase() || '').indexOf(filterValue) > -1 ||
                removeAccents(optionValue.code?.toLowerCase() || '').indexOf(filterValue) > -1,
        );
    }

    onFileSelected(event: Event): void {
        this.clearFeedback();
        const element = event.currentTarget as HTMLInputElement;
        const fileList: FileList | null = element.files;

        if (fileList && fileList.length > 0) {
            const file = fileList[0];
            const fileExtension = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();

            // Basic extension check (client-side validation)
            if (this.allowedExtensions.includes(fileExtension)) {
                this.selectedFile = file;
                // Update form control value for validation state, though we use selectedFile property
                this.uploadForm.patchValue({ fileInput: file });
                this.uploadForm.get('fileInput')?.updateValueAndValidity();
                this.feedbackMessage = `Selected file: ${file.name}`;
            } else {
                this.selectedFile = null;
                this.uploadForm.patchValue({ fileInput: null });
                this.uploadForm.get('fileInput')?.updateValueAndValidity();
                this.uploadStatus = 'error';
                this.feedbackMessage = `Invalid file type. Please select a CSV or Excel file (${this.allowedExtensions.join(', ')}).`;
                element.value = ''; // Clear the input visually
            }
        } else {
            this.selectedFile = null;
            this.uploadForm.patchValue({ fileInput: null });
            this.uploadForm.get('fileInput')?.updateValueAndValidity();
        }
    }

    onSubmit(): void {
        this.clearFeedback();

        // Keep existing checks for selectedFile and form validity
        if (!this.selectedFile) {
            this.uploadStatus = 'error';
            this.feedbackMessage = 'Vui lòng chọn một tệp để tải lên.';
            // Ensure fileInput control reflects this if it's required
            this.uploadForm.get('fileInput')?.markAsTouched();
            return;
        }

        if (this.uploadForm.invalid) {
            this.uploadStatus = 'error';
            // More specific message might be needed depending on which field is invalid
            this.feedbackMessage = 'Vui lòng điền đầy đủ thông tin Sự kiện, Session và chọn tệp.';
            this.uploadForm.markAllAsTouched(); // Mark all fields to show errors
            return;
        }

        this.isLoading = true;

        const customerUserId = this.uploadForm.get('customerUserId')?.value?.trim();
        this.adminService
            .bookingTicketFromFile(this.selectedFile, this.selectedShow!.id, this.selectedSession!.id, customerUserId)
            .subscribe({
                next: (response: HttpResponse<BookingUploadSuccessResponse | BookingUploadErrorResponse>) => {
                    this.isLoading = false;
                    const body = response.body as BookingUploadSuccessResponse; // Assume success structure first

                    if (response.status === 200) {
                        // OK
                        this.uploadStatus = 'success';
                        this.feedbackMessage = body.message || 'Tải lên thành công.';
                        this.successDetails = body.bookingIds || null;
                        // Optionally clear the form/file input on success
                        // this.resetFormAndInput();
                    } else if (response.status === 207) {
                        // Multi-Status
                        this.uploadStatus = 'partial_success';
                        this.feedbackMessage = body.message || 'Tải lên hoàn tất với một số lỗi.';
                        this.successDetails = body.successfulBookingIds || null;
                        this.failedBookingDetails = body.failedBookings || null;
                        // Optionally clear the form/file input on partial success
                        // this.resetFormAndInput();
                    } else {
                        this.uploadStatus = 'error';
                        this.feedbackMessage = `Trạng thái thành công không mong đợi: ${response.status}. Phản hồi: ${JSON.stringify(response.body)}`;
                    }

                    this.loadTicketBookingData();
                },
                error: (error: HttpErrorResponse) => {
                    console.error('Upload failed raw error:', error); // Log the full error for debugging
                    this.isLoading = false;
                    this.uploadStatus = 'error';

                    // **** START: Specific Error Handling ****
                    const errorCode = error.error?.code;
                    const errorMessage =
                        error.error?.message || 'Đã xảy ra lỗi không xác định trong quá trình tải lên.';

                    if (error.error instanceof ProgressEvent) {
                        // <-- Check for your specific error code
                        this.feedbackMessage =
                            'Lỗi: Tệp đã chọn dường như đã bị thay đổi. Vui lòng chọn lại tệp và thử lại.';
                        // Clear the file input to force re-selection
                        this.clearFileInput();
                    } else {
                        // Handle other errors generically
                        this.feedbackMessage = errorMessage;
                        // Optionally provide more details if available
                        if (error.error?.details) {
                            // Check if details is an array or object and display appropriately
                            if (Array.isArray(error.error.details)) {
                                this.errorDetails = error.error.details;
                            } else if (typeof error.error.details === 'object') {
                                // Convert object details to a readable format if needed
                                this.errorDetails = Object.entries(error.error.details).map(
                                    ([key, value]) => `${key}: ${value}`,
                                );
                            } else {
                                this.errorDetails = [String(error.error.details)];
                            }
                            console.error('Upload error details:', error.error.details);
                        }
                    }
                    // **** END: Specific Error Handling ****

                    // Reload data even on error, as some bookings might have been created/processed partially
                    this.loadTicketBookingData();
                },
                complete: () => {
                    this.isLoading = false; // Ensure loading is false on completion
                },
            });
    }

    // Helper function to clear the file input state
    private clearFileInput(): void {
        this.selectedFile = null;
        // Reset the form control value and validation state
        this.uploadForm.patchValue({ fileInput: null });
        this.uploadForm.get('fileInput')?.setErrors(null); // Clear potential previous errors
        this.uploadForm.get('fileInput')?.updateValueAndValidity();
        // Clear the actual input element visually
        if (this.fileInputRef && this.fileInputRef.nativeElement) {
            this.fileInputRef.nativeElement.value = '';
        }
    }

    // Modify clearFeedback if needed
    public clearFeedback(): void {
        this.uploadStatus = 'idle';
        this.feedbackMessage = null;
        this.errorDetails = null;
        this.successDetails = null;
        this.failedBookingDetails = null;
        // Don't clear the file input here, only on specific errors or success
    }

    // Helper for template validation messages
    isInvalid(controlName: string): boolean {
        const control = this.uploadForm.get(controlName);
        return !!control && control.invalid && (control.dirty || control.touched);
    }
}
