export interface KioskPerson {
  personType: 'STUDENT' | 'EMPLOYEE' | string;
  id: number;
  name: string;
  personNo: string;
  email: string | null;
  photo: string | null;
  department: string | null;
  course: string | null;
  school: string | null;
  position: string | null;
  rfid: string | null;
}

export interface KioskLookupRequest {
  identifier: string;
}

export interface KioskTicketRequest {
  identifier: string;
  category: string;
  concern?: string;
}

export interface ServerTime {
  epochMillis: number;
  iso: string;
  timezone: string;
}
