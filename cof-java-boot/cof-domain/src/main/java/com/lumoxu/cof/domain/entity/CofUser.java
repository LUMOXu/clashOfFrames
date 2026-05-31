package com.lumoxu.cof.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.UUID;

@TableName("cof_user")
public class CofUser {

    @TableId("client_id")
    public UUID clientId;
    public String username;
    public String passwordHash;
    public String passwordSalt;
    public Integer passwordIterations;
    public String passwordDigest;
    public Long createdAt;
    public Long lastLoginAt;
}
