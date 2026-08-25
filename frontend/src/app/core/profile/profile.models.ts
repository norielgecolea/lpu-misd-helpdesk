export interface UserProfile {
  id: number;
  email: string;
  name: string;
  role: string;
  needsStudentInfo: boolean;
  declaredStudentName: string | null;
  declaredStudentNo: string | null;
  declaredPersonType: string | null;
  declaredLpuEmail: string | null;
}

export type DeclaredPersonType = 'STUDENT' | 'EMPLOYEE';

export interface StudentInfoRequest {
  personType: DeclaredPersonType;
  studentName: string;
  studentNo: string;
  lpuEmail: string;
}
