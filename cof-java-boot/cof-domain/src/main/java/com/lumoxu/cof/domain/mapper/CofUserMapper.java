package com.lumoxu.cof.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumoxu.cof.domain.entity.CofUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface CofUserMapper extends BaseMapper<CofUser> {

    @Select("SELECT * FROM cof_user WHERE LOWER(username) = LOWER(#{username}) LIMIT 1")
    CofUser findByUsernameIgnoreCase(String username);

    default Optional<CofUser> findByUsername(String username) {
        return Optional.ofNullable(findByUsernameIgnoreCase(username));
    }
}
