import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { DialogConfirmComponent } from '../../dialog/dialog.confirm/dialog.confirm.component';
import { ReplaySubject } from 'rxjs';
import { SeatplanCategoryModel, ShowSessionModel } from '../../../@core/metaticket/model';

@Component({
    templateUrl: './category_item.component.html',
    styleUrls: ['./category_item.component.scss'],
    selector: 'category-item',
})
export class CategoryItemComponent {
    @Input() session: ShowSessionModel;
    @Input() index: number;
    @Input() formGroup: FormGroup;
    @Input() seatplanCategory: SeatplanCategoryModel;
    @Output() removeEvent: EventEmitter<any> = new EventEmitter<any>();
    @Output() createEvent: EventEmitter<any> = new EventEmitter<any>();
    @Output() updateEvent: EventEmitter<any> = new EventEmitter<any>();

    constructor(
        private dialogService: NbDialogService,
        private toastrService: NbToastrService,
    ) {}

    remove() {
        if (['Open'].includes(this.session.status)) {
            this.toastrService.show(
                `session status is ${this.session.status}`,
                'Không thể xóa category, show đã được mở bán',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        if (this.newly.value === true) {
            this.removeEvent.emit(this.index);
            return;
        }

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: 'Xác nhận xóa hạng vé',
                content: 'Bạn có chắc chắn muốn xóa hạng vé hay không?',
                onAction$: onAction$,
            },
        });
        onAction$.subscribe(act => {
            if (act === 'confirm') {
                this.removeEvent.emit(this.index);
            }
        });
    }

    get editMode() {
        return this.formGroup.get('editMode');
    }

    get newly() {
        return this.formGroup.get('newly');
    }

    get loading() {
        return this.formGroup.get('loading');
    }

    get displayNameFormControl() {
        return <FormControl>this.formGroup.get('displayName');
    }

    get categoryFormControl() {
        return <FormControl>this.formGroup.get('category');
    }

    get priceFormControl() {
        return <FormControl>this.formGroup.get('price');
    }

    get colorFormControl() {
        return <FormControl>this.formGroup.get('color');
    }

    get openDateFormControl() {
        return <FormControl>this.formGroup.get('openDate');
    }

    get closeDateFormControl() {
        return <FormControl>this.formGroup.get('closeDate');
    }

    switchEditMode() {
        this.editMode.setValue(true);
    }
}
