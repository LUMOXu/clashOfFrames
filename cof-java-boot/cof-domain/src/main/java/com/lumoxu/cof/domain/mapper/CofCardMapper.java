package com.lumoxu.cof.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumoxu.cof.domain.entity.CofCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CofCardMapper extends BaseMapper<CofCard> {

    @Select("SELECT * FROM cof_card WHERE deck_id = #{deckId} ORDER BY pmv_id, card_id")
    List<CofCard> listByDeckId(Long deckId);
}
