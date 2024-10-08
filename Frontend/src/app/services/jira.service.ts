import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Project {
  key: string;
  name: string;
}

@Injectable({
  providedIn: 'root'
})
export class JiraService {

  private apiUrl = 'http://localhost:8080/api/issues';

  constructor(private http: HttpClient) { }

  getProjects(): Observable<Project[]> {
    return this.http.get<Project[]>(`${this.apiUrl}/projects`);
  }

  getReleaseNotes(projectKey: string): Observable<string> {
    return this.http.get(`${this.apiUrl}/${projectKey}/summarize-comments`, { responseType: 'text' });
  }
}
