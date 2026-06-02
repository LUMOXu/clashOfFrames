package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cof_deck_pmv")
public class CofDeckPmv {

    @TableId(value = "pmv_id", type = IdType.AUTO)
    public Long pmvId;

    public Long deckId;

    /** User-facing PMV number used for cross-deck bell matching. */
    @TableField("match_id")
    public Integer matchId;

    public String name;
    public String author;
    public String description;
    public String link;
    public String submitterClientId;
    public String reviewStatus;
}
