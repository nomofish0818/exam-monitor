package com.exam.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.monitor.entity.MonitorRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonitorRecordMapper extends BaseMapper<MonitorRecord> {
}