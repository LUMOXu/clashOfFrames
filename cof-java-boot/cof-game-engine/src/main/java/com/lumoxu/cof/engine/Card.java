package com.lumoxu.cof.engine;

public class Card {

    public String id;
    public String libraryId;
    public String fileName;
    /** Global {@code cof_pmv.id} used for match grouping in play. */
    public Long pmvId;
    public String pmvName;
    public String shot;
    public String imageUrl;
    public String backUrl;
    public int playedSeq;
    public String playedBy;
}
