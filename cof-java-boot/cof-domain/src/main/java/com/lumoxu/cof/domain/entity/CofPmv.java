package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("cof_pmv")
public class CofPmv {

    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    public String name;
    public String author;
    public String description;
    public String link;
    public String reviewStatus;
    public String pendingReviewStatus;
    public String pendingName;
    public String pendingAuthor;
    public String pendingDescription;
    public String pendingLink;
    public String submitterClientId;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant deletedAt;
}
