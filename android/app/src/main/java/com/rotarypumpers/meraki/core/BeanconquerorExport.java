package com.rotarypumpers.meraki.core;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/** Beanconqueror 8.8.2's public ADD_BREW v1 contract. */
public final class BeanconquerorExport {
    public static final int MAX_JSON_BYTES = 512 * 1024;
    public static final int MAX_URL_BYTES = 128 * 1024;
    private BeanconquerorExport() {}

    public static JSONObject build(JSONObject shot, String version, boolean includeNotes) throws Exception {
        // Reuse the established complete-shot, curve, cadence and auto-tare validation.
        JSONObject visualizer = VisualizerExport.build(shot, false, null);
        JSONArray weights = visualizer.getJSONObject("brewFlow").getJSONArray("weight");
        JSONObject provenance = visualizer.getJSONObject("meta").getJSONObject("meraki");
        JSONObject recipe = shot.optJSONObject("recipe");
        JSONObject bag = shot.optJSONObject("bag");
        String recipeName = recipe == null ? "Recorded espresso" : recipe.optString("name", "Recorded espresso");
        JSONObject brew = new JSONObject()
                .put("date", java.time.Instant.parse(shot.getString("start_utc")).truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString())
                .put("waterIn", quantity(0, "ml"))
                .put("beverageOut", quantity(shot.getDouble("settled_yield_g"), "g"))
                .put("brewTime", rounded(shot.getDouble("duration_estimate_s")))
                .put("grindSize", String.valueOf(shot.getInt("grind_setting")))
                .put("grinderName", "Meraki integrated grinder")
                .put("preparationMethod", "Meraki espresso")
                .put("preparationType", "PORTAFILTER");
        JSONObject grind = shot.optJSONObject("grind_workflow");
        if (grind != null && grind.has("settled_dose_g")) {
            double dose = finite(grind.getDouble("settled_dose_g"));
            if (dose < 0 || dose > 200) throw new IllegalArgumentException("Measured grind weight is outside the import range");
            brew.put("doseIn", quantity(dose, "g"));
        }
        double target = finite(shot.getDouble("brew_target_c"));
        if (target < -50 || target > 250) throw new IllegalArgumentException("Temperature target is outside the import range");
        brew.put("temperature", target);
        double drip = CupFlow.firstDetectedDrip(shot);
        if (Double.isFinite(drip)) brew.put("firstDripTime", rounded(drip));
        JSONObject pi = shot.optJSONObject("pre_infusion");
        StringBuilder note = new StringBuilder(recipeName)
                .append("\nTemperature target: ").append(target).append(" °C.")
                .append("\nBrew time is estimated. Weight graph includes final drips.")
                .append("\nWater input is unmeasured (required import field set to 0 ml).");
        double averageFlow = ShotInsights.average(shot);
        if (Double.isFinite(averageFlow)) note.append(String.format(Locale.US, "\nAverage cup flow at brew stop: %.2f g/s.", averageFlow));
        note.append("\nCup flow is the calculated graph trace. Beanconqueror's built-in flow summary is unavailable for this import type.");
        if (!brew.has("doseIn")) note.append("\nMeasured dose unavailable.");
        if (grind != null && grind.has("duration_estimate_s")) {
            note.append(String.format(Locale.US, "\nGrind duration: %.1f s.", finite(grind.getDouble("duration_estimate_s"))));
        }
        if (pi != null) {
            if (pi.optInt("enabled") == 1) brew.put("bloomTime", finite(pi.getDouble("duration_setting_s")));
            note.append("\nPre-infusion setting: ").append(pi.optInt("enabled") == 1 ? "On" : "Off")
                    .append(" · ").append(pi.optInt("pressure_choice")).append(" bar · ")
                    .append(pi.optInt("duration_setting_s")).append(" s.");
        }
        if(shot.optJSONObject("brand_assignment")!=null)note.append("\nBrand assigned: ").append(shot.getJSONObject("brand_assignment").getString("brand"));
        if (bag != null) {
            for (String key : new String[]{"roast_date", "opened_date", "age_days", "brand"}) {
                if (bag.has(key)) note.append("\n").append(key.equals("brand")&&shot.has("brand_assignment")?"Originally recorded brand":key.replace('_', ' ')).append(": ").append(bag.get(key));
            }
        }
        if (includeNotes) {
            String taste = shot.optString("taste");
            if (!taste.isEmpty()) note.append("\nTaste: ").append(taste);
            String written = shot.optString("notes");
            if (!written.isEmpty()) note.append("\n\n").append(written);
        }
        if (note.length() > 10000) throw new IllegalArgumentException("The tasting note is too long for Beanconqueror. Turn off Include tasting notes and try again.");
        brew.put("note", note.toString());

        JSONArray times = new JSONArray(), deltas = new JSONArray(), water = new JSONArray();
        JSONArray absoluteTimes = new JSONArray(), rates = new JSONArray();
        double previousWeight = 0;
        long previousTime = 0;
        double[] cupFlow = CupFlow.rates(shot.getJSONArray("samples"), shot.optDouble("sampling_gap_limit_s", .75));
        for (int i = 0; i < weights.length(); i++) {
            JSONObject sample = weights.getJSONObject(i);
            long milliseconds = Math.round(Double.parseDouble(sample.getString("brew_time")) * 1000);
            double weight = sample.getDouble("actual_weight");
            times.put(milliseconds - previousTime);
            // Decigrams on the wire, including negative scale jitter. Preserve the curve.
            deltas.put(Math.round((weight - previousWeight) * 1000000.0) / 100000.0);
            water.put(0);
            if (Double.isFinite(cupFlow[i])) {
                absoluteTimes.put(milliseconds);
                rates.put(cupFlow[i]);
            }
            previousWeight = weight;
            previousTime = milliseconds;
        }
        JSONArray metrics = new JSONArray();
        metrics.put(new JSONObject().put("key", "companion_cup_flow")
                .put("name", "Cup flow from scale (2 s smoothing)").put("unit", "g/s")
                .put("kind", "measured").put("t", absoluteTimes).put("v", rates));
        provenance.put("notes_included", includeNotes).put("recipe_name", recipeName).put("water_input_measured", false);
        if (grind != null) provenance.put("grind_workflow", new JSONObject(grind.toString()));
        JSONObject result = new JSONObject().put("v", 1)
                .put("app", new JSONObject().put("name", "Meraki Companion").put("version", version))
                .put("brew", brew)
                .put("flow", new JSONObject().put("fidelity", "full").put("t", times).put("weight", deltas).put("waterDispensed", water))
                .put("metrics", metrics)
                .put("imported", new JSONObject().put("source", "meraki_companion")
                        .put("sourceName", "Meraki Companion").put("schema", 1).put("params", provenance));
        JSONObject assignedBrand=shot.optJSONObject("brand_assignment");
        JSONObject bean = new JSONObject().put("name", bag == null ? recipeName : bag.optString("name", recipeName))
                .put("note", "Imported from Companion. Recipe: " + recipeName);
        if (bag != null) {
            if (bag.has("brand")) bean.put("roaster", bag.getString("brand"));
            if (bag.has("roast_date")) bean.put("roastingDate", bag.getString("roast_date"));
        }
        if(assignedBrand!=null)bean.put("roaster",assignedBrand.getString("brand"));
        result.put("bean", bean);
        if (result.toString().getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Shot is too large for Beanconqueror's complete-shot import");
        return result;
    }

    /** Name hints must match the user's own Beanconqueror library. Original shot metadata is retained. */
    public static JSONObject matchNames(JSONObject envelope, String bean, String grinder, String method) throws Exception {
        String[] values = {bean, grinder, method};
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null || values[i].trim().isEmpty() || values[i].trim().length() > 512)
                throw new IllegalArgumentException("Enter the existing bean, grinder and method names (1–512 characters)");
            values[i] = values[i].trim();
        }
        JSONObject result = new JSONObject(envelope.toString());
        result.getJSONObject("bean").put("name", values[0]);
        result.getJSONObject("brew").put("grinderName", values[1]).put("preparationMethod", values[2]);
        return result;
    }

    public static String link(JSONObject envelope) throws Exception {
        byte[] json = envelope.toString().getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_JSON_BYTES) throw new IllegalArgumentException("Shot exceeds Beanconqueror's import limit");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(json); }
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        StringBuilder url = new StringBuilder("beanconqueror://ADD_BREW?len=").append(payload.length());
        int chunk = 0;
        for (int start = 0; start < payload.length(); start += 400) {
            url.append("&shareBrew").append(chunk++).append('=').append(payload, start, Math.min(start + 400, payload.length()));
        }
        if (chunk > 1024 || url.toString().getBytes(StandardCharsets.UTF_8).length > MAX_URL_BYTES)
            throw new IllegalArgumentException("Shot is too large to open safely in Beanconqueror");
        return url.toString();
    }

    private static JSONObject quantity(double value, String unit) throws Exception {
        return new JSONObject().put("value", finite(value)).put("unit", unit);
    }
    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Shot contains an invalid number");
        return value;
    }
    private static double rounded(double value) { return Math.round(value * 1000.0) / 1000.0; }
}
