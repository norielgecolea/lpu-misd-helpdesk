package org.lpu.dev.codes.helpdesk.dto;

import java.time.LocalDate;
import org.lpu.dev.codes.helpdesk.model.Student;

public record StudentResponse(
        Long id,
        String name,
        String studentNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String department,
        String course,
        String school,
        boolean financeTagged
) {
    public static StudentResponse from(Student student, String photoUrl) {
        return new StudentResponse(
                student.getId(),
                student.getName(),
                student.getStudentNo(),
                photoUrl,
                student.getRfid(),
                student.getBirthdate(),
                student.getDepartment(),
                student.getCourse(),
                student.getSchool(),
                student.isFinanceTagged()
        );
    }
}
