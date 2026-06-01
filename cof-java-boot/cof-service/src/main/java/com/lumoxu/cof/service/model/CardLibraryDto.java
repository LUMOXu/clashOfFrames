package com.lumoxu.cof.service.model;

import java.util.ArrayList;
import java.util.List;

public class CardLibraryDto {

    public String id;
    public String name;
    public String folderName;
    public String title;
    public String curator;
    public String description;
    public String version;
    public String link;
    public String backUrl;
    public int cardCount;
    public int pmvCount;
    public String reviewStatus;
    public String submitterClientId;
    public List<CardDto> cards = new ArrayList<>();

    public static class CardDto {
        public String id;
        public String libraryId;
        public String fileName;
        public int pmvId;
        public String pmvName;
        public String imageUrl;
        public String backUrl;
        public String shot;
        /** False for pending submissions — excluded from match dealing. */
        public Boolean approvedForPlay;
    }
}
