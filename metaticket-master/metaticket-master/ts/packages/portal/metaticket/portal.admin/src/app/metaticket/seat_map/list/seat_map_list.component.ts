import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { IColumnType, LocalDataSource, Settings } from 'angular2-smart-table';
import { MetaticketSeatplannerService } from '../../service/metaticket.seatplanner.service';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { ReplaySubject } from "rxjs";
import { DialogConfirmComponent } from "../../dialog/dialog.confirm/dialog.confirm.component";

@Component({
    selector: 'app-seat-map-list',
    templateUrl: './seat_map_list.component.html',
    styleUrls: ['./seat_map_list.component.scss'],
})
export class SeatMapListComponent implements OnInit {
    settings: Settings = {
        actions: {
            add: false,
            edit: false,
            delete: false,
            custom: [
                { name: 'draw', customButtonContent: '<i class="eva eva-edit-2-outline" title="Vẽ"></i>' },
                // Delete button logic would ideally be here or as a custom action if smart table supports it well,
                // but for now let's use a custom action for 'Delete' as well if needed,
                // or just rely on the requirement "mỗi hàng có nút xoa/vẽ".
                // I'll put buttons in the template via a custom render component if needed,
                // but for simplicity with smart table, I'll use custom actions.
                { name: 'delete', customButtonContent: '<i class="eva eva-trash-2-outline" title="Xóa"></i>' },
            ],
            position: 'right',
        },
        columns: {
            id: {
                title: 'ID',
                type: IColumnType.Text,
                isEditable: false,
            },
            displayName: {
                title: 'Tên',
                type: IColumnType.Text,
            },
            category: {
                title: 'Categories',
                type: IColumnType.Text,
                valuePrepareFunction: (cell: any, row: any) => {
                    if (row.catNames && Array.isArray(row.catNames)) {
                        return row.catNames.join(', ');
                    }
                    return '';
                },
            },
        },
    };

    source: LocalDataSource = new LocalDataSource();

    constructor(
        private toastrService: NbToastrService,
        private seatplannerService: MetaticketSeatplannerService,
        private router: Router,
        private dialogService: NbDialogService,
    ) {}

    ngOnInit(): void {
        this.loadData();
    }

    loadData() {
        this.seatplannerService.getAllSeatplanCategory().subscribe(data => {
            this.source.load(data.reverse());
        });
    }

    onCustomAction(event: any) {
        if (event.action === 'draw') {
            window.location.href = '/portal/seat-map/draw/' +  event.data.id;
        } else if (event.action === 'delete') {
            const loading$ = new ReplaySubject<boolean>();
            const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();

            this.dialogService.open(DialogConfirmComponent, {
                context: {
                    title: 'Xác nhận xóa',
                    content: 'Bạn có chắc chắn muốn xóa seat map này không?',
                    loading$: loading$,
                    onAction$: onAction$,
                },
            });

            onAction$.subscribe(action => {
                if (action === 'confirm') {
                    loading$.next(true);
                    this.seatplannerService.deleteSeatPlan(event.data.id).subscribe(
                        () => {
                            loading$.next(false);
                            this.toastrService.show('Xóa seat map thành công', 'Thành công', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'success',
                            });
                            this.loadData();
                        },
                        error => {
                            loading$.next(false);
                            this.toastrService.show('Có lỗi xảy ra khi xóa seat map: ' + error.message, 'Lỗi', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'danger',
                            });
                        },
                    );
                }
            });
        }
    }

    createNew() {
        this.seatplannerService.createSeatPlan().subscribe(
            () => {
                this.toastrService.show('Tạo seat map mới thành công', 'Thành công', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'success',
                });
                this.loadData();
            },
            error => {
                this.toastrService.show('Có lỗi xảy ra khi tạo seat map mới: ' + error.message, 'Lỗi', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'danger',
                });
            },
        );
    }
}
