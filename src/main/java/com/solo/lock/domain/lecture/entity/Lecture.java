package com.solo.lock.domain.lecture.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String title;
    @Column
    private Integer capacity;
    @Column
    private Integer enrolledCount;

    @Version
    private Long version;

    // 자원이 마지막으로 받아들인 fencing token (기본 0). primitive long → NOT NULL, 기본값 0
    private long fenceToken;

    @Builder
    public Lecture(String title, Integer capacity, Integer enrolledCount) {
        this.title = title;
        this.capacity = capacity;
        this.enrolledCount = enrolledCount;
    }

    public void increase() {
        if(this.capacity <= this.enrolledCount) throw new RuntimeException("정원 초과 예외");
        this.enrolledCount++;
    }
}
