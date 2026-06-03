package com.lumoxu.cof.service.model;

import java.util.ArrayList;
import java.util.List;

public class CardLibraryDto {

    /** Public library id — numeric deck id. */
    public String id;
    public String name;
    public String title;
    public String description;
    public String backUrl;
    public int cardCount;
    public int pmvCount;
    public String reviewStatus;
    public String pendingReviewStatus;
    public String submitterClientId;
    public List<CardDto> cards = new ArrayList<>();

    public static class CardDto {
        /** Database card id. */
        public String id;
        public String libraryId;
        public long pmvId;
        public String pmvName;
        public String cardName;
        public String cardDescription;
        public String imageUrl;
        public String backUrl;
        public Boolean approvedForPlay;
        public String pendingReviewStatus;
    }
}
