package com.rotarypumpers.meraki;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.EditText;
import com.rotarypumpers.meraki.core.BeanconquerorExport;
import org.json.JSONObject;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** A handoff opens the receiving app. It is never treated as a confirmed import. */
final class BeanconquerorController {
    private final Activity activity;
    private final BooleanSupplier allowed;

    BeanconquerorController(Activity activity, BooleanSupplier allowed) {
        this.activity = activity;
        this.allowed = allowed;
    }

    void share(JSONObject shot, String version) {
        if (!allowed.getAsBoolean()) {
            message("Disconnect recording and finish machine updates before sending a shot");
            return;
        }
        try {
            JSONObject snapshot = new JSONObject(shot.toString());
            JSONObject envelope = BeanconquerorExport.build(snapshot, version, false);
            JSONObject brew = envelope.getJSONObject("brew");
            String dose = brew.has("doseIn")
                    ? String.format(Locale.US, "%.2f g", brew.getJSONObject("doseIn").getDouble("value")) : "Unavailable";
            JSONObject pi = snapshot.optJSONObject("pre_infusion");
            String preinfusion = pi == null ? "Unavailable" : pi.optInt("enabled") == 1
                    ? String.format(Locale.US, "On · %d bar · %d s", pi.optInt("pressure_choice"), pi.optInt("duration_setting_s")) : "Off";
            String review = envelope.getJSONObject("bean").getString("name")
                    + String.format(Locale.US, "\n\nMeasured grind  %s\nFinal yield  %.1f g\nBrew time  %.1f s\nTemperature target  %.0f °C\nGrind setting  %s",
                    dose, brew.getJSONObject("beverageOut").getDouble("value"), brew.getDouble("brewTime"),
                    brew.getDouble("temperature"), brew.getString("grindSize"))
                    + "\nPre-infusion  " + preinfusion
                    + "\n\nWeight and cup-flow graphs, bag details and original date included."
                    + "\nUse the exact names already saved in Beanconqueror below. They are remembered after sending.";
            JSONObject bag = snapshot.optJSONObject("bag");
            JSONObject recipe = snapshot.optJSONObject("recipe");
            String identity = bag != null ? bag.optString("id", envelope.getJSONObject("bean").getString("name"))
                    : recipe != null ? recipe.optString("id", envelope.getJSONObject("bean").getString("name")) : envelope.getJSONObject("bean").getString("name");
            String beanKey = "bc_bean_name_" + java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String key = "beanconqueror_sent_" + snapshot.getString("start_utc");
            boolean sentBefore = activity.getPreferences(0).contains(key);
            if (sentBefore) review += "\n\nThis shot was sent before. Sending again may create a duplicate.";
            android.content.Context context = LightDialogs.context(activity);
            ScrollView scroll = new ScrollView(context);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
            content.setPadding(pad, 0, pad, pad / 2);
            TextView text = new TextView(context);
            text.setText(review);
            text.setTextSize(16);
            content.addView(text);
            EditText beanName = nameField(context, content, "Bean in Beanconqueror", activity.getPreferences(0).getString(beanKey, envelope.getJSONObject("bean").getString("name")));
            EditText grinderName = nameField(context, content, "Grinder in Beanconqueror", activity.getPreferences(0).getString("bc_grinder_name", brew.getString("grinderName")));
            EditText methodName = nameField(context, content, "Method in Beanconqueror", activity.getPreferences(0).getString("bc_method_name", brew.getString("preparationMethod")));
            CheckBox notes = new CheckBox(context);
            notes.setText("Include tasting notes");
            notes.setChecked(true);
            content.addView(notes);
            scroll.addView(content);
            android.app.AlertDialog reviewDialog = LightDialogs.builder(activity).setTitle(sentBefore ? "Send this shot again?" : "Send to Beanconqueror")
                    .setView(scroll)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open Beanconqueror", null).create();
            reviewDialog.setOnShowListener(shown -> reviewDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(clicked -> {
                        if (!allowed.getAsBoolean()) { message("Disconnect recording before sending a shot"); return; }
                        try {
                            requireSupportedReceiver();
                            JSONObject matched = BeanconquerorExport.matchNames(BeanconquerorExport.build(snapshot, version, notes.isChecked()),
                                    beanName.getText().toString(), grinderName.getText().toString(), methodName.getText().toString());
                            String link = BeanconquerorExport.link(matched);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                            intent.setPackage("com.beanconqueror.app");
                            activity.startActivity(intent);
                            activity.getPreferences(0).edit().putLong(key, System.currentTimeMillis())
                                    .putString(beanKey, matched.getJSONObject("bean").getString("name"))
                                    .putString("bc_grinder_name", matched.getJSONObject("brew").getString("grinderName"))
                                    .putString("bc_method_name", matched.getJSONObject("brew").getString("preparationMethod")).apply();
                            reviewDialog.dismiss();
                        } catch (ActivityNotFoundException e) {
                            LightDialogs.builder(activity).setTitle("Beanconqueror required")
                                    .setMessage("Install regular Beanconqueror 8.8.2 or later, open it once to finish setup, then send this shot again.")
                                    .setPositiveButton("Close", null).show();
                        } catch (Exception e) { message("Shot could not be sent: " + e.getMessage()); }
                    }));
            reviewDialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            reviewDialog.show();
        } catch (Exception e) { message("Shot could not be sent: " + e.getMessage()); }
    }

    private EditText nameField(android.content.Context context, LinearLayout content, String label, String value) {
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextSize(14);
        title.setPadding(0, 18, 0, 0);
        content.addView(title);
        EditText field = new EditText(context);
        field.setText(value);
        field.setContentDescription(label);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(512)});
        content.addView(field);
        return field;
    }

    private void requireSupportedReceiver() throws ActivityNotFoundException {
        try {
            String version = activity.getPackageManager().getPackageInfo("com.beanconqueror.app", 0).versionName;
            java.util.regex.Matcher match = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(version == null ? "" : version);
            if (!match.find()) throw new ActivityNotFoundException("Unsupported Beanconqueror version");
            int major = Integer.parseInt(match.group(1)), minor = Integer.parseInt(match.group(2));
            int patch = match.group(3) == null ? 0 : Integer.parseInt(match.group(3));
            if (major < 8 || (major == 8 && (minor < 8 || (minor == 8 && patch < 2))))
                throw new ActivityNotFoundException("Beanconqueror 8.8.2 or later is required");
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new ActivityNotFoundException("Beanconqueror is not installed");
        }
    }

    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
}
