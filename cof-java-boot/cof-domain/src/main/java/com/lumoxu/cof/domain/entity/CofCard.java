package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("cof_card")
public class CofCard {

    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    public Long deckId;
    public Long pmvId;
    public String name;
    public String description;
    public String imageUrl;
    public String reviewStatus;
    public String pendingReviewStatus;
    public String pendingName;
    public String pendingDescription;
    public String pendingImageUrl;
    public String submitterClientId;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant deletedAt;
}
