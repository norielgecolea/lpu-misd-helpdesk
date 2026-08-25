export interface UserProfile {
  id: number;
  email: string;
  name: string;
  role: string;
  needsStudentInfo: boolean;
  declaredStudentName: string | null;
  declaredStudentNo: string | null;
}

export interface StudentInfoRequest {
  studentName: string;
  studentNo: string;
}
