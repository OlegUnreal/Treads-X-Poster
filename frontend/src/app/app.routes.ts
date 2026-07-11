import { Routes } from '@angular/router';
import { DashboardPageComponent } from './pages/dashboard-page.component';
import { AccountsPageComponent } from './pages/accounts-page.component';
import { PublishPageComponent } from './pages/publish-page.component';
import { CreatePageComponent } from './pages/create-page.component';
import { QueuePageComponent } from './pages/queue-page.component';
import { AutomationPageComponent } from './pages/automation-page.component';
import { SettingsPageComponent } from './pages/settings-page.component';
import { PlaybackPageComponent } from './pages/playback-page.component';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'accounts'
  },
  {
    path: 'accounts',
    component: AccountsPageComponent
  },
  {
    path: 'overview',
    component: DashboardPageComponent
  },
  {
    path: 'publish',
    component: PublishPageComponent
  },
  {
    path: 'create',
    component: CreatePageComponent
  },
  {
    path: 'queue',
    component: QueuePageComponent
  },
  {
    path: 'automation',
    component: AutomationPageComponent
  },
  {
    path: 'playback',
    component: PlaybackPageComponent
  },
  {
    path: 'settings',
    component: SettingsPageComponent
  }
];
