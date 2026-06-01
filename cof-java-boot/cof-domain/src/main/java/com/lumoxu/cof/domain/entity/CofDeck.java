package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cof_deck")
public class CofDeck {

    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    public String folderName;
    public String name;
    public String curator;
    public String description;
    public String version;
    public String link;
    public String backUrl;
    public Integer cardCount;
    public Integer pmvCount;
    public Boolean enabled;
    public Long updatedAt;
    public String submitterClientId;
    public String reviewStatus;
}
