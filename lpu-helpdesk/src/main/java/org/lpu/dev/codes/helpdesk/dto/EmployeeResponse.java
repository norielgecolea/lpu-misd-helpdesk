package org.lpu.dev.codes.helpdesk.dto;

import java.time.LocalDate;
import org.lpu.dev.codes.helpdesk.model.Employee;

public record EmployeeResponse(
        Long id,
        String name,
        String employeeNo,
        String photo,
        String rfid,
        LocalDate birthdate,
        String department,
        String position
) {
    public static EmployeeResponse from(Employee employee, String photoUrl) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getEmployeeNo(),
                photoUrl,
                employee.getRfid(),
                employee.getBirthdate(),
                employee.getDepartment(),
                employee.getPosition()
        );
    }
}
