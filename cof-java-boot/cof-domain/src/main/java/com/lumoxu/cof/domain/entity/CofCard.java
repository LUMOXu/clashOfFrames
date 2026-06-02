package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cof_card")
public class CofCard {

    @TableId(value = "card_id", type = IdType.AUTO)
    public Long cardId;

    public Long deckId;

    /** FK to {@link CofDeckPmv#pmvId}. */
    public Long pmvId;

    public String shot;
    public String fileName;
    public String imageUrl;
    public String cardUid;
    public String submitterClientId;
    public String reviewStatus;
}
