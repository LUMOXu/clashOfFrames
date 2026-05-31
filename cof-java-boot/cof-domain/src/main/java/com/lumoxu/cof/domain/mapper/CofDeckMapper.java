package com.lumoxu.cof.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumoxu.cof.domain.entity.CofDeck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CofDeckMapper extends BaseMapper<CofDeck> {

    @Select("SELECT * FROM cof_deck WHERE enabled = TRUE ORDER BY id")
    List<CofDeck> listEnabledDecks();

    @Select("SELECT * FROM cof_deck WHERE folder_name = #{folderName} LIMIT 1")
    CofDeck findByFolderName(String folderName);
}
