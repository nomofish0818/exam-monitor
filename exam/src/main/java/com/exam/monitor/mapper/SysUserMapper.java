package com.exam.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.monitor.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    // 這裡可以根據需要定義自定義查詢，例如根據用戶名查詢
}