package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cof_deck_pmv")
public class CofDeckPmv {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long deckId;
    public Integer pmvId;
    public String name;
    public String author;
    public String description;
    public String link;
    public String submitterClientId;
    public String reviewStatus;
}
