package com.exam.monitor.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    /**
     * 主鍵ID (雪花算法)
     */
    @TableId
    private Long id;

    /**
     * 登錄賬號 (學號/工號)
     */
    private String username;

    /**
     * 密碼 (BCrypt加密)
     */
    private String password;

    /**
     * 真實姓名
     */
    private String realName;

    /**
     * 角色: 1-教師, 2-學生
     */
    private Integer role;

    /**
     * 頭像路徑
     */
    private String avatar;

    /**
     * 賬號狀態: 1-正常, 0-禁用
     */
    private Integer status;

    /**
     * 邏輯刪除: 0-存在, 1-刪除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 創建時間
     */
    private LocalDateTime createTime;

    /**
     * 更新時間
     */
    private LocalDateTime updateTime;
}