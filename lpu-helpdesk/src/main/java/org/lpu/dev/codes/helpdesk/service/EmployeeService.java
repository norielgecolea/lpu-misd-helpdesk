package org.lpu.dev.codes.helpdesk.service;

import java.util.List;
import org.lpu.dev.codes.helpdesk.config.GateAttendanceProperties;
import org.lpu.dev.codes.helpdesk.dto.EmployeePageResponse;
import org.lpu.dev.codes.helpdesk.dto.EmployeeResponse;
import org.lpu.dev.codes.helpdesk.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final GateAttendanceProperties gateAttendanceProperties;

    public EmployeeService(EmployeeRepository employeeRepository, GateAttendanceProperties gateAttendanceProperties) {
        this.employeeRepository = employeeRepository;
        this.gateAttendanceProperties = gateAttendanceProperties;
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public EmployeePageResponse page(String search, int offset, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        int from = Math.max(offset, 0);
        List<EmployeeResponse> items = employeeRepository.searchActive(search, from, size).stream()
                .map(e -> EmployeeResponse.from(e, gateAttendanceProperties.resolvePhotoUrl(e.getPhoto())))
                .toList();
        return new EmployeePageResponse(items, employeeRepository.countActive(search));
    }
}
