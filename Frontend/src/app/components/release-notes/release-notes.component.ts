import { Component, Input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Component({
  selector: 'release-notes',
  templateUrl: './release-notes.component.html',
  styleUrls: ['./release-notes.component.css'],
})
export class ReleaseNotesComponent {
  @Input() notes: string = '';

  constructor(private sanitizer: DomSanitizer) {}

  get formattedNotes(): SafeHtml {
    // Beispielhafte einfache Formatierung
    let formatted = this.notes
      .replace(/\n/g, '<br/>') // Zeilenumbrüche erhalten
      .replace(/(?:\r\n|\r|\n)/g, '<br/>'); // Weitere Zeilenumbrüche

    // Überschriften erkennen (einfaches Beispiel)
    formatted = formatted.replace(/(Feature:)/g, '<h3>$1</h3>');
    formatted = formatted.replace(/(Bugfix:)/g, '<h3>$1</h3>');

    return this.sanitizer.bypassSecurityTrustHtml(formatted);
  }
}
