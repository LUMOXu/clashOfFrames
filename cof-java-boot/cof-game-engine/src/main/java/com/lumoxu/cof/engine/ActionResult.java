package com.lumoxu.cof.engine;

public class ActionResult {

    public boolean ok;
    public String error;
    public Player player;
    public Game game;

    public static ActionResult fail(String error) {
        ActionResult result = new ActionResult();
        result.ok = false;
        result.error = error;
        return result;
    }

    public static ActionResult ok(Player player) {
        ActionResult result = new ActionResult();
        result.ok = true;
        result.player = player;
        return result;
    }

    public static ActionResult okGame(Game game) {
        ActionResult result = new ActionResult();
        result.ok = true;
        result.game = game;
        return result;
    }
}
