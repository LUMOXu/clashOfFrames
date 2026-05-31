package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cof_computer_player")
public class CofComputerPlayer {

    @TableId("computer_id")
    public String computerId;
    public String name;
    public String description;
    public Double playDelayMeanSeconds;
    public Double playDelayStdSeconds;
    public Double reactionMeanSeconds;
    public Double reactionStdSeconds;
    public Double matchDetectionProbability;
    public Double falseRingProbability;
    public Long updatedAt;
}
