package com.exam.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.exam.monitor.entity.MonitorRecord;
import org.springframework.web.multipart.MultipartFile;

public interface MonitorRecordService extends IService<MonitorRecord> {
    // 處理截圖上傳與資料儲存
    boolean saveAbnormalRecord(MultipartFile file, Long studentId, Long examId, String abnormalType);
}