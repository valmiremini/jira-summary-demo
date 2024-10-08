import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { JiraService, Project } from '../../services/jira.service';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'project-selector',
  templateUrl: './project-selector.component.html',
  styleUrls: ['./project-selector.component.css'],
})
export class ProjectSelectorComponent implements OnInit {
  projects: Project[] = [];
  selectedProjectKey: string = '';
  isLoading: boolean = false;

  @Output() releaseNotesGenerated = new EventEmitter<string>();

  constructor(private jiraService: JiraService, private snackBar: MatSnackBar) {}

  ngOnInit(): void {
    this.jiraService.getProjects().subscribe(
      (data) => {
        this.projects = data;
      },
      (error) => {
        console.error('Fehler beim Abrufen der Projekte', error);
      }
    );
  }

  onProjectSelectionChange(): void {
    // Hier können Sie zusätzliche Aktionen durchführen, wenn ein Projekt ausgewählt wird.
    // Momentan machen wir hier nichts Spezielles.
  }

  onFetchReleaseNotes(): void {
    if (this.selectedProjectKey) {
      this.isLoading = true;
      this.jiraService.getReleaseNotes(this.selectedProjectKey).subscribe(
        (data) => {
          this.releaseNotesGenerated.emit(data);
          this.isLoading = false;
        },
        (error) => {
          console.error('Fehler beim Abrufen der Release Notes', error);
          this.isLoading = false;
          this.snackBar.open('Fehler beim Abrufen der Release Notes', 'Schließen', {
            duration: 3000,
          });
        }
      );
    } else {
      this.snackBar.open('Bitte wählen Sie ein Projekt aus.', 'Schließen', {
        duration: 3000,
      });
    }
  }
}
