import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, ViewChild } from '@angular/core';
import { socialContacts } from '../../constants/social-contacts';

type ContactId = 'facebook' | 'zalo' | 'hotline' | 'email';

type ContactItem = {
  id: ContactId;
  label: string;
  sublabel: string;
  bg: string;
  hoverBg: string;
};

@Component({
  selector: 'app-contact-widget',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './contact-widget.component.html',
  styleUrls: ['./contact-widget.component.scss'],
})
export class ContactWidgetComponent {
  @ViewChild('panelRef') panelRef?: ElementRef<HTMLDivElement>;
  @ViewChild('buttonRef') buttonRef?: ElementRef<HTMLButtonElement>;

  open = false;
  hoveredId: ContactId | null = null;
  pulse = true;

  readonly contacts: ContactItem[] = [
    {
      id: 'facebook',
      label: 'Facebook',
      sublabel: 'Nhắn tin ngay',
      bg: '#1877F2',
      hoverBg: '#0d6ae0',
    },
    {
      id: 'zalo',
      label: 'Zalo OA',
      sublabel: 'Liên hệ qua Zalo',
      bg: '#0068FF',
      hoverBg: '#0055d4',
    },
    {
      id: 'hotline',
      label: 'Hotline',
      sublabel: '1900 3392',
      bg: '#16a34a',
      hoverBg: '#15803d',
    },
    {
      id: 'email',
      label: 'Email',
      sublabel: 'support@metaticket.com.vn',
      bg: '#7c3aed',
      hoverBg: '#6d28d9',
    },
  ];

  /** Toggle widget open/close; stop pulse after first open. */
  toggleOpen(): void {
    // Toggle state; keep behavior identical to the provided React sample.
    this.open = !this.open;
    if (this.open) this.pulse = false;
  }

  /** Handle escape + outside click; close panel for UX consistency. */
  @HostListener('document:mousedown', ['$event'])
  onDocumentMouseDown(event: MouseEvent): void {
    // Only close when panel is open.
    if (!this.open) return;

    const target = event.target as Node | null;
    if (!target) return;

    const panelEl = this.panelRef?.nativeElement;
    const buttonEl = this.buttonRef?.nativeElement;

    // Close on outside click.
    if (panelEl && panelEl.contains(target)) return;
    if (buttonEl && buttonEl.contains(target)) return;
    this.open = false;
  }

  /** Close on `Escape` key. */
  @HostListener('document:keydown', ['$event'])
  onDocumentKeyDown(event: KeyboardEvent): void {
    // Close only when open; Escape is a common accessibility shortcut.
    if (!this.open) return;
    if (event.key === 'Escape') this.open = false;
  }

  /** Close panel and perform action for a selected contact channel. */
  onContactClick(contact: ContactItem): void {
    // Ensure the panel closes immediately after user selection.
    this.open = false;

    switch (contact.id) {
      case 'facebook': {
        if (typeof window !== 'undefined') {
          window.open(socialContacts.facebook, '_blank');
        }
        return;
      }

      case 'zalo': {
        if (typeof window !== 'undefined') {
          window.open(socialContacts.zalo, '_blank');
        }
        return;
      }

      case 'hotline': {
        if (typeof window !== 'undefined') {
          window.open(`tel:${socialContacts.hotline}`, '_self');
        }
        return;
      }

      case 'email': {
        if (typeof window !== 'undefined') {
          window.location.href = `mailto:${socialContacts.email}`;
        }
        return;
      }
    }
  }
}
