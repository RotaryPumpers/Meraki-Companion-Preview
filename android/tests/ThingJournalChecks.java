package com.rotarypumpers.meraki;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ThingJournalChecks {
    private static int checks;
    private static final String PHONE_DATE = "2026-01-01T10:00:00Z";
    private static final String THING_DATE = "2026-01-01T10:01:00Z";

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
    }

    private static JSONObject pairing(String host) throws Exception {
        return new JSONObject().put("format","MerakiThingJournal").put("host",host).put("port",8743)
                .put("pin","a".repeat(64)).put("token","b".repeat(43));
    }

    public static void main(String[] args) throws Exception {
        String[] privateFixtures = {
            String.join(".", "10", "42", "0", "17"),
            String.join(".", "172", "20", "0", "18"),
            String.join(".", "192", "168", "20", "19")
        };
        for (String host : privateFixtures) {
            check(ThingJournal.pair(pairing(host).toString()).getString("host").equals(host), "private Pi address accepted");
        }
        try {
            ThingJournal.pair(pairing(String.join(".", "203", "0", "113", "12")).toString());
            throw new AssertionError("non-private address accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }

        JSONArray phone = new JSONArray().put(new JSONObject().put("start_utc",PHONE_DATE)
                .put("notes","Original note").put("samples",new JSONArray()
                        .put(new JSONObject().put("uptime",10).put("weight_g",1))
                        .put(new JSONObject().put("uptime",11).put("weight_g",3))));
        JSONObject remoteShot = new JSONObject().put("start_utc",THING_DATE).put("thing_id","thing-new")
                .put("shot_detail",new JSONObject().put("points",new JSONArray().put(new JSONArray().put(0).put(2))));
        JSONObject updatedPhoneShot = new JSONObject().put("start_utc",PHONE_DATE).put("thing_id","phone-alias")
                .put("notes","Edited on Thing");
        JSONObject response = new JSONObject().put("aliases",new JSONObject().put(PHONE_DATE,"phone-alias"))
                .put("shots",new JSONArray().put(updatedPhoneShot).put(remoteShot));
        JSONArray merged = ThingJournal.reconcile(phone, packet -> {
            JSONObject outbound = packet.getJSONArray("shots").getJSONObject(0).getJSONObject("shot");
            check(outbound.getString("notes").equals("Original note"), "phone note sent");
            check(outbound.getJSONObject("shot_detail").getJSONArray("points").length()==2, "phone points sent");
            return response;
        });
        check(merged.length()==2, "aliases avoid phone duplicates and import a Thing shot");
        check(merged.getJSONObject(0).getString("notes").equals("Edited on Thing"), "Thing edit merged");
        check(merged.getJSONObject(1).getJSONArray("samples").getJSONObject(0).getDouble("weight_g")==2,
                "Thing point available for journal replay");
        check(phone.getJSONObject(0).getString("notes").equals("Original note"), "original journal array preserved");
        System.out.println("THING_JOURNAL_CHECKS_OK checks="+checks);
    }
}
