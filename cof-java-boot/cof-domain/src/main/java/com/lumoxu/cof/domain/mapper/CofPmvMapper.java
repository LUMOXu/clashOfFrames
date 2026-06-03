package com.lumoxu.cof.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumoxu.cof.domain.entity.CofPmv;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CofPmvMapper extends BaseMapper<CofPmv> {

    @Select("""
            SELECT * FROM cof_pmv
            WHERE deleted_at IS NULL
            ORDER BY name
            """)
    List<CofPmv> listAlive();

    @Select("""
            SELECT * FROM cof_pmv
            WHERE deleted_at IS NULL
              AND review_status = 'approved'
            ORDER BY name
            """)
    List<CofPmv> listApproved();

    @Select("""
            SELECT * FROM cof_pmv
            WHERE deleted_at IS NULL
              AND LOWER(name) = LOWER(#{name})
            LIMIT 1
            """)
    CofPmv findAliveByName(@Param("name") String name);

    @Select("""
            SELECT * FROM cof_pmv
            WHERE deleted_at IS NULL
              AND pending_name IS NOT NULL
              AND LOWER(pending_name) = LOWER(#{name})
            LIMIT 1
            """)
    CofPmv findAliveByPendingName(@Param("name") String name);
}
