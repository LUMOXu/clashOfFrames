package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cof_match_history")
public class CofMatchHistory {

    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    public String gameId;
    public String roomId;
    public Long playedAt;
    public String summary;
}
