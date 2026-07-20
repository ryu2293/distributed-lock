package com.solo.lock.domain.enrollment.service;

import com.solo.lock.domain.enrollment.entity.Enrollment;
import com.solo.lock.domain.enrollment.repository.EnrollmentRepository;
import com.solo.lock.domain.lecture.entity.Lecture;
import com.solo.lock.domain.lecture.repository.LectureRepository;
import com.solo.lock.domain.student.entity.Student;
import com.solo.lock.domain.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final LectureRepository lectureRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public void enroll(Long studentId, Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new IllegalArgumentException());
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException());

        lecture.increase();

        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .lecture(lecture)
                .build();

        enrollmentRepository.save(enrollment);
    }
}
