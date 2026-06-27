import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <main class="app-shell">
      <header class="topbar">
        <div class="brand">
          <span class="eyebrow">Behind The Smile</span>
          <strong>Admin</strong>
        </div>
        <nav class="nav">
          <a routerLink="/overview" routerLinkActive="active">Overview</a>
          <a routerLink="/publish" routerLinkActive="active">Publish</a>
          <a routerLink="/create" routerLinkActive="active">Create</a>
          <a routerLink="/queue" routerLinkActive="active">Queue</a>
          <a routerLink="/automation" routerLinkActive="active">Automation</a>
        </nav>
      </header>

      <section class="content">
        <router-outlet />
      </section>
    </main>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      background:
        radial-gradient(circle at 10% 10%, rgba(255, 208, 118, 0.18), transparent 22%),
        radial-gradient(circle at 90% 20%, rgba(87, 178, 255, 0.16), transparent 20%),
        linear-gradient(180deg, #f6f0e7 0%, #ebe7dd 100%);
      color: #1f2933;
      font-family: Georgia, "Times New Roman", serif;
    }
    .app-shell {
      max-width: 1280px;
      margin: 0 auto;
      padding: 28px 24px 56px;
    }
    .topbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 20px;
      margin-bottom: 28px;
      padding: 18px 20px;
      background: rgba(255,255,255,0.72);
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 24px;
      box-shadow: 0 24px 50px rgba(69,58,42,0.12);
    }
    .brand { display: grid; gap: 4px; }
    .eyebrow {
      text-transform: uppercase;
      letter-spacing: 0.14em;
      font: 700 11px/1.2 "Segoe UI", sans-serif;
      color: #8a5a24;
    }
    .brand strong { font-size: 24px; }
    .nav {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }
    .nav a {
      text-decoration: none;
      color: #31465a;
      padding: 10px 14px;
      border-radius: 999px;
      background: rgba(255,255,255,0.82);
      border: 1px solid rgba(31,41,51,0.08);
      font: 700 14px/1.2 "Segoe UI", sans-serif;
    }
    .nav a.active {
      background: linear-gradient(135deg, #1f6feb, #0f766e);
      color: white;
      box-shadow: 0 16px 30px rgba(21, 48, 74, 0.2);
    }
    .content { display: block; }
    @media (max-width: 900px) {
      .topbar { flex-direction: column; align-items: stretch; }
      .nav { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
    @media (max-width: 640px) {
      .nav { grid-template-columns: 1fr; }
      .app-shell { padding: 18px 16px 40px; }
    }
  `]
})
export class AppComponent {}
