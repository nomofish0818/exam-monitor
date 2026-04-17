package com.exam.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("monitor_record")
public class MonitorRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long studentId;

    private String abnormalType;

    // 儲存圖片的相對 URL 路徑，例如: /uploads/abc123xx.jpg
    private String screenshotUrl;

    private LocalDateTime triggerTime;
}