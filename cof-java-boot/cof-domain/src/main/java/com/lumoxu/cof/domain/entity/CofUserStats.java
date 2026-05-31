package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumoxu.cof.domain.handler.JsonbStringTypeHandler;

@TableName("cof_user_stats")
public class CofUserStats {

    @TableId("stats_id")
    public String statsId;
    public String username;
    public Integer gamesPlayed;
    public Integer wins;
    public Integer rings;
    public Integer correctRings;
    public Integer wrongRings;
    public Integer wonCards;
    public Integer totalRank;
    public Boolean isComputer;
    public String computerId;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    public String defeatedComputers;
    public String godRewardGameId;
    public Long godDefeatedAt;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    public String history;
    public Long updatedAt;
}
