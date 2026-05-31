package com.lumoxu.cof.service.redis;

public final class RedisKeys {

    public static final String SESSION_PREFIX = "cof:session:";
    public static final String GAME_PREFIX = "cof:game:";
    public static final String ROOM_PREFIX = "cof:room:";
    public static final String CACHE_CARD_LIBRARIES = "cof:cache:card-libraries";
    public static final String CACHE_COMPUTER_PLAYERS = "cof:cache:computer-players";
    public static final String CACHE_LEADERBOARD = "cof:cache:leaderboard";
    public static final String DECK_BUNDLE_PREFIX = "cof:deck:bundle:";
    public static final String ROOM_CATALOG_PREFIX = "cof:room:";
    public static final String ROOM_CATALOG_SUFFIX = ":catalog";
    public static final String GAME_CATALOG_PREFIX = "cof:game:";
    public static final String GAME_CATALOG_SUFFIX = ":catalog";
    public static final String ROOM_INDEX = "cof:rooms:index";
    public static final String PLAYER_ROOM_PREFIX = "cof:player-room:";

    private RedisKeys() {
    }

    public static String playerRoom(String clientId) {
        return PLAYER_ROOM_PREFIX + clientId;
    }

    public static String session(String token) {
        return SESSION_PREFIX + token;
    }

    public static String game(String gameId) {
        return GAME_PREFIX + gameId;
    }

    public static String room(String roomId) {
        return ROOM_PREFIX + roomId;
    }

    public static String deckBundle(String deckId) {
        return DECK_BUNDLE_PREFIX + deckId;
    }

    public static String roomCatalog(String roomId) {
        return ROOM_CATALOG_PREFIX + roomId + ROOM_CATALOG_SUFFIX;
    }

    public static String gameCatalog(String gameId) {
        return GAME_CATALOG_PREFIX + gameId + GAME_CATALOG_SUFFIX;
    }
}
