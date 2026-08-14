package org.lpu.dev.codes.helpdesk.service;

import java.util.List;
import org.lpu.dev.codes.helpdesk.config.GateAttendanceProperties;
import org.lpu.dev.codes.helpdesk.dto.StudentPageResponse;
import org.lpu.dev.codes.helpdesk.dto.StudentResponse;
import org.lpu.dev.codes.helpdesk.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final GateAttendanceProperties gateAttendanceProperties;

    public StudentService(StudentRepository studentRepository, GateAttendanceProperties gateAttendanceProperties) {
        this.studentRepository = studentRepository;
        this.gateAttendanceProperties = gateAttendanceProperties;
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public StudentPageResponse page(String search, int offset, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        int from = Math.max(offset, 0);
        List<StudentResponse> items = studentRepository.searchActive(search, from, size).stream()
                .map(s -> StudentResponse.from(s, gateAttendanceProperties.resolvePhotoUrl(s.getPhoto())))
                .toList();
        return new StudentPageResponse(items, studentRepository.countActive(search));
    }
}
