package com.lumoxu.cof.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumoxu.cof.domain.entity.CofCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CofCardMapper extends BaseMapper<CofCard> {

    @Select("""
            SELECT * FROM cof_card
            WHERE deleted_at IS NULL
              AND deck_id = #{deckId}
            ORDER BY id
            """)
    List<CofCard> listAliveByDeckId(@Param("deckId") long deckId);

    @Select("""
            SELECT * FROM cof_card
            WHERE deleted_at IS NULL
              AND deck_id = #{deckId}
              AND pmv_id = #{pmvId}
            ORDER BY id
            """)
    List<CofCard> listAliveByDeckIdAndPmvId(@Param("deckId") long deckId, @Param("pmvId") long pmvId);

    @Select("""
            SELECT * FROM cof_card
            WHERE deleted_at IS NULL
              AND image_url = #{imageUrl}
            LIMIT 1
            """)
    CofCard findAliveByImageUrl(@Param("imageUrl") String imageUrl);

    @Select("""
            SELECT * FROM cof_card
            WHERE deleted_at IS NULL
              AND pending_image_url = #{imageUrl}
            LIMIT 1
            """)
    CofCard findAliveByPendingImageUrl(@Param("imageUrl") String imageUrl);

    @Select("""
            SELECT COUNT(*) FROM cof_card
            WHERE deleted_at IS NULL AND deck_id = #{deckId}
            """)
    long countAliveByDeckId(@Param("deckId") long deckId);

    @Select("""
            SELECT COUNT(DISTINCT pmv_id) FROM cof_card
            WHERE deleted_at IS NULL AND deck_id = #{deckId}
            """)
    long countDistinctPmvByDeckId(@Param("deckId") long deckId);
}
