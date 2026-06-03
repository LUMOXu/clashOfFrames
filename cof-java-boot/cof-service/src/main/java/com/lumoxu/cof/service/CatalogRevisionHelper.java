package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import com.lumoxu.cof.common.catalog.ReviewStatus;
import com.lumoxu.cof.domain.entity.CofCard;
import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.entity.CofPmv;

import java.time.Instant;

/** Effective vs pending catalog fields and review transitions. */
public final class CatalogRevisionHelper {

    public static final String PENDING_EDIT = "pending";

    private CatalogRevisionHelper() {}

    public static boolean isAlive(CofDeck deck) {
        return deck != null && deck.deletedAt == null;
    }

    public static boolean isAlive(CofPmv pmv) {
        return pmv != null && pmv.deletedAt == null;
    }

    public static boolean isAlive(CofCard card) {
        return card != null && card.deletedAt == null;
    }

    public static Instant now() {
        return Instant.now();
    }

    public static void touch(CofDeck deck) {
        deck.updatedAt = now();
    }

    public static void touch(CofPmv pmv) {
        pmv.updatedAt = now();
    }

    public static void touch(CofCard card) {
        card.updatedAt = now();
    }

    public static boolean hasPendingEdit(CofDeck deck) {
        return PENDING_EDIT.equalsIgnoreCase(deck.pendingReviewStatus);
    }

    public static boolean hasPendingEdit(CofPmv pmv) {
        return PENDING_EDIT.equalsIgnoreCase(pmv.pendingReviewStatus);
    }

    public static boolean hasPendingEdit(CofCard card) {
        return PENDING_EDIT.equalsIgnoreCase(card.pendingReviewStatus);
    }

    public static void ensureNoPendingEdit(CofDeck deck) {
        if (hasPendingEdit(deck)) {
            throw new CofException(ErrorCode.CONFLICT, "该牌组已有修改待审，请等待审核完成。");
        }
    }

    public static void ensureNoPendingEdit(CofPmv pmv) {
        if (hasPendingEdit(pmv)) {
            throw new CofException(ErrorCode.CONFLICT, "该 PMV 已有修改待审，请等待审核完成。");
        }
    }

    public static void ensureNoPendingEdit(CofCard card) {
        if (hasPendingEdit(card)) {
            throw new CofException(ErrorCode.CONFLICT, "该卡牌已有修改待审，请等待审核完成。");
        }
    }

    public static boolean isApprovedLive(CofDeck deck) {
        return ReviewStatus.isApproved(deck.reviewStatus);
    }

    public static boolean isApprovedLive(CofPmv pmv) {
        return ReviewStatus.isApproved(pmv.reviewStatus);
    }

    public static boolean isApprovedLive(CofCard card) {
        return ReviewStatus.isApproved(card.reviewStatus);
    }

    public static boolean isPlayableDeck(CofDeck deck) {
        return isAlive(deck)
                && Boolean.TRUE.equals(deck.enabled)
                && isApprovedLive(deck);
    }

    public static boolean isPlayableCard(CofDeck deck, CofPmv pmv, CofCard card) {
        return isPlayableDeck(deck) && isAlive(pmv) && isApprovedLive(pmv) && isAlive(card) && isApprovedLive(card);
    }

    public static void approveDeckInitial(CofDeck deck) {
        deck.reviewStatus = ReviewStatus.APPROVED;
        touch(deck);
    }

    public static void approveDeckRevision(CofDeck deck) {
        if (deck.pendingName != null) {
            deck.name = deck.pendingName;
        }
        if (deck.pendingDescription != null) {
            deck.description = deck.pendingDescription;
        }
        if (deck.pendingBackUrl != null) {
            deck.backUrl = deck.pendingBackUrl;
        }
        clearDeckPending(deck);
        touch(deck);
    }

    public static void clearDeckPending(CofDeck deck) {
        deck.pendingReviewStatus = null;
        deck.pendingName = null;
        deck.pendingDescription = null;
        deck.pendingBackUrl = null;
    }

    public static void approvePmvInitial(CofPmv pmv) {
        pmv.reviewStatus = ReviewStatus.APPROVED;
        touch(pmv);
    }

    public static void approvePmvRevision(CofPmv pmv) {
        if (pmv.pendingName != null) {
            pmv.name = pmv.pendingName;
        }
        if (pmv.pendingAuthor != null) {
            pmv.author = pmv.pendingAuthor;
        }
        if (pmv.pendingDescription != null) {
            pmv.description = pmv.pendingDescription;
        }
        if (pmv.pendingLink != null) {
            pmv.link = pmv.pendingLink;
        }
        clearPmvPending(pmv);
        touch(pmv);
    }

    public static void clearPmvPending(CofPmv pmv) {
        pmv.pendingReviewStatus = null;
        pmv.pendingName = null;
        pmv.pendingAuthor = null;
        pmv.pendingDescription = null;
        pmv.pendingLink = null;
    }

    public static void approveCardInitial(CofCard card) {
        card.reviewStatus = ReviewStatus.APPROVED;
        touch(card);
    }

    public static void approveCardRevision(CofCard card) {
        if (card.pendingName != null) {
            card.name = card.pendingName;
        }
        if (card.pendingDescription != null) {
            card.description = card.pendingDescription;
        }
        if (card.pendingImageUrl != null) {
            card.imageUrl = card.pendingImageUrl;
        }
        clearCardPending(card);
        touch(card);
    }

    public static void clearCardPending(CofCard card) {
        card.pendingReviewStatus = null;
        card.pendingName = null;
        card.pendingDescription = null;
        card.pendingImageUrl = null;
    }

    public static void rejectDeckRevision(CofDeck deck) {
        clearDeckPending(deck);
        deck.pendingReviewStatus = ReviewStatus.REJECTED;
        touch(deck);
    }

    public static void rejectPmvRevision(CofPmv pmv) {
        clearPmvPending(pmv);
        pmv.pendingReviewStatus = ReviewStatus.REJECTED;
        touch(pmv);
    }

    public static void rejectCardRevision(CofCard card) {
        clearCardPending(card);
        card.pendingReviewStatus = ReviewStatus.REJECTED;
        touch(card);
    }
}
