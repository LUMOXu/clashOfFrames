package com.lumoxu.cof.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumoxu.cof.domain.entity.CofCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CofCardMapper extends BaseMapper<CofCard> {

    @Select("SELECT * FROM cof_card WHERE deck_id = #{deckId} ORDER BY pmv_id, shot")
    List<CofCard> listByDeckId(Long deckId);

    @Select("SELECT * FROM cof_card WHERE pmv_id = #{pmvId} AND shot = #{shot} LIMIT 1")
    CofCard findByPmvIdAndShot(@Param("pmvId") Long pmvId, @Param("shot") String shot);
}
