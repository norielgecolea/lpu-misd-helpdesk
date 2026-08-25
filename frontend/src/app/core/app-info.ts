export const APP_NAME = 'MISD Helpdesk';

export interface ReleaseNote {
  title: string;
  detail: string;
}

export interface Release {
  version: string;
  notes: ReleaseNote[];
}

/**
 * Newest first. Add a new `{ version, notes }` object at the top;
 * leave older releases in place so their patch notes stay on the page.
 */
export const RELEASES: Release[] = [
  {
    version: '1.1.0',
    notes: [
      {
        title: 'About this system',
        detail: 'Login shows the version and opens patch notes for every release.',
      },
      {
        title: 'Clearer CSM ratings',
        detail: 'Feedback options are now Not Satisfied, Satisfied, and Very Satisfied.',
      },
    ],
  },
  {
    version: '1.0.0',
    notes: [
      {
        title: 'Online tickets',
        detail: 'Sign in with Microsoft or an email code and submit a request from any browser.',
      },
      {
        title: 'RFID kiosk',
        detail: 'Tap an LPU ID at the office to join the queue.',
      },
      {
        title: 'Queue board',
        detail: 'See who is waiting and who is being served.',
      },
      {
        title: 'Live monitor',
        detail: 'Full-screen display for the office waiting area.',
      },
      {
        title: 'Ticket chat',
        detail: 'Message MISD on an open ticket.',
      },
      {
        title: 'Hold a ticket',
        detail: 'Pause work when waiting on the requester.',
      },
      {
        title: 'Online vs onsite',
        detail: 'Staff work online requests and walk-ins in separate lists.',
      },
      {
        title: 'Customer satisfaction',
        detail: 'Rate the visit after a ticket is resolved.',
      },
      {
        title: 'Admin dashboard',
        detail: 'Volume, queue, and CSM at a glance.',
      },
      {
        title: 'Monthly analytics',
        detail: 'Recap a month and export a report.',
      },
      {
        title: 'CSM by admin',
        detail: 'See satisfaction ratings for each assigned staff member.',
      },
      {
        title: 'Campus directory',
        detail: 'Look up students and employees from attendance records.',
      },
      {
        title: 'Kiosk choices',
        detail: 'Choose which request types appear on the walk-in kiosk.',
      },
      {
        title: 'Staff accounts',
        detail: 'Create MISD admin and monitoring logins.',
      },
      {
        title: 'Campus emails',
        detail: 'LPU Laguna and LPUSC accounts can sign in.',
      },
    ],
  },
];

export const APP_VERSION = RELEASES[0]?.version ?? '1.0.0';
