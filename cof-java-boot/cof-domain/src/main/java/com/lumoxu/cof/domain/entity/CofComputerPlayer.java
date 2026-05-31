package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cof_computer_player")
public class CofComputerPlayer {

    @TableId("computer_id")
    public String computerId;
    public String name;
    public String description;
    @TableField("play_delay_mean_seconds")
    public Double playDelayMeanSeconds;
    @TableField("play_delay_std_seconds")
    public Double playDelayStdSeconds;
    @TableField("reaction_mean_seconds")
    public Double reactionMeanSeconds;
    @TableField("reaction_std_seconds")
    public Double reactionStdSeconds;
    @TableField("match_detection_probability")
    public Double matchDetectionProbability;
    @TableField("false_ring_probability")
    public Double falseRingProbability;
    @TableField("updated_at")
    public Long updatedAt;
}
