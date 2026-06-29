import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <main class="app-shell container-fluid">
      <header class="topbar navbar navbar-expand-lg">
        <div class="brand navbar-brand">
          <strong>Behind The Smile</strong>
          <span>Admin</span>
        </div>
        <nav class="nav nav-pills ms-lg-auto">
          <a class="nav-link" routerLink="/overview" routerLinkActive="active">Overview</a>
          <a class="nav-link" routerLink="/publish" routerLinkActive="active">Publish</a>
          <a class="nav-link" routerLink="/create" routerLinkActive="active">Create</a>
          <a class="nav-link" routerLink="/queue" routerLinkActive="active">Queue</a>
          <a class="nav-link" routerLink="/playback" routerLinkActive="active">Playback</a>
          <a class="nav-link" routerLink="/automation" routerLinkActive="active">Automation</a>
          <a class="nav-link" routerLink="/settings" routerLinkActive="active">Settings</a>
        </nav>
      </header>

      <section class="content container-fluid px-0">
        <router-outlet />
      </section>
    </main>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      background: #f4f6f8;
      color: #17212b;
      font-family: Inter, "Segoe UI", system-ui, sans-serif;
    }
    .app-shell {
      max-width: 1440px;
      margin: 0 auto;
      padding: 12px 16px 32px;
    }
    .topbar {
      gap: 16px;
      margin-bottom: 14px;
      padding: 10px 12px;
      background: #ffffff;
      border: 1px solid #dde3ea;
      border-radius: 12px;
      box-shadow: 0 8px 22px rgba(15, 23, 42, 0.06);
    }
    .brand { display: flex; align-items: baseline; gap: 8px; margin: 0; }
    .brand strong { font-size: 18px; letter-spacing: 0; }
    .brand span { color: #64748b; font-size: 13px; font-weight: 700; }
    .nav {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .nav a {
      color: #334155;
      padding: 7px 11px;
      border-radius: 8px;
      font: 700 13px/1.2 "Segoe UI", sans-serif;
    }
    .nav a.active {
      background: #0d6efd;
      color: white;
    }
    .content { display: block; }
    @media (max-width: 900px) {
      .topbar { align-items: stretch; }
      .nav { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
    @media (max-width: 640px) {
      .nav { grid-template-columns: 1fr; }
      .app-shell { padding: 10px; }
    }
  `]
})
export class AppComponent {}
