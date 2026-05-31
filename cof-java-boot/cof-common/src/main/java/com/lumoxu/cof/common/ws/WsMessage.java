package com.lumoxu.cof.common.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsMessage {

    /** Message type: PLAY, RING, LOAD, PING, SYNC, AUDIO, ROOM */
    public String t;
    public String g;
    public String r;
    public String cid;
    public Integer ti;
    public String pc;
    public Integer ld;
    public Integer lt;
    public Boolean dn;
    public String au;
    public JsonNode sync;
    public JsonNode room;
    public String err;

    public static WsMessage ofType(String type) {
        WsMessage message = new WsMessage();
        message.t = type;
        return message;
    }
}
