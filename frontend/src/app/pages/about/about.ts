import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { APP_NAME, APP_VERSION, RELEASES } from '../../core/app-info';

@Component({
  selector: 'app-about',
  imports: [RouterLink],
  templateUrl: './about.html',
})
export class About {
  protected readonly appName = APP_NAME;
  protected readonly version = APP_VERSION;
  protected readonly releases = RELEASES;
}
