package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumoxu.cof.domain.handler.JsonbStringTypeHandler;

@TableName("cof_match_history")
public class CofMatchHistory {

    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    public String gameId;
    public String roomId;
    public Long playedAt;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    public String summary;
    /** Full match log as plain text (relative timestamps from match start). */
    public String logText;
    /** JSON {@link com.lumoxu.cof.engine.GameReplayTimeline} for visual replay. */
    public String replayJson;
}
