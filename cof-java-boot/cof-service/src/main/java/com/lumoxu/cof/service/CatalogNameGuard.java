package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofPmv;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import com.lumoxu.cof.domain.mapper.CofPmvMapper;
import org.springframework.stereotype.Component;

@Component
public class CatalogNameGuard {

    private final CofDeckMapper deckMapper;
    private final CofPmvMapper pmvMapper;

    public CatalogNameGuard(CofDeckMapper deckMapper, CofPmvMapper pmvMapper) {
        this.deckMapper = deckMapper;
        this.pmvMapper = pmvMapper;
    }

    public void ensureDeckNameAvailable(String name, Long excludeDeckId) {
        if (name == null || name.isBlank()) {
            return;
        }
        CofDeck byName = deckMapper.findAliveByName(name.trim());
        if (byName != null && (excludeDeckId == null || !excludeDeckId.equals(byName.id))) {
            throw new CofException(ErrorCode.CONFLICT, "牌组名称已存在，请更换。");
        }
        CofDeck byPending = deckMapper.findAliveByPendingName(name.trim());
        if (byPending != null && (excludeDeckId == null || !excludeDeckId.equals(byPending.id))) {
            throw new CofException(ErrorCode.CONFLICT, "牌组名称已被其他待审修改占用。");
        }
    }

    public void ensurePmvNameAvailable(String name, Long excludePmvId) {
        if (name == null || name.isBlank()) {
            return;
        }
        CofPmv byName = pmvMapper.findAliveByName(name.trim());
        if (byName != null && (excludePmvId == null || !excludePmvId.equals(byName.id))) {
            throw new CofException(ErrorCode.CONFLICT, "PMV 名称已存在，请更换。");
        }
        CofPmv byPending = pmvMapper.findAliveByPendingName(name.trim());
        if (byPending != null && (excludePmvId == null || !excludePmvId.equals(byPending.id))) {
            throw new CofException(ErrorCode.CONFLICT, "PMV 名称已被其他待审修改占用。");
        }
    }
}
