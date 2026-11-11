import { Component } from '@angular/core';
import { CKEditorModule } from '@ckeditor/ckeditor5-angular';
import ClassicEditor from '@ckeditor/ckeditor5-build-classic';
import {
  ButtonComponent,
  FormInputComponent,
  ImageComponent,
  LayoutComponent,
  NavbarComponent,
} from 'src/app/lib/components';
import { appIcons } from 'src/app/lib/theme';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [
    NavbarComponent,
    LayoutComponent,
    FormInputComponent,
    CKEditorModule,
    ImageComponent,
    NzFormModule,
    NzTypographyComponent,
    ButtonComponent,
  ],
  templateUrl: './contact.component.html',
})
export class ContactComponent {
  title = 'angular';
  public editor = ClassicEditor;
  protected readonly appIcons = appIcons;
  formGroup: FormGroup<{
    email: FormControl<string | null>;
    phone: FormControl<string | null>;
    type: FormControl<string | null>;
    title: FormControl<string | null>;
  }>;

  constructor(private _fb: FormBuilder) {
    this.formGroup = this._fb.group({
      email: [''],
      phone: [''],
      type: [''],
      title: [''],
    });
  }
}
