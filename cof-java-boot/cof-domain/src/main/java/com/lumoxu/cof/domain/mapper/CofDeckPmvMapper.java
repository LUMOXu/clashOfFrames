package com.lumoxu.cof.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumoxu.cof.domain.entity.CofDeckPmv;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CofDeckPmvMapper extends BaseMapper<CofDeckPmv> {

    @Select("SELECT * FROM cof_deck_pmv WHERE deck_id = #{deckId} ORDER BY match_id")
    List<CofDeckPmv> listByDeckId(Long deckId);

    @Select("SELECT * FROM cof_deck_pmv WHERE deck_id = #{deckId} AND match_id = #{matchId} LIMIT 1")
    CofDeckPmv findByDeckIdAndMatchId(@Param("deckId") Long deckId, @Param("matchId") Integer matchId);
}
