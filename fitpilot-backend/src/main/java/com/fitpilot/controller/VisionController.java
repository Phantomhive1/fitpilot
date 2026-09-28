package com.fitpilot.controller;

import com.fitpilot.service.VisionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** 姿势纠正：上传动作照片，视觉模型分析 */
@RestController
@RequestMapping("/api/vision")
public class VisionController {

    private final VisionService visionService;

    public VisionController(VisionService visionService) {
        this.visionService = visionService;
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "movement", required = false) String movement)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请上传照片");
        }
        return visionService.analyze(file, movement);
    }
}
