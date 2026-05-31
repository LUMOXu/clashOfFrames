package com.lumoxu.cof.engine;

import java.util.ArrayList;
import java.util.List;

public class ResultInfo {

    public List<ResultPlayer> players = new ArrayList<>();
    public List<List<Integer>> counts = new ArrayList<>();

    public static class ResultPlayer {
        public String clientId;
        public String username;
    }
}
