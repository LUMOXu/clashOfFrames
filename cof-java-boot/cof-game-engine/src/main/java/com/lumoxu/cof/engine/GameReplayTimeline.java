package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.List;

public class GameReplayTimeline {

    public long startedAt;
    /** Preferred client id for table orientation (bottom seat). */
    public String defaultViewerId;
    public List<GameReplayFrame> frames = new ArrayList<>();
}
