package com.tank2d.shared;

import org.json.JSONObject;
import java.util.HashMap;

public class Packet {
    public int type; // int thay cho enum
    public HashMap<String, Object> data = new HashMap<>();

    public Packet(int type) {
        this.type = type;
    }

    // Chuyển sang JSON string
    public String toJson() {
        JSONObject obj = new JSONObject();
        obj.put("type", type);
        obj.put("data", data);
        return obj.toString();
    }


    // Parse từ JSON string
    public static Packet fromJson(String json) {
        JSONObject obj = new JSONObject(json);
        Packet p = new Packet(obj.getInt("type"));
        JSONObject dataObj = obj.getJSONObject("data");
        for (String key : dataObj.keySet()) {
            p.data.put(key, dataObj.get(key));
        }
        return p;
    }

    @Override
    public String toString() {
        return toJson();
    }
}
