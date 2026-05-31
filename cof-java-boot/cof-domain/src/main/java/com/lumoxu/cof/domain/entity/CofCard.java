package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cof_card")
public class CofCard {

    @TableId(type = IdType.AUTO)
    public Long id;
    public Long deckId;
    public Integer pmvId;
    public String cardId;
    public String shot;
    public String fileName;
    public String imageUrl;
    public String cardUid;
}
