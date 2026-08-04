package com.solo.lock.domain.enrollment.controller;

import com.solo.lock.domain.enrollment.dto.response.PopularLectureRow;
import com.solo.lock.domain.enrollment.service.PopularLectureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/lectures/popular")
public class PopularController {

    private final PopularLectureService popularLectureService;

    @GetMapping
    public List<PopularLectureRow> popular() {
        return popularLectureService.getPopular();
    }

    @GetMapping("/recompute-count")
    public int recomputeCount() {
        return popularLectureService.getRecomputeCount();
    }

    @DeleteMapping("/cache")
    public void resetCache() {
        popularLectureService.resetCache();
    }
}
