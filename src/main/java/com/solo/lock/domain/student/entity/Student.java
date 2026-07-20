package com.solo.lock.domain.student.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String schoolId;

    @Column
    private String name;

    @Builder
    public Student(String schoolId, String name) {
        this.schoolId = schoolId;
        this.name = name;
    }
}
