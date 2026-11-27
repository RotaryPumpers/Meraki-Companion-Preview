package com.rotarypumpers.meraki;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import android.content.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import org.json.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import java.time.Instant;import java.time.YearMonth;import java.time.ZoneId;import java.time.format.DateTimeFormatter;import java.nio.charset.StandardCharsets;import java.io.*;import com.rotarypumpers.meraki.core.*;import com.rotarypumpers.meraki.sync.ForumSync;

public final class MainActivity extends Activity {
    private ShotReplay replay;
    private PairingQrController qrPairing;
    private boolean pairingAllowed(){return !syncing.get()&&!busy.get()&&!running.get()&&!retryPending&&!temperatureBusy.get()&&!recipeOperation.active()&&!recordAction.active();}
    private PairingQrController qrPairing(){if(qrPairing==null)qrPairing=new PairingQrController(this,this::pairingAllowed,()->{show(false);startRecording();});return qrPairing;}

    private BeanconquerorController beanconqueror;
    private void sendBeanconqueror(JSONObject shot) {
        if (beanconqueror == null) beanconqueror = new BeanconquerorController(this, this::pairingAllowed);
        beanconqueror.share(shot, appVersion());
    }
    private VisualizerController visualizer;
    private VisualizerController visualizer(){if(visualizer==null)visualizer=new VisualizerController(this,()->!busy.get()&&!running.get()&&!retryPending&&!temperatureBusy.get()&&!recipeOperation.active());return visualizer;}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);visualizer().callback(intent);}

    private final RecipeOperationState recipeOperation=new RecipeOperationState();
    private RecipeProgressDialog recipeProgress;
    private volatile PreinfusionControlTransport piConnection;
    private volatile HomeControlTransport homeConnection;
    private TextView homeAppearanceNote;
    private final ExecutorService temperatureWorker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean temperatureBusy=new AtomicBoolean();
    private volatile boolean temperatureAllowed;
    private volatile TemperatureControlTransport temperatureConnection;
    private TextView temperatureLabel,temperatureWarning,yieldLabel,yieldWarning,doseLabel,doseWarning,recipeControlWarning;
    private volatile DoseControlTransport doseConnection;
    private volatile YieldControlTransport yieldConnection;
    private LinearLayout graphArea;private Button graphToggle;private boolean graphExpanded;private static final int SAGE=CompanionPalette.SAGE;

    private final Handler recoveryHandler=new Handler(Looper.getMainLooper());
    private final ConnectionRecovery recovery=new ConnectionRecovery();
    private boolean connectionRequested,retryPending; private int connectionGeneration;
    private void cancelRecovery(){connectionRequested=false;retryPending=false;connectionGeneration++;recoveryHandler.removeCallbacksAndMessages(null);}

    private InlineReminderView recordAnimation;
    private TextView waterWarning;
    private String workflowReminder="";private boolean foreground,lastWaterLow;private long waterReceivedAt;
    private final Handler waterHandler=new Handler(Looper.getMainLooper());
    private final Runnable waterRefresh=new Runnable(){public void run(){renderWater();if(foreground)waterHandler.postDelayed(this,1000);}};
    private void renderWater(){
        refreshCompact();
        if(waterWarning==null)return;
        waterWarning.setVisibility(lastWaterLow?View.VISIBLE:View.GONE);
        if(!lastWaterLow)return;
        boolean fresh=running.get()&&foreground&&waterReceivedAt>0&&SystemClock.elapsedRealtime()-waterReceivedAt<3000;
        String label=fresh?"Water low\nRefill tank":"Water low\nLast seen";
        waterWarning.setText(label);
        waterWarning.setTextColor(fresh?0xff75d4ff:MUTED);
        waterWarning.setContentDescription(label.replace('\n',' '));
    }
    private UsageTotals usage;
    private RecipeStore recipes; private JSONObject selectedRecipe; private int journalLimit=30;
    private String journalQuery="",recipeQuery="";private int currentPage=0;private int journalFilter=0;private boolean journalOldest=false;
    private final AtomicBoolean syncing=new AtomicBoolean(); private volatile boolean cancelSync;
    private final Map<String,JSONObject> captures=new HashMap<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final AtomicBoolean running=new AtomicBoolean(),busy=new AtomicBoolean();private volatile SecureRecorderTransport secureConnection;private JsonStore store;private volatile JSONArray shots=new JSONArray(),live=new JSONArray();private LinearLayout body;private TextView status,weight,scaleLabel,detail,workflowStatus,lastResult;private EditText ip;private Button connect,stop;private ShotChart chart;private String log;private Typeface face,serifFace;private static final int BG=CompanionPalette.BACKGROUND,FG=CompanionPalette.TEXT,MUTED=CompanionPalette.MUTED,GOLD=CompanionPalette.ACCENT;
    public void onCreate(Bundle saved){super.onCreate(saved);face=getResources().getFont(R.font.space_grotesk);serifFace=Typeface.createFromAsset(getAssets(),"detail-serif-bold.ttf");store=new JsonStore(getFilesDir());usage=new UsageTotals(getFilesDir());recipes=new RecipeStore(getFilesDir());try{String selected=getPreferences(0).getString("selected_recipe","");JSONArray savedRecipes=recipes.load();for(int i=0;i<savedRecipes.length();i++)if(selected.equals(savedRecipes.getJSONObject(i).optString("id")))selectedRecipe=savedRecipes.getJSONObject(i);}catch(Exception e){toast("Recipes could not be read; original file retained");}try{shots=store.load();if(CaptureColors.upgrade(shots))store.save(shots);}catch(Exception e){toast("Journal could not be read; original file retained");}try{usage.backfill(shots,new File(getFilesDir(),"grinder-events.jsonl"));}catch(Exception e){toast("Usage totals unavailable: "+e.getMessage());}show(false);visualizer().callback(getIntent());}
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "Version unavailable";}}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTypeface(face);v.setTextColor(color);v.setPadding(0,dp(7),0,dp(7));return v;}
    private Button button(String s,Runnable action){
        Button b=new Button(this);b.setText(s);b.setTypeface(face);b.setTextSize(14);b.setAllCaps(false);b.setSingleLine(true);b.setHorizontallyScrolling(false);b.setGravity(Gravity.CENTER);b.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff929b95,GOLD}));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));
        GradientDrawable shape=new GradientDrawable();shape.setColor(CompanionPalette.SURFACE);shape.setCornerRadius(dp(10));shape.setStroke(dp(1),CompanionPalette.LINE);
        b.setBackgroundTintList(null);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44efb48d),shape,null));
        b.setOnClickListener(v->{if(!blockDuringRecipe())action.run();});return b;
    }
    private void actions(LinearLayout parent,Button... buttons){
        // Keep one container child so recipe navigation can safely replace page content.
        LinearLayout group=new LinearLayout(this);group.setOrientation(1);
        float available=getResources().getConfiguration().screenWidthDp-36;
        int columns=(getResources().getConfiguration().fontScale>1.15f||available/buttons.length<80)?2:buttons.length;
        for(int start=0;start<buttons.length;start+=columns){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            for(int i=start;i<Math.min(start+columns,buttons.length);i++){LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);if(i>start)bp.leftMargin=dp(8);row.addView(buttons[i],bp);}
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(4);rp.bottomMargin=dp(4);group.addView(row,rp);
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);lp.bottomMargin=dp(4);parent.addView(group,lp);
    }
    private void importJournal(){if(busy.get()){toast("Disconnect before importing");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);}
    private static final int EXPORT_RECENT_SHOTS = 6;
    private static final int RECENT_EXPORT_LIMIT = 10;

    private void exportJournal() {
        if (busy.get()) {
            toast("Disconnect before exporting");
            return;
        }
        LightDialogs.builder(this)
            .setTitle("Export journal")
            .setItems(new String[]{"Last 10 shots", "Full journal backup"}, (dialog, choice) -> {
                boolean recent = choice == 0;
                if (recent && JournalBrowse.select(shots, "", 0, false).length() == 0) {
                    toast("No saved shots to export");
                    return;
                }
                String filename = recent ? "meraki-last-10-shots.json" : "meraki-journal-backup.json";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, filename);
                startActivityForResult(intent, recent ? EXPORT_RECENT_SHOTS : 2);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void writeRecentShots(android.net.Uri destination) throws Exception {
        JSONArray ordered = JournalBrowse.select(shots, "", 0, false);
        JSONArray recent = new JSONArray();
        int count = Math.min(ordered.length(), RECENT_EXPORT_LIMIT);
        for (int index = 0; index < count; index++) {
            recent.put(ordered.getJSONObject(index));
        }
        try (OutputStream out = getContentResolver().openOutputStream(destination)) {
            if (out == null) throw new IOException("Cannot open destination");
            out.write(recent.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        toast(count == 1 ? "1 shot exported" : count + " shots exported");
    }

    private void toolsMenu(){LightDialogs.builder(this).setTitle("Companion").setItems(new String[]{"Help","Quick machine adjustments…","Machine connection","Visualizer","Journal & app tools"},(d,w)->{if(w==0)recordHelp();else if(w==1)recordQuickControls();else if(w==2)machineConnectionSettings();else if(w==3)visualizer().settings();else toolsLibraryMenu();}).show();}
    private void thingMenu(){
        String saved=getPreferences(0).getString("thing_pair","");
        if(saved.isEmpty()){scanThing();return;}
        LightDialogs.builder(this).setTitle("MerakiThing").setItems(new String[]{"Sync shots and recipes","Pair again"},(d,w)->{if(w==0)syncThing();else scanThing();}).show();
    }
    private void scanThing(){
        Intent scan=new Intent(this,PairingScanActivity.class);
        scan.setAction("com.google.zxing.client.android.SCAN");
        scan.putExtra("SCAN_FORMATS","QR_CODE");scan.putExtra("PROMPT_MESSAGE","Scan Pair Companion on the Thing");
        startActivityForResult(scan,8259);
    }
    private void syncThing(){
        if(!pairingAllowed()){toast("Finish recording before syncing");return;}
        worker.execute(()->{try{synchronized(this){
            JSONArray next=ThingJournal.sync(ThingJournal.pair(getPreferences(0).getString("thing_pair","")),shots);
            store.save(next);shots=next;
            String pairing=getPreferences(0).getString("thing_pair","");
            JSONObject baseline=new JSONObject(getPreferences(0).getString("thing_recipe_base","{}"));
            JSONArray sent=recipes.load();
            JSONObject result=ThingJournal.request(ThingJournal.pair(pairing),new JSONObject()
                .put("version",1).put("operation","recipes").put("recipes",sent).put("base",baseline));
            if(!result.has("recipes"))throw new java.io.IOException("Update MerakiThing to 0.18.0 for recipe sync");
            JSONArray merged=RecipeStore.decode(RecipeStore.encode(result.getJSONArray("recipes")));
            synchronized(recipes){
                if(!sent.toString().equals(recipes.load().toString()))
                    throw new java.io.IOException("Recipes changed during sync. Tap sync again.");
                recipes.save(merged);
                for(int i=0;i<merged.length();i++){
                    JSONObject r=merged.getJSONObject(i);
                    if(r.optString("id").equals(getPreferences(0).getString("selected_recipe","")))selectedRecipe=r;
                }
                getPreferences(0).edit().putString("thing_recipe_base",result.getJSONObject("base").toString()).commit();
            }
        }runOnUiThread(()->show(true));toast("Shots and recipes synced");}catch(Exception e){toast("Journal sync: "+e.getMessage());}});
    }
    private void toolsLibraryMenu(){
        LightDialogs.builder(this).setTitle("Journal & app tools").setItems(new String[]{"Forum connection","Last sync details","Export journal","Import JSON / backup","Export recording logs","Load bundled recordings","App notes","Record animations","Machine connection","Visualizer","Sync with MerakiThing"},(d,which)->{
            switch(which){case 10:thingMenu();break;case 9:visualizer().settings();break;case 8:machineConnectionSettings();break;case 7:cupReminderSettings();break;case 0:forumSettings();break;case 1:LightDialogs.builder(this).setTitle("Last forum sync").setMessage(getPreferences(0).getString("last_forum_sync","No sync yet.")).setPositiveButton("Close",null).show();break;case 2:exportJournal();break;case 3:importJournal();break;case 4:exportLogs();break;case 5:LightDialogs.builder(this).setTitle("Example recordings").setMessage("No example recordings are included in this build. Your own saved shots appear under Shots.").setPositiveButton("Close",null).show();break;case 6:LightDialogs.builder(this).setTitle("Meraki Companion · "+appVersion()).setMessage("Cup flow is calculated from recorded scale weight with a shared two-second smoothing method for charts and Visualizer. First detected drip is a scale-based estimate. Pressure settings are not pressure measurements. Electrical readings remain in Diagnostics.\n\nGrinder records follow the motor-command state and grinder scale. A preceding grind is associated by timing, not proof of which grounds were used. No cup-presence trigger is assumed.\n\nDose, yield and brew temperature reflect selected targets; measured output may vary.\n\nARM recorder: 500 ms snapshots of the display cache, streamed and saved on this phone. No recording files are kept on the machine. The recorder stops if acknowledgements cease for fifteen seconds. Each session is limited to two hours. A fixed 64-sample RAM buffer tolerates brief delivery stalls. Record animations are off by default. Enable dose cup, brew cup or steam separately in Record animations. They appear subtly behind the graph while the relevant machine state or workflow reminder is active. Water-low alerts take priority. Live weight is labeled Grinder scale during grinding and Brew scale otherwise. Ready does not mean the displayed scale has just been tared. Water low reflects the machine warning; a stale warning is marked Last seen. Network interruption or acknowledgement timeout closes the current session and retries up to six times in Secure Link mode (three in legacy mode) while the app stays visible. Wake the machine if asleep; connection failures alone do not identify sleep. The retry budget does not reset after a brief reconnection. Each retry verifies the machine again and starts a separate session. Missing shot data is not reconstructed. Disconnect or leaving the app cancels retries. Other recording errors require manual reconnection. Recent raw session logs are limited to about 100 MB; saved shots are not automatically removed.\n\nSecure Link beta: Tools > Machine connection pairs an already-running local listener. Recording uses certificate-pinned TLS and a secret key; there is no automatic fallback to Telnet. Pairing credentials are encrypted with Android Keystore and excluded from journal exports. Machine screenshots are unavailable in Secure Link mode; shot charts and replay remain available. Secure Link 0.2.0 supports verified automatic startup after installation. Initial installation and pairing still require setup. Telnet on the machine remains enabled until separately retired.\n\nRecording observes machine state. Manual temperature, yield and target-dose updates preserve the other settings. Use recipe applies all three targets sequentially, then its Home color and name. Home appearance can also be changed separately and retained across navigation and restart. An uncertain step stops the recipe; remaining steps do not run automatically. Keep the app visible during updates. None of these actions starts grinding or brewing. Journal entries are stored on this phone. Forum sync is manual; deleting a phone entry does not delete the forum copy. Short brew events under 15 seconds are shown under Shots > Filter > Short events and excluded from usage totals and cup-flow comparisons. Usage totals count observed brew events of at least 15 seconds and measured grinds of at least 5 g; they are not machine lifetime counters. Totals survive journal deletion and are included in journal backups. Cup flow is a two-second smoothed change in scale weight, not pump flow. Average cup flow uses weight at brew stop divided by brew duration; later drips are excluded. Favourite comparisons require the same recipe ID, machine settings and pre-infusion settings, with complete ungapped recordings. Typical range means the middle 50% of at least four references. No grinder-adjustment instruction is inferred from flow alone.").setPositiveButton("Close",null).show();break;}
        }).show();
    }
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    private void show(boolean journal){showPage(journal?1:0);}
    private void showPage(int page){closeDetailPage();currentPage=page;boolean journal=page==1;
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(1);shell.setBackgroundColor(BG);
        shell.setOnApplyWindowInsetsListener((v,insets)->{shell.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(16),dp(10),dp(16),dp(20));scroll.addView(body);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(shell);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text(page==1?"Your shots":page==2?"Recipes":"Record",22,FG);heading.setTypeface(serifFace);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));Button gear=button("⋯",this::toolsMenu);gear.setContentDescription("App tools and settings");gear.setBackgroundColor(Color.TRANSPARENT);header.addView(gear,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(header);if(page==0&&getResources().getConfiguration().screenHeightDp<500){header.setVisibility(View.GONE);body.setPadding(dp(12),0,dp(12),dp(12));}
        LinearLayout nav=new LinearLayout(this);GradientDrawable navBg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff202522,CompanionPalette.CARD});navBg.setCornerRadius(dp(11));navBg.setStroke(dp(1),CompanionPalette.LINE);nav.setBackground(navBg);
        String[] labels={"Record","Shots","Recipes"};int[] icons={R.drawable.nav_record,R.drawable.nav_cup,R.drawable.nav_book};Runnable[] callbacks={()->show(false),()->show(true),this::showRecipes};
        for(int n=0;n<labels.length;n++){Button tab=button(labels[n],callbacks[n]);tab.setTextSize(12);tab.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);tab.setPadding(dp(2),dp(5),dp(2),dp(5));tab.setCompoundDrawablePadding(dp(3));android.graphics.drawable.Drawable icon=getDrawable(icons[n]);icon.setBounds(0,0,dp(20),dp(20));icon.setTint(n==page?GOLD:MUTED);if(getResources().getConfiguration().screenHeightDp<500)tab.setCompoundDrawables(icon,null,null,null);else tab.setCompoundDrawables(null,icon,null,null);tab.setTextColor(n==page?GOLD:MUTED);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(10));bg.setColor(n==page?0xff303b33:Color.TRANSPARENT);tab.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44efb48d),bg,null));nav.addView(tab,new LinearLayout.LayoutParams(0,-2,1));}shell.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        if(page==2||page==3)return;
        if(journal){journal();return;}
        compactRecord();
    }
    private TextView preInfusionLabel,shotTimer,recordTarget,recordFlow,recordMatch,recordRecipeName;private Button recipeApplyButton,recordChange;private LinearLayout recordMini,recordHero;private GradientDrawable recordHeroBackground;private ProgressBar recordProgress;
    private Sample recordSample;private Recorder.State recordState=Recorder.State.WAITING;private long recordSampleAt;private Double recordDisplayWeight;private String recordDisplayLabel="Brew scale",recordDisplayMessage="",recordConnectionMessage="",recordReferenceKey="";private double recordStart=Double.NaN,recordGrindElapsed=Double.NaN;private JSONObject recordStoppedShot;
    private final RecordActionGate recordAction=new RecordActionGate();
    private boolean recordFresh(){return RecordPresentation.fresh(running.get(),foreground,recordSampleAt,SystemClock.elapsedRealtime());}
    private void compactRecord(){
        boolean landscape=getResources().getConfiguration().screenHeightDp<500;
        LinearLayout connectionRow=new LinearLayout(this);connectionRow.setGravity(Gravity.CENTER_VERTICAL);status=text("Machine offline",12,0xffef9786);connectionRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));connect=button("Connect",this::startRecording);greenButton(connect);stop=button("Disconnect",this::disconnect);redButton(stop);stop.setPadding(0,0,0,0);connectionRow.addView(connect,new LinearLayout.LayoutParams(dp(108),dp(48)));connectionRow.addView(stop,new LinearLayout.LayoutParams(dp(108),dp(48)));if(landscape){Button menu=button("⋯",this::toolsMenu);menu.setContentDescription("App tools and settings");menu.setBackgroundColor(Color.TRANSPARENT);connectionRow.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));}body.addView(connectionRow);
        ip=new EditText(this);ip.setText(getPreferences(0).getString("host",""));
        LinearLayout hero=insightCard(body);recordHero=hero;recordHeroBackground=(GradientDrawable)hero.getBackground();if(landscape){hero.setOrientation(0);hero.setPadding(dp(8),dp(6),dp(8),dp(6));}LinearLayout numbers=new LinearLayout(this);numbers.setOrientation(1);if(landscape)hero.addView(numbers,new LinearLayout.LayoutParams(dp(156),-2));else hero.addView(numbers);
        scaleLabel=text("Brew scale",11,MUTED);scaleLabel.setPadding(0,0,0,0);numbers.addView(scaleLabel);LinearLayout reading=new LinearLayout(this);reading.setGravity(Gravity.CENTER_VERTICAL);weight=text("— g",landscape?30:40,FG);weight.setTypeface(serifFace);weight.setSingleLine();weight.setAutoSizeTextTypeUniformWithConfiguration(24,landscape?32:44,1,android.util.TypedValue.COMPLEX_UNIT_SP);reading.addView(weight,new LinearLayout.LayoutParams(0,dp(landscape?40:58),1));shotTimer=text("— s",landscape?23:28,FG);shotTimer.setTypeface(serifFace);shotTimer.setGravity(Gravity.RIGHT);shotTimer.setSingleLine();if(landscape){weight.setPadding(0,0,0,0);shotTimer.setPadding(0,0,0,0);}reading.addView(shotTimer,new LinearLayout.LayoutParams(dp(104),dp(58)));if(landscape){reading.removeView(shotTimer);numbers.addView(reading);numbers.addView(shotTimer,new LinearLayout.LayoutParams(-1,dp(34)));}else numbers.addView(reading);
        recordTarget=text("Recipe target —",11,MUTED);recordTarget.setPadding(0,0,0,dp(3));numbers.addView(recordTarget);recordProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);recordProgress.setMax(1000);recordProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffb59de8));numbers.addView(recordProgress,new LinearLayout.LayoutParams(-1,dp(5)));
        graphArea=new LinearLayout(this);graphArea.setOrientation(1);chart=new RecordChart(this);recordReferenceKey="";((RecordChart)chart).start(recordStart);if(recordStoppedShot!=null)chart.shot(recordStoppedShot);else chart.data(live);FrameLayout graphLayers=new FrameLayout(this);recordAnimation=new InlineReminderView(this);recordAnimation.setAlpha(.16f);recordAnimation.setClickable(false);recordAnimation.setFocusable(false);FrameLayout.LayoutParams animationBox=new FrameLayout.LayoutParams(dp(landscape?108:154),dp(landscape?108:154),Gravity.CENTER);graphLayers.addView(recordAnimation,animationBox);graphLayers.addView(chart,new FrameLayout.LayoutParams(-1,-1));graphArea.addView(graphLayers,new LinearLayout.LayoutParams(-1,dp(landscape?148:206)));if(landscape)hero.addView(graphArea,new LinearLayout.LayoutParams(0,-2,1));else hero.addView(graphArea);
        recordFlow=text("Cup flow — g/s",11,MUTED);recordFlow.setPadding(0,dp(3),0,dp(2));numbers.addView(recordFlow);workflowStatus=text("Connect before grinding or brewing",11,MUTED);workflowStatus.setMaxLines(3);workflowStatus.setPadding(0,dp(3),0,0);if(landscape){numbers.addView(workflowStatus);workflowStatus.setVisibility(View.GONE);}else hero.addView(workflowStatus);
        LinearLayout recipeCard=insightCard(body);recordMini=new LinearLayout(this);recordMini.setOrientation(1);
        recordRecipeName=text(selectedRecipe==null?"Choose a recipe":selectedRecipe.optString("name"),18,FG);recordRecipeName.setTypeface(serifFace);recordRecipeName.setGravity(Gravity.LEFT);recordMini.addView(recordRecipeName,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout recipeInfo=new LinearLayout(this);recipeInfo.setGravity(Gravity.CENTER_VERTICAL);if(selectedRecipe!=null){View thumb=recordThumbnail(selectedRecipe);LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(dp(42),dp(48));image.rightMargin=dp(10);recipeInfo.addView(thumb,image);}LinearLayout words=new LinearLayout(this);words.setOrientation(1);TextView targets=text(RecordPresentation.targets(selectedRecipe),11,MUTED);targets.setPadding(0,0,0,dp(2));words.addView(targets);String age=recordBagAge();if(!age.isEmpty())words.addView(text(age,11,MUTED));recipeInfo.addView(words,new LinearLayout.LayoutParams(0,-2,1));recordMini.addView(recipeInfo,new LinearLayout.LayoutParams(-1,-2));recipeCard.addView(recordMini);
        LinearLayout recipeFooter=new LinearLayout(this);recipeFooter.setGravity(Gravity.CENTER_VERTICAL);recordMatch=text("",11,MUTED);LinearLayout matchSpace=new LinearLayout(this);matchSpace.addView(recordMatch,new LinearLayout.LayoutParams(-1,-2));recipeFooter.addView(matchSpace,new LinearLayout.LayoutParams(0,-2,1));recordChange=button("Change",()->recordPauseFor(this::showRecipes,false));recordChange.setBackgroundColor(Color.TRANSPARENT);recordChange.setPadding(0,0,0,0);recipeFooter.addView(recordChange,new LinearLayout.LayoutParams(dp(76),dp(48)));recipeCard.addView(recipeFooter,new LinearLayout.LayoutParams(-1,-2));
        recipeApplyButton=button("Apply recipe",()->recordPauseFor(()->requestRecipeControls(selectedRecipe),true));greenButton(recipeApplyButton);recipeCard.addView(recipeApplyButton,new LinearLayout.LayoutParams(-1,dp(48)));
        // Existing pending-result controls remain reachable; old success receipts are not live status.
        temperatureLabel=new TextView(this);yieldLabel=new TextView(this);doseLabel=new TextView(this);temperatureWarning=text("",11,0xffe8bb7c);temperatureWarning.setOnClickListener(v->checkTemperatureResult());recipeCard.addView(temperatureWarning);yieldWarning=text("",11,0xffe8bb7c);yieldWarning.setOnClickListener(v->checkYieldResult());recipeCard.addView(yieldWarning);doseWarning=text("",11,0xffe8bb7c);doseWarning.setOnClickListener(v->checkDoseResult());recipeCard.addView(doseWarning);recipeControlWarning=text("",11,0xffe8bb7c);recipeControlWarning.setOnClickListener(v->checkRecipeResult());recipeCard.addView(recipeControlWarning);preInfusionLabel=new TextView(this);homeAppearanceNote=new TextView(this);detail=new TextView(this);updateTemperatureDisplay();updateYieldDisplay();updateDoseDisplay();updateRecipeControlDisplay();
        waterWarning=text("",12,GOLD);
        body.addView(waterWarning);
        lastResult=text("",12,FG);
        body.addView(lastResult);
        if(!new SecureLinkCredentials(this).configured()&&getPreferences(0).getString("host","").isEmpty()){Button pair=button("Scan pairing code",()->qrPairing().scan());pair.setBackgroundColor(Color.TRANSPARENT);body.addView(pair);}
        renderWater();
    }
    private View recordThumbnail(JSONObject recipe){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);try{if(RecipeBackground.mode(recipe).equals("photo")){byte[] b=new RecipeMediaStore(getFilesDir()).read(recipe.optString("record_photo"));android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(b,0,b.length,o);if(o.outWidth>0&&o.outWidth<=1600&&o.outHeight>0&&o.outHeight<=1600){image.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(b,0,b.length));return image;}}}catch(Exception|OutOfMemoryError ignored){}image.setImageResource(R.drawable.nav_cup);image.setImageTintList(android.content.res.ColorStateList.valueOf(GOLD));image.setPadding(dp(8),dp(8),dp(8),dp(8));GradientDrawable color=new GradientDrawable();color.setColor(RecipeBackground.homeColor(recipe));color.setCornerRadius(dp(8));image.setBackground(color);return image;}
    private String recordBagAge(){try{if(selectedRecipe==null)return "";JSONObject bag=new BagStore(getFilesDir()).find(selectedRecipe.optString("bag_id"));if(bag==null)return "";String label=BagTracking.current(bag);return bag.optString("name").equalsIgnoreCase(selectedRecipe.optString("name"))?label.substring(label.indexOf(" · ")+3):label;}catch(Exception e){return "Bag age unavailable";}}
    private void refreshCompact(){if(currentPage!=0||connect==null||graphArea==null)return;boolean fresh=recordFresh(),active=busy.get()||retryPending,locked=RecordPresentation.locked(recordSample,recordState),adjust=RecordPresentation.canAdjust(fresh,recordSample,recordState);connect.setVisibility(active?View.GONE:View.VISIBLE);stop.setVisibility(active?View.VISIBLE:View.GONE);connect.setEnabled(!active&&!temperatureBusy.get()&&!recordAction.active());stop.setEnabled(active);stop.setText(retryPending?"Cancel":"Disconnect");if(recordAction.waiting())status.setText("Pausing recording…");else if(active&&!running.get())status.setText("Reconnecting…");else if(running.get()&&!fresh)status.setText(recordSample==null?"Connecting · waiting for data":"Connected · delayed data");else if(fresh)status.setText("Connected · "+(GrindWorkflow.grinding(recordSample)?"Grinding":recordState==Recorder.State.RECORDING?"Recording":recordState==Recorder.State.TARING?"Taring":recordState==Recorder.State.SETTLING?"Final drips":recordState==Recorder.State.READY?"Ready":"Waiting"));else status.setText("Machine offline");status.setTextColor(fresh?SAGE:active?0xffe8bb7c:0xffef9786);
        weight.setText(fresh&&recordDisplayWeight!=null?String.format(Locale.US,"%.1f g",recordDisplayWeight):"— g");scaleLabel.setText(fresh?recordDisplayLabel:"Brew scale");
        boolean grinder=fresh&&recordDisplayLabel.toLowerCase(Locale.ROOT).contains("grind");
        double elapsed=Double.NaN;
        if(grinder)elapsed=recordGrindElapsed;
        else if(fresh&&recordState==Recorder.State.READY)elapsed=0;
        else if(fresh&&Double.isFinite(recordStart)&&recordSample!=null)elapsed=Math.max(0,recordSample.uptime-recordStart);
        shotTimer.setText(Double.isFinite(elapsed)?String.format(Locale.US,"%.1f s",elapsed):"— s");
        double target=fresh&&recordSample!=null?(grinder?recordSample.registers[4]/10.0:recordSample.registers[23]/10.0):selectedRecipe==null?Double.NaN:selectedRecipe.optDouble("yield_g");recordTarget.setText(Double.isFinite(target)?String.format(Locale.US,(fresh?"Machine ":"Recipe ")+(grinder?"dose":"yield")+" target %.1f g",target):"Choose a recipe target");recordProgress.setProgress(fresh&&recordDisplayWeight!=null&&target>0&&(grinder||recordState==Recorder.State.RECORDING||recordState==Recorder.State.SETTLING)?(int)Math.max(0,Math.min(1000,recordDisplayWeight/target*1000)):0);
        String message=recordAction.waiting()?"Recording is pausing for your settings change":fresh?recordDisplayMessage:running.get()?"Waiting for fresh samples · readouts are held":"Connect before grinding or brewing";if(!fresh&&!recordConnectionMessage.isEmpty()&&!recordAction.waiting())message=recordConnectionMessage;workflowStatus.setText(message);double flow=Double.NaN;if(fresh&&recordState==Recorder.State.RECORDING&&live.length()>0){double[] rates=CupFlow.rates(live,.75);flow=rates[rates.length-1];}recordFlow.setText(Double.isFinite(flow)?String.format(Locale.US,"Cup flow %.2f g/s",flow):"Cup flow — g/s");
        if(chart instanceof RecordChart){RecordChart liveChart=(RecordChart)chart;liveChart.visibleLive(fresh);String key=(selectedRecipe==null?"":selectedRecipe.optString("id"))+"/"+(fresh&&recordSample!=null?RecordPresentation.observed(recordSample)+"/"+recordSample.registers[37]+"/"+Sample.u32(recordSample.preferences,1)+"/"+Sample.u32(recordSample.preferences,5):"offline");if(!key.equals(recordReferenceKey)){recordReferenceKey=key;liveChart.context(selectedRecipe,RecordPresentation.reference(selectedRecipe,fresh?recordSample:null,shots));}}
        renderRecordAnimation(fresh);if(recordHeroBackground!=null){boolean liveBorder=fresh&&recordState==Recorder.State.RECORDING;recordHeroBackground.setStroke(dp(liveBorder?2:1),liveBorder?0xffb59de8:CompanionPalette.LINE);}boolean pending=pendingRecipe()||pendingTemperature()||pendingYield()||pendingDose()||getPreferences(0).contains("pi_request_id");boolean match=fresh&&RecordPresentation.matches(selectedRecipe,recordSample);recordMatch.setVisibility(fresh?View.VISIBLE:View.GONE);recordMatch.setText(!fresh?"":locked?"Recipe locked · pre-infusion "+(recordSample.preferences[0]==1?"on":"off"):selectedRecipe==null?"No recipe selected":(match?"Observed targets match recipe":"Machine targets differ")+" · pre-infusion "+(recordSample.preferences[0]==1?"on":"off"));recordMatch.setTextColor(fresh&&!locked&&match?SAGE:MUTED);recordMini.setAlpha(locked?.6f:1);recordChange.setEnabled(!temperatureBusy.get()&&!recordAction.active()&&(!active||adjust));recipeApplyButton.setVisibility(selectedRecipe!=null&&adjust&&!match&&!pending?View.VISIBLE:View.GONE);recipeApplyButton.setEnabled(adjust&&!temperatureBusy.get()&&!recordAction.active());temperatureWarning.setVisibility(pendingTemperature()?View.VISIBLE:View.GONE);yieldWarning.setVisibility(pendingYield()?View.VISIBLE:View.GONE);doseWarning.setVisibility(pendingDose()?View.VISIBLE:View.GONE);recipeControlWarning.setVisibility(pendingRecipe()?View.VISIBLE:View.GONE);if(lastResult!=null)lastResult.setVisibility(lastResult.getText().length()==0?View.GONE:View.VISIBLE);
    }
    private void recordPauseFor(Runnable action,boolean resume){if(recordAction.active()||temperatureBusy.get()){toast("Finish the current settings operation first");return;}if(!busy.get()&&!running.get()&&!retryPending){if(resume){toast("Connect to check the machine first");return;}action.run();return;}if(!RecordPresentation.canAdjust(recordFresh(),recordSample,recordState)){toast("Wait until the machine is idle with fresh data");return;}LightDialogs.builder(this).setTitle(resume?"Apply recipe?":"Pause recording?").setMessage(resume?"Recording pauses while the recipe is applied, then reconnects after verification. No coffee starts from Companion.":"Recording pauses for this change. Machine-only adjustments do not edit your saved recipe; reconnect afterward.").setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{if(!RecordPresentation.canAdjust(recordFresh(),recordSample,recordState)){toast("Machine is no longer ready; no change sent");return;}disconnect();recordAction.begin(true,SystemClock.elapsedRealtime(),resume);recordPausePoll(action,resume);}).show();}
    private void recordPausePoll(Runnable action,boolean resume){RecordActionGate.Next next=recordAction.poll(SystemClock.elapsedRealtime(),busy.get(),foreground);if(next==RecordActionGate.Next.WAIT){recoveryHandler.postDelayed(()->recordPausePoll(action,resume),100);return;}if(next==RecordActionGate.Next.CANCEL){toast("Recording pause was cancelled; no settings sent");return;}action.run();if(!resume||!recipeOperation.active())recordAction.complete(false,foreground);refreshCompact();}
    private void recordQuickControls(){LightDialogs.builder(this).setTitle("Machine only · saved recipe unchanged").setItems(new String[]{"Current observed targets","Pre-infusion for this shot","Temperature for this shot","Yield for this shot","Dose for this shot"},(d,w)->{if(w==0){LightDialogs.builder(this).setTitle("Observed machine targets").setMessage(recordFresh()?RecordPresentation.observed(recordSample):"Connect and wait for fresh machine data. Saved verification messages are available in Recording details.").setPositiveButton("Close",null).show();return;}if(!recordFresh()){toast("Connect recording and wait for Ready first");return;}Runnable change=w==1?this::readPreinfusion:w==2?this::temperaturePicker:w==3?this::yieldPicker:this::dosePicker;recordPauseFor(change,false);}).show();}
    private void quickStart(){LightDialogs.builder(this).setTitle("Getting started").setMessage("1. Setup: install the invited beta machine package with the Mac setup, then pair this phone using Three dots (⋯) > Machine connection > Scan pairing code. Keep both devices on your home Wi-Fi.\n\n2. Record: tap Connect before grinding. Wait for READY, then make coffee using the machine. Keep Companion visible through the final drips. Your shot saves automatically.\n\n3. Recipes: disconnect recording, then choose a recipe to apply its temperature, yield, grind dose and Home appearance. This uses a separate settings connection; it does not start grinding or brewing. Grind size stays manual. Recipes can leave pre-infusion unchanged or switch it On/Off; pressure and duration stay as set on the machine.\n\n4. If an update stops: check the machine settings, then tap the result message. Checking a result never resends a command.\n\nShots and recipes stay on this phone. Back them up in Settings; forum sync is optional.").setNegativeButton("Close",null).setPositiveButton("Machine connection",(d,w)->machineConnectionSettings()).show();}
    private void recordHelp(){LightDialogs.builder(this).setTitle("Recording & controls").setItems(new String[]{"Getting started","Recording details","Check temperature result","Check yield result","Check dose result","Check recipe result","App tools"},(d,w)->{if(w==0)quickStart();else if(w==2)checkTemperatureResult();else if(w==3)checkYieldResult();else if(w==4)checkDoseResult();else if(w==5)checkRecipeResult();else if(w==6)toolsMenu();else LightDialogs.builder(this).setTitle("Recording details").setMessage("Connect before grinding. Keep Companion visible while recording.\n\n"+(detail==null?"":detail.getText())+"\n\n"+getPreferences(0).getString("temperature_note","No temperature update recorded")+"\n\n"+getPreferences(0).getString("yield_note","No yield update recorded")+"\n\n"+appVersion()).setPositiveButton("Close",null).show();}).show();}
    private void updateYieldDisplay(){if(yieldLabel==null||yieldWarning==null)return;String note=getPreferences(0).getString("yield_note","");int verified=getPreferences(0).getInt("yield_last_verified",-1);yieldLabel.setText(verified>=10&&verified<=99?verified+" g":"— g");yieldLabel.setContentDescription(note.isEmpty()?"No yield update recorded":note);boolean warn=pendingYield()||(!note.isEmpty()&&!note.startsWith("Last yield update:"));yieldWarning.setVisibility(warn?View.VISIBLE:View.GONE);yieldWarning.setText(warn?note+" · Tap to check yield result":"");}
    private void updateTemperatureDisplay(){if(temperatureLabel==null||temperatureWarning==null)return;String note=getPreferences(0).getString("temperature_note","");java.util.regex.Matcher match=java.util.regex.Pattern.compile("Last temperature update: (\\d+)°C").matcher(note);int verified=getPreferences(0).getInt("temperature_last_verified",-1);if(match.find())verified=Integer.parseInt(match.group(1));temperatureLabel.setText(verified>=85&&verified<=96?verified+"°C":"— °C");temperatureLabel.setContentDescription(note.isEmpty()?"No temperature update recorded":note);boolean warning=pendingTemperature()||(!note.isEmpty()&&!note.startsWith("Last temperature update:"));temperatureWarning.setVisibility(warning?View.VISIBLE:View.GONE);temperatureWarning.setText(warning?note+" · Tap to check":"");}
    private LinearLayout insightCard(LinearLayout parent){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(14),dp(10),dp(14),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(CompanionPalette.CARD);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),CompanionPalette.LINE);card.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);parent.addView(card,lp);return card;}
    private void metricRow(LinearLayout parent,String first,String second){LinearLayout row=new LinearLayout(this);for(String value:new String[]{first,second}){TextView cell=text(value,18,FG);cell.setGravity(Gravity.CENTER);row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}parent.addView(row);}
    private void usageSummary(LinearLayout parent){
        LinearLayout card=insightCard(parent);
        try{UsageTotals.Summary t=usage.summary();card.addView(text(t.shotCount+" shots · "+t.grindCount+" measured grinds",14,FG));card.addView(text(t.groundWeight+" coffee ground · tracking details ›",11,MUTED));
            card.setOnClickListener(v->showUsageTotals(t));
        }catch(Exception e){card.addView(text("Totals unavailable",12,MUTED));}
    }
    private void showUsageTotals(UsageTotals.Summary totals){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(18),dp(8),dp(18),dp(12));
        panel.addView(text("Shots recorded",12,MUTED));panel.addView(text(totals.shotCount,24,FG));
        panel.addView(text(totals.shotsSinceUTC.isEmpty()?"No shots recorded yet":"Since "+JournalBrowse.localTime(totals.shotsSinceUTC).replace(" AM","\u00a0AM").replace(" PM","\u00a0PM"),12,MUTED));
        TextView grindTitle=text("Measured grinds",12,MUTED);grindTitle.setPadding(0,dp(20),0,dp(4));panel.addView(grindTitle);panel.addView(text(totals.grindCount,24,FG));
        panel.addView(text(totals.groundWeight.replace(" g","\u00a0g")+" coffee ground",16,FG));
        panel.addView(text(totals.grindsSinceUTC.isEmpty()?"No measured grinds recorded yet":"Since "+JournalBrowse.localTime(totals.grindsSinceUTC).replace(" AM","\u00a0AM").replace(" PM","\u00a0PM"),12,MUTED));
        Button monthly=button("Monthly totals…",()->showMonthlyTotals(YearMonth.now()));
        panel.addView(monthly);
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);LightDialogs.builder(this).setTitle("Recorded totals").setView(scroll).setPositiveButton("Close",null).show();
    }
    private BrandAssignmentController brandAssignments(){return new BrandAssignmentController(this,usage,()->shots,()->!pairingAllowed(),worker);}
    private void showMonthlyTotals(YearMonth month){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18),dp(8),dp(18),dp(12));
        try{
            UsageTotals.Monthly totals=usage.monthly(month,ZoneId.systemDefault());
            panel.addView(text(totals.totals.label(),20,FG));
            for(Map.Entry<String,UsageTotals.Counts> entry:totals.brands.entrySet()){
                String brand=entry.getKey().isEmpty()?"Unknown brand":entry.getKey();
                panel.addView(text(brand+" · "+entry.getValue().label(),14,FG));
            }
        }catch(Exception e){panel.addView(text("Monthly totals unavailable",14,FG));}
        Button assign=button("Assign brand…",()->{});panel.addView(assign);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(panel);
        AlertDialog monthlyDialog=LightDialogs.builder(this).setTitle(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
            .setView(scroll)
            .setNeutralButton("Previous",(dialog,which)->showMonthlyTotals(month.minusMonths(1)))
            .setNegativeButton("Next",(dialog,which)->showMonthlyTotals(month.plusMonths(1)))
            .setPositiveButton("Close",null).create();
        assign.setOnClickListener(v->{monthlyDialog.dismiss();brandAssignments().month(month,()->showMonthlyTotals(month));});
        monthlyDialog.show();
    }
    private void usageMetric(LinearLayout card,String label,String value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);row.setPadding(0,dp(8),0,dp(8));
        TextView name=text(label,14,MUTED);name.setPadding(0,0,dp(12),0);name.setGravity(Gravity.CENTER_VERTICAL);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView number=text(value,20,FG);number.setPadding(0,0,0,0);number.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);number.setSingleLine();number.setMaxWidth(dp(160));number.setAutoSizeTextTypeUniformWithConfiguration(14,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);row.addView(number,new LinearLayout.LayoutParams(dp(140),-2));
        card.addView(row);
    }
    private TextView settingValue(LinearLayout row,String heading){LinearLayout cell=new LinearLayout(this);cell.setOrientation(1);cell.setPadding(dp(4),0,dp(4),0);TextView title=text(heading,12,MUTED);title.setSingleLine();cell.addView(title,new LinearLayout.LayoutParams(-1,dp(Math.round(36*Math.max(1f,getResources().getConfiguration().fontScale)))));TextView value=text("",18,MUTED);value.setTypeface(Typeface.MONOSPACE);value.setSingleLine();value.setAutoSizeTextTypeUniformWithConfiguration(13,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);cell.addView(value,new LinearLayout.LayoutParams(-1,dp(Math.round(48*Math.max(1f,getResources().getConfiguration().fontScale)))));row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));return value;}
    private void updateDoseDisplay(){if(doseLabel==null||doseWarning==null)return;int raw=getPreferences(0).getInt("dose_last_verified",-1);doseLabel.setText(raw>=10&&raw<=309?String.format(Locale.US,"%.1f g",raw/10.0):"— g");String note=getPreferences(0).getString("dose_note","");doseLabel.setContentDescription(note.isEmpty()?"No dose update recorded":note);boolean warn=pendingDose()||(!note.isEmpty()&&!note.startsWith("Last dose update:"));doseWarning.setVisibility(warn?View.VISIBLE:View.GONE);doseWarning.setText(warn?note+" · Tap to check dose result":"");}

    private String fontAsset(String name)throws IOException{try(InputStream in=getAssets().open(name)){return java.util.Base64.getEncoder().encodeToString(readBounded(in,1024*1024));}}
    private void redButton(Button b){GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff3b2420,0xff2b1b18});shape.setCornerRadius(dp(9));shape.setStroke(dp(1),0xffef9786);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44ef9786),shape,null));b.setTextColor(0xffef9786);}
    private void greenButton(Button b){GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff28392d,0xff223027});shape.setCornerRadius(dp(9));shape.setStroke(dp(1),CompanionPalette.SAGE);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44a9c397),shape,null));b.setTextColor(0xffd6e8ca);}
    private android.webkit.WebView detailWeb;
    private void closeDetailPage(){if(detailWeb!=null){detailWeb.removeJavascriptInterface("Companion");detailWeb.stopLoading();detailWeb.destroy();detailWeb=null;}}
    private JSONObject attributedShot(JSONObject shot){try{return usage.annotated(shot);}catch(Exception e){toast("Brand attribution unavailable; original shot retained");return shot;}}
    private JSONObject currentShot(String id,JSONObject fallback){for(int i=0;i<shots.length();i++){JSONObject s=shots.optJSONObject(i);if(s!=null&&id.equals(s.optString("start_utc")))return attributedShot(s);}return attributedShot(fallback);}
    private void showShotDetails(JSONObject original){
        try{
            final JSONObject s=attributedShot(original);
            closeDetailPage();currentPage=3;String id=s.getString("start_utc");String template;byte[] font;
            try(InputStream in=getAssets().open("shot-detail.html")){template=new String(readBounded(in,1024*1024),StandardCharsets.UTF_8);}try(InputStream in=getAssets().open("detail-sans.ttf")){font=readBounded(in,1024*1024);}
            String html=ShotDetailPage.render(template,java.util.Base64.getEncoder().encodeToString(font),fontAsset("detail-serif.ttf"),fontAsset("detail-serif-bold.ttf"),ShotDetailPage.data(s,shots));
            android.webkit.WebView web=new android.webkit.WebView(this);detailWeb=web;web.setBackgroundColor(CompanionPalette.BACKGROUND);
            android.webkit.WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);settings.setTextZoom(Math.round(getResources().getConfiguration().fontScale*100));
            web.setWebViewClient(new android.webkit.WebViewClient(){@Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest request){return true;}@Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,String url){return true;}});
            web.addJavascriptInterface(new Object(){
                @android.webkit.JavascriptInterface public void action(String action){runOnUiThread(()->{if(detailWeb!=web||isFinishing())return;JSONObject shot=currentShot(id,s);switch(action){case "record":show(false);break;case "shots":case "back":show(true);break;case "recipes":showRecipes();break;case "tools":toolsMenu();break;case "replay":openReplay(shot);break;case "compare":chooseComparison(shot);break;case "favourite":if(busy.get()){toast("Disconnect before changing favourites");return;}worker.execute(()->{try{saveFavourite(id);boolean value=currentShot(id,s).optBoolean("favourite");runOnUiThread(()->{if(detailWeb==web)web.evaluateJavascript("window.favoriteSaved("+value+")",null);});}catch(Exception e){toast("Favourite could not be saved");}});break;case "share":visualizer().share(shot);break;case "beanconqueror":sendBeanconqueror(shot);break;case "more":LightDialogs.builder(MainActivity.this).setTitle("Shot options").setItems(new String[]{"Save settings as recipe","Diagnostic graph","Brew screen","Delete this shot","Visualizer","Compare recent shots for this recipe","Send to Beanconqueror","Assign brand…"},(d,w)->{if(w==0)recipeFromShot(shot);else if(w==1)showDiagnostic(shot);else if(w==2){JSONObject capture=shot.optJSONObject("screen_capture");if(capture!=null&&capture.has("png_base64"))showCapture(capture);else toast("No brew-screen capture for this shot");}else if(w==3)confirmDelete(id);else if(w==4)visualizer().share(shot);else if(w==5)compareRecentShots(shot.optJSONObject("recipe"));else if(w==6)sendBeanconqueror(shot);else brandAssignments().shot(shot,()->showShotDetails(shot));}).show();break;default:break;}});}
                @android.webkit.JavascriptInterface public void taste(String taste){runOnUiThread(()->{
                    if(detailWeb!=web||isFinishing())return;
                    if(busy.get()||!java.util.Arrays.asList("Sour","Balanced","Bitter","Liked","Sharp","Harsh/dry").contains(taste)){web.evaluateJavascript("window.tasteSaveFailed()",null);toast("Disconnect recording before saving taste");return;}
                    worker.execute(()->{try{saveTasteOnly(id,taste);runOnUiThread(()->{if(detailWeb==web)web.evaluateJavascript("window.tasteSaved("+JSONObject.quote(taste)+")",null);});}catch(Exception e){runOnUiThread(()->{if(detailWeb==web)web.evaluateJavascript("window.tasteSaveFailed()",null);});toast("Taste could not be saved");}});
                });}
                @android.webkit.JavascriptInterface public void save(String taste,String note){runOnUiThread(()->{if(detailWeb!=web||isFinishing())return;if(busy.get()){toast("Disconnect before editing notes");return;}if(note==null||note.length()>8000||taste==null||taste.length()>256){toast("Invalid tasting note");return;}worker.execute(()->{try{saveTaste(id,note,taste);runOnUiThread(()->{if(detailWeb==web)web.evaluateJavascript("window.noteSaved('Tasting note saved')",null);});}catch(Exception e){toast("Could not save tasting note");}});});}
            },"Companion");
            FrameLayout host=new FrameLayout(this);host.setBackgroundColor(CompanionPalette.BACKGROUND);host.addView(web,new FrameLayout.LayoutParams(-1,-1));host.setOnApplyWindowInsetsListener((v,insets)->{host.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});setContentView(host);web.loadDataWithBaseURL("https://companion.invalid/",html,"text/html","UTF-8",null);
        }catch(Exception e){toast("Shot details could not open: "+e.getMessage());show(true);}
    }
    private synchronized void saveTasteOnly(String id,String taste)throws Exception{saveTaste(id,currentShot(id,new JSONObject()).optString("notes"),taste);}
    private synchronized void saveTaste(String id,String note,String taste)throws Exception{JSONArray next=new JSONArray(shots.toString());for(int i=0;i<next.length();i++){JSONObject s=next.getJSONObject(i);if(id.equals(s.optString("start_utc"))){s.put("notes",note);s.put("taste",taste);}}store.save(next);shots=next;}

    private void journal(){
        usageSummary(body);EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(FG);search.setHintTextColor(MUTED);search.setHint("Search coffee, notes or date");search.setText(journalQuery);body.addView(search);
        actions(body,button(JournalBrowse.FILTERS[journalFilter]+" ▾",this::journalFilters),button(journalOldest?"Oldest ↑":"Newest ↓",()->{journalOldest=!journalOldest;show(true);}),button("⋯",()->LightDialogs.builder(this).setTitle("Journal").setItems(new String[]{"Forum sync","Clear search and filter"},(d,w)->{if(w==0)showForumPanel();else{journalQuery="";journalFilter=0;show(true);}}).show()));
        LinearLayout results=new LinearLayout(this);results.setOrientation(1);body.addView(results);renderJournal(results);watchSearch(search,()->{journalQuery=search.getText().toString().trim();journalLimit=30;renderJournal(results);});
    }
    private void watchSearch(EditText field,Runnable changed){field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int a,int b,int c){changed.run();}public void afterTextChanged(android.text.Editable e){}});}
    private void renderJournal(LinearLayout results){results.removeAllViews();JSONArray visible=JournalBrowse.select(shots,journalQuery,journalFilter,journalOldest);
        if(visible.length()==0)results.addView(text(shots.length()==0?"Your saved shots will appear here after recording.":"No matching shots. Try another search or clear the filter.",15,MUTED));
        for(int n=0;n<Math.min(visible.length(),journalLimit);n++){JSONObject s=visible.optJSONObject(n);LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(4),dp(12),dp(4));GradientDrawable bg=new GradientDrawable();bg.setColor(CompanionPalette.CARD);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),CompanionPalette.LINE);card.setBackground(bg);LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.topMargin=dp(10);results.addView(card,margin);
            String stamp=JournalBrowse.localTime(s.optString("start_utc"));
            TextView coffeeName=text((s.optBoolean("favourite")?"★ ":"")+JournalBrowse.name(s),16,FG);coffeeName.setMaxLines(2);coffeeName.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(coffeeName);
            card.addView(text(stamp+" · "+JournalBrowse.quality(s),11,JournalBrowse.review(s)?0xffe8bb7c:MUTED));
            card.addView(text((s.has("settled_yield_g")?String.format(Locale.US,"%.1f g out",s.optDouble("settled_yield_g")):"Yield unavailable")+" · "+(s.has("duration_estimate_s")?String.format(Locale.US,"%.1f s",s.optDouble("duration_estimate_s")):"Partial capture"),20,FG));
            card.addView(text(JournalBrowse.doseAndRatio(s),12,MUTED));

            String bagText=BagTracking.label(s.optJSONObject("bag"));if(!bagText.isEmpty())card.addView(text(bagText,12,MUTED));
            String assessment=ShotAssessment.summary(s);if(!assessment.isEmpty())card.addView(text(assessment,12,FG));
            ShotSparkline mini=new ShotSparkline(this,s);card.addView(mini,new LinearLayout.LayoutParams(-1,dp(30)));
            card.setOnClickListener(v->showShotDetails(s));card.setContentDescription("Open shot: "+JournalBrowse.name(s));
            LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);TextView taste=text(s.optString("taste","").isEmpty()?"Tap to review taste":s.optString("taste"),12,SAGE);bottom.addView(taste,new LinearLayout.LayoutParams(0,-2,1));Button quickPlay=button("▶",()->openReplay(s));quickPlay.setContentDescription("Replay shot");quickPlay.setBackgroundColor(Color.TRANSPARENT);bottom.addView(quickPlay,new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(bottom);

        }
        if(visible.length()>journalLimit)actions(results,button("Show 30 more",()->{journalLimit+=30;renderJournal(results);}));
    }
    private void showForumPanel(){LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);LinearLayout prior=body;body=panel;forumControls();body=prior;ScrollView scroll=new ScrollView(this);scroll.addView(panel);LightDialogs.builder(this).setTitle("Private forum sync").setView(scroll).setPositiveButton("Close",null).show();}
    private void journalFilters(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(16),dp(12),dp(16),dp(12));panel.setBackgroundColor(BG);
        panel.addView(text("Needs review includes incomplete recordings and data gaps. Short events under 15 seconds are kept separately.",12,MUTED));
        AlertDialog dialog=LightDialogs.builder(this).setTitle("Show shots").setView(panel).create();
        for(int i=0;i<JournalBrowse.FILTERS.length;i++){final int chosen=i;panel.addView(button((i==journalFilter?"✓ ":"")+JournalBrowse.FILTERS[i],()->{journalFilter=chosen;journalLimit=30;dialog.dismiss();show(true);}));}
        panel.addView(button("Cancel",dialog::dismiss));dialog.show();
    }
    private void toggleFavourite(String id){
        if(busy.get()){toast("Disconnect before changing favourites");return;}
        worker.execute(()->{try{saveFavourite(id);runOnUiThread(()->show(true));}catch(Exception e){toast("Favourite could not be saved");}});
    }
    private synchronized void saveFavourite(String id)throws Exception{
        JSONArray next=new JSONArray(shots.toString());for(int i=0;i<next.length();i++){JSONObject s=next.getJSONObject(i);if(id.equals(s.optString("start_utc")))s.put("favourite",!s.optBoolean("favourite"));}store.save(next);shots=next;
    }
    private void compareRecentShots(JSONObject recipe) {
        String id = recipe == null ? "" : recipe.optString("id");
        if (id.isEmpty()) {
            toast("This shot has no saved recipe");
            return;
        }
        JSONArray ordered = JournalBrowse.select(shots, "", 0, false);
        JSONArray recent = new JSONArray();
        for (int index = 0; index < ordered.length() && recent.length() < 10; index++) {
            JSONObject shot = ordered.optJSONObject(index);
            JSONObject savedRecipe = shot.optJSONObject("recipe");
            if (!shot.optBoolean("demo") && savedRecipe != null && id.equals(savedRecipe.optString("id"))) {
                recent.put(shot);
            }
        }
        if (recent.length() == 0) {
            toast("No saved shots for this recipe");
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(4), dp(16), dp(12));
        scroll.addView(panel);
        RecentShotChart overlay = new RecentShotChart(this, recent);
        panel.addView(overlay, new LinearLayout.LayoutParams(-1, dp(218)));
        panel.addView(text("Newest first · Tap a shot to isolate its curve", 12, 0xff59635d));
        for (int index = 0; index < recent.length(); index++) {
            JSONObject shot = recent.optJSONObject(index);
            JSONObject grind = shot.optJSONObject("grind_workflow");
            double weight = grind == null ? Double.NaN : grind.optDouble("settled_dose_g");
            if (grind != null && grind.optBoolean("cup_movement_detected")) weight = Double.NaN;
            double duration = shot.optDouble("duration_estimate_s");
            String brewTime = Double.isFinite(duration)
                ? String.format(Locale.US, "%.1f s", duration) : "Brew time unavailable";
            String dose = Double.isFinite(weight) && weight > 0
                ? String.format(Locale.US, "%.2f g measured grind", weight) : "Grind weight unavailable";
            String quality = JournalBrowse.review(shot) ? " · " + JournalBrowse.quality(shot) : "";
            String missing = overlay.hasCurve(index) ? "" : " · Curve unavailable";
            TextView row = text((index + 1) + "   " + brewTime + "  ·  " + dose + "\n"
                + JournalBrowse.localTime(shot.optString("start_utc")) + quality + missing,
                13, 0xff20261e);
            android.graphics.drawable.GradientDrawable swatch = new android.graphics.drawable.GradientDrawable();
            swatch.setColor(RecentShotChart.color(index));
            swatch.setCornerRadius(dp(2));
            swatch.setBounds(0, 0, dp(10), dp(10));
            row.setCompoundDrawablesRelative(swatch, null, null, null);
            row.setCompoundDrawablePadding(dp(8));
            row.setMinHeight(dp(48));
            final int traceIndex = index;
            if (overlay.hasCurve(index)) row.setOnClickListener(view -> overlay.toggleTrace(traceIndex));
            panel.addView(row);
        }
        LightDialogs.builder(this).setTitle("Recent shots · " + recipe.optString("name"))
            .setView(scroll).setPositiveButton("Done", null).show();
    }

    private void chooseComparison(JSONObject shot){
        try{new ReplayTimeline(shot);}catch(Exception e){toast(e.getMessage());return;}
        JSONArray choices=ShotAssessment.candidates(JournalBrowse.select(shots,"",0,true),shot);if(choices.length()==0){toast("Record another shot to compare");return;}
        String[] labels=new String[choices.length()];for(int i=0;i<labels.length;i++){
            JSONObject s=choices.optJSONObject(i);JSONObject r=s.optJSONObject("recipe");
            labels[i]=(s.optBoolean("favourite")?"★ ":"")+(r==null?"Shot":r.optString("name"))+" · "+s.optString("start_utc")+"\n"+ShotAssessment.result(s);
        }
        LightDialogs.builder(this).setTitle("Compare with · favourites first").setItems(labels,(d,n)->{try{if(replay!=null)replay.dismiss();replay=new ShotReplay(this,shot,choices.optJSONObject(n));replay.show();}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("Cancel",null).show();
    }
    private void openReplay(JSONObject s){try{if(replay!=null)replay.dismiss();replay=new ShotReplay(this,s);replay.show();}catch(Exception e){toast(e.getMessage());}}
    private void showRecipes(){
        if(blockDuringRecipe())return;
        if(busy.get()){toast("Disconnect before managing recipes");return;}showPage(2);
        body.addView(text("Apply dose, yield, temperature and Home appearance. Optional pre-infusion On/Off; grind size stays manual.",12,MUTED));
        Button add=button("New recipe",()->editRecipe(null));greenButton(add);actions(body,add,button("⋯",()->LightDialogs.builder(this).setTitle("Recipe library").setItems(new String[]{"Import recipes","Export recipes","How recipes work","Home appearance"},(d,w)->{if(w==0)recipeFile(false);else if(w==1)recipeFile(true);else if(w==3)homeAppearanceDialog();else quickStart();}).show()));
        EditText search=new EditText(this);search.setSingleLine();search.setTextColor(FG);search.setHintTextColor(MUTED);search.setHint("Search recipes");search.setText(recipeQuery);body.addView(search);
        LinearLayout results=new LinearLayout(this);results.setOrientation(1);body.addView(results);renderRecipes(results);watchSearch(search,()->{recipeQuery=search.getText().toString().trim();renderRecipes(results);});
    }
    private void applyRecipe(JSONObject r){selectedRecipe=r;getPreferences(0).edit().putString("selected_recipe",r.optString("id")).apply();show(false);requestRecipeControls(r);}
    private void renderRecipes(LinearLayout results){results.removeAllViews();
        try{JSONArray saved=recipes.load();List<JSONObject> list=new ArrayList<>();for(int i=0;i<saved.length();i++){JSONObject r=saved.getJSONObject(i);if((r.optString("name")+" "+r.optString("notes")).toLowerCase(Locale.ROOT).contains(recipeQuery.toLowerCase(Locale.ROOT)))list.add(r);}
            list.sort((a,b)->Boolean.compare(selectedRecipe!=null&&selectedRecipe.optString("id").equals(b.optString("id")),selectedRecipe!=null&&selectedRecipe.optString("id").equals(a.optString("id"))));
            if(list.isEmpty())results.addView(text("No matching recipes. Create one or clear the search.",13,MUTED));
            for(JSONObject r:list){boolean selected=selectedRecipe!=null&&selectedRecipe.optString("id").equals(r.optString("id"));LinearLayout card=insightCard(results);if(selected)card.addView(text("SELECTED",10,SAGE));
                LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(r.optString("name"),18,FG);name.setTypeface(serifFace);title.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button more=button("⋯",()->recipeOptions(r));more.setContentDescription("Options for "+r.optString("name"));more.setBackgroundColor(Color.TRANSPARENT);title.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(title);card.addView(text(RecipeStore.summary(r),12,MUTED));String bagText=recipeBagLabel(r);if(!bagText.isEmpty())card.addView(text(bagText,12,SAGE));Button apply=button(selected?"Apply recipe":"Use recipe",()->applyRecipe(r));greenButton(apply);card.addView(apply,new LinearLayout.LayoutParams(-1,dp(48)));
            }
        }catch(Exception e){toast("Cannot read recipes: "+e.getMessage());}
    }
    private void saveRecordBackground(String id,String mode,String photo)throws Exception{
        JSONArray all=recipes.load(),next=new JSONArray();JSONObject updated=null;
        for(int i=0;i<all.length();i++){JSONObject r=all.getJSONObject(i);if(id.equals(r.optString("id"))){r.put("record_background",mode);if(photo!=null)r.put("record_photo",photo);r=RecipeStore.validate(r);updated=r;}next.put(r);}
        if(updated==null)throw new IOException("Recipe no longer exists");recipes.save(next);if(selectedRecipe!=null&&id.equals(selectedRecipe.optString("id")))selectedRecipe=updated;showRecipes();toast("Record background saved");
    }
    private void recordPhotoResult(int result,Intent data){
        String id=getPreferences(0).getString("pending_recipe_photo","");getPreferences(0).edit().remove("pending_recipe_photo").apply();
        if(result!=RESULT_OK||data==null||data.getData()==null||id.isEmpty())return;
        if(!pairingAllowed()){toast("Background unchanged. Disconnect recording before choosing a photo");return;}
        final android.net.Uri uri=data.getData();worker.execute(()->{try{byte[] bytes=RecordCardAppearance.photo(this,uri);String photo=new RecipeMediaStore(getFilesDir()).put(bytes);runOnUiThread(()->{try{if(!pairingAllowed())throw new IOException("Finish the current task first");saveRecordBackground(id,"photo",photo);}catch(Exception e){toast("Background unchanged: "+e.getMessage());}});}catch(Exception|OutOfMemoryError e){toast("Photo could not be used; previous background kept");}});
    }
    private void recipeOptions(JSONObject r){LightDialogs.builder(this).setTitle(r.optString("name")).setItems(new String[]{"Edit","Duplicate","Deselect","Delete…","Compare recent shots for this recipe"},(d,w)->{if(w==0)editRecipe(r);else if(w==1)duplicateRecipe(r);else if(w==4)compareRecentShots(r);else if(w==2){if(selectedRecipe!=null&&selectedRecipe.optString("id").equals(r.optString("id"))){selectedRecipe=null;getPreferences(0).edit().remove("selected_recipe").apply();showRecipes();toast("Recipe deselected; machine settings unchanged");}}else LightDialogs.builder(this).setTitle("Delete recipe?").setMessage("Saved shots keep their attached recipe. Remove this library recipe?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{try{JSONArray next=new JSONArray(),all=recipes.load();for(int i=0;i<all.length();i++)if(!r.optString("id").equals(all.getJSONObject(i).optString("id")))next.put(all.getJSONObject(i));recipes.save(next);if(selectedRecipe!=null&&selectedRecipe.optString("id").equals(r.optString("id"))){selectedRecipe=null;getPreferences(0).edit().remove("selected_recipe").apply();}showRecipes();}catch(Exception e){toast("Recipe could not be removed");}}).show();}).show();}
    private void duplicateRecipe(JSONObject recipe){try{JSONObject copy=new JSONObject(recipe.toString());copy.put("id",UUID.randomUUID().toString());String name=copy.optString("name");copy.put("name",name.substring(0,Math.min(name.length(),73))+" (copy)");editRecipe(copy);}catch(Exception e){toast("Could not duplicate recipe");}}
    private void recipeFile(boolean export){Intent intent=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);if(export)intent.putExtra(Intent.EXTRA_TITLE,"RP-Companion-Recipes-"+java.time.LocalDate.now()+".json");startActivityForResult(intent,export?5:4);}
    private void recipeFromShot(JSONObject shot){try{editRecipe(new JSONObject().put("id",UUID.randomUUID().toString()).put("name","Recipe from shot").put("dose_g",shot.optDouble("dose_target_g",18)).put("yield_g",shot.optDouble("yield_target_g",shot.optDouble("settled_yield_g",36))).put("brew_c",shot.optDouble("brew_target_c",93)).put("grind",shot.optInt("grind_setting",13)).put("time_s",shot.has("duration_estimate_s")?shot.getDouble("duration_estimate_s"):null).put("notes",""));}catch(Exception e){toast(e.getMessage());}}
    private void editRecipe(JSONObject original){
        if(!pairingAllowed()){toast("Disconnect recording and finish the current task before editing recipes");return;}
        try{new RecipeEditor(this,original,(r,bag,photo)->{
            if(!pairingAllowed())throw new IOException("Finish the current task before saving");
            if(bag!=null)new BagStore(getFilesDir()).put(bag);
            JSONArray all=recipes.load(),next=new JSONArray();boolean found=false;for(int i=0;i<all.length();i++){JSONObject old=all.getJSONObject(i);if(old.getString("id").equals(r.getString("id"))){next.put(r);found=true;}else next.put(old);}if(!found)next.put(r);recipes.save(next);if(selectedRecipe!=null&&selectedRecipe.optString("id").equals(r.getString("id")))selectedRecipe=r;showRecipes();if(photo){getPreferences(0).edit().putString("pending_recipe_photo",r.getString("id")).apply();startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),8043);}
        }).show();}catch(Exception e){toast("Recipe editor unavailable: "+e.getMessage());}
    }
    private String recipeBagLabel(JSONObject r){try{JSONObject bag=new BagStore(getFilesDir()).find(r.optString("bag_id"));return bag==null?"":BagTracking.current(bag);}catch(Exception e){return "Bag unavailable";}}
    private void showDiagnostic(JSONObject shot){LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(14),dp(12),dp(14),dp(12));panel.setBackgroundColor(BG);panel.addView(text("Full saved recording, including final drips and settling. P1 is electrical power, not pressure.",13,MUTED));JSONArray rows=shot.optJSONArray("samples");if(rows!=null&&rows.length()>0){JSONArray regs=rows.optJSONObject(rows.length()-1).optJSONArray("registers");if(regs!=null&&regs.length()>57)panel.addView(text("Bean-chamber interlock: "+((regs.optInt(33)&0x100)!=0?"reported seated":"reported open")+" · register 57: "+regs.optInt(57),12,MUTED));}ShotChart graph=new ShotChart(this);graph.diagnostic(shot);panel.addView(graph,new LinearLayout.LayoutParams(-1,dp(360)));LightDialogs.builder(this).setTitle("Diagnostic graph").setView(panel).setPositiveButton("Close",null).show();}
    private boolean pendingTemperature(){return getPreferences(0).contains("temperature_request_id");}
    private void temperatureNote(String note){
        java.util.regex.Matcher previous=java.util.regex.Pattern.compile("Last temperature update: (\\d+)°C").matcher(getPreferences(0).getString("temperature_note",""));if(previous.find())getPreferences(0).edit().putInt("temperature_last_verified",Integer.parseInt(previous.group(1))).apply();
        getPreferences(0).edit().putString("temperature_note",note).apply();
        runOnUiThread(()->{updateTemperatureDisplay();});
    }
    private void temperaturePicker(){
        if(blockDuringRecipe())return;
        NumberPicker picker=new NumberPicker(LightDialogs.context(this));picker.setMinValue(85);picker.setMaxValue(96);picker.setWrapSelectorWheel(false);int initial=93;
        if(selectedRecipe!=null)try{initial=TemperatureRequest.celsius(selectedRecipe.optDouble("brew_c",Double.NaN));}catch(Exception ignored){}
        picker.setValue(initial);
        LightDialogs.builder(this).setTitle("Brew temperature (°C)").setMessage("Dose and yield stay unchanged. Leave the machine idle and keep its touchscreen clear during the update.").setView(picker).setNegativeButton("Cancel",null).setPositiveButton("Set temperature",(d,w)->requestTemperature(picker.getValue())).show();
    }
    private void requestTemperature(double requested){
        if(getPreferences(0).contains("pi_request_id")){toast("Check the pending pre-infusion result first");readPreinfusion();return;}
        if(pendingRecipe()){checkRecipeResult();return;}
        if(pendingDose()){checkDoseResult();return;}

        final int celsius;try{celsius=TemperatureRequest.celsius(requested);}catch(Exception e){temperatureNote("Recipe selected; temperature not applied: "+e.getMessage());return;}
        if(pendingYield()){toast("Check the pending yield result first");checkYieldResult();return;}
        if(pendingTemperature()){temperatureNote("Previous temperature result needs checking before another update");checkTemperatureResult();return;}
        if(!new SecureLinkCredentials(this).configured()){temperatureNote("Temperature not applied · pair Secure Link and start the temperature service");return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current settings operation to finish");return;}
        temperatureNote("Checking temperature control…");
        temperatureWorker.execute(()->{
            TemperatureControlTransport transport=null;boolean submitted=false;
            try{
                SecurePairing pairing=new SecureLinkCredentials(this).load();transport=new TemperatureControlTransport(pairing.pin);temperatureConnection=transport;
                if(!temperatureAllowed)throw new IOException("App paused before update");
                transport.connect(pairing.host,9444,pairing.token);JSONObject capabilities=transport.capabilities();
                if(!temperatureAllowed)throw new IOException("App paused before update");
                String id=UUID.randomUUID().toString(),epoch=capabilities.getString("epoch");
                if(!getPreferences(0).edit().putString("temperature_request_id",id).putString("temperature_epoch",epoch).putString("temperature_host",pairing.host).putString("temperature_pin",pairing.pin).putInt("temperature_target",celsius).commit())throw new IOException("Could not save pending temperature request");
                submitted=true;temperatureNote("Setting brew temperature to "+celsius+"°C…");
                finishTemperature(transport.setTemperature(id,epoch,celsius),celsius);
            }catch(Exception e){temperatureNote(submitted?"Temperature result not confirmed · check temperature result before trying again":"Temperature not applied · control service unavailable or connection failed");}
            finally{if(transport!=null)transport.close();temperatureConnection=null;temperatureBusy.set(false);}
        });
    }
    private void finishTemperature(JSONObject result,int target)throws Exception{
        String outcome=result.getString("status");
        if(outcome.equals("verified")||outcome.equals("unchanged")){
            if(result.optInt("celsius",-1)!=target)throw new IOException("Temperature result differs from pending target");
            if(!getPreferences(0).edit().remove("temperature_request_id").remove("temperature_epoch").remove("temperature_host").remove("temperature_pin").remove("temperature_target").commit())throw new IOException("Could not retain temperature result");
            getPreferences(0).edit().putInt("temperature_last_verified",target).apply();
            temperatureNote("Last temperature update: "+target+"°C · "+(outcome.equals("unchanged")?"already set":"verified")+" · dose and yield unchanged");toast("Temperature "+target+"°C · "+outcome);
        }else if(outcome.equals("busy")||outcome.equals("rejected")){
            if(!getPreferences(0).edit().remove("temperature_request_id").remove("temperature_epoch").remove("temperature_host").remove("temperature_pin").remove("temperature_target").commit())throw new IOException("Could not save declined request");
            temperatureNote(outcome.equals("busy")?"Temperature not applied · another update is running":"Temperature request declined · machine settings unchanged");
        }else{
            temperatureNote(outcome.equals("pending")?"Temperature update still running · check again shortly":"Temperature result not verified · check the machine before another update");
            if(!outcome.equals("pending"))runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check machine settings").setMessage("The last update was not fully verified. Check the displayed temperature, dose and yield before starting another update. The command will not be retried automatically.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->{getPreferences(0).edit().remove("temperature_request_id").remove("temperature_epoch").remove("temperature_host").remove("temperature_pin").remove("temperature_target").apply();temperatureNote("Previous result dismissed after machine check");}).show();});
        }
    }
    private void checkTemperatureResult(){
        if(!pendingTemperature()){LightDialogs.builder(this).setTitle("Temperature result").setMessage("This checks the previous request; it never resends a temperature command.\n\n"+getPreferences(0).getString("temperature_note","No temperature update has been requested.")).setPositiveButton("Close",null).show();return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current settings operation to finish");return;}
        temperatureWorker.execute(()->{
            TemperatureControlTransport transport=null;
            try{
                SecurePairing pairing=new SecureLinkCredentials(this).load();
                if(!pairing.host.equals(getPreferences(0).getString("temperature_host",""))||!pairing.pin.equals(getPreferences(0).getString("temperature_pin","")))throw new IOException("Pending request belongs to another pairing");
                transport=new TemperatureControlTransport(pairing.pin);temperatureConnection=transport;
                if(!temperatureAllowed)throw new IOException("App paused");transport.connect(pairing.host,9444,pairing.token);transport.capabilities();
                if(!temperatureAllowed)throw new IOException("App paused");
                finishTemperature(transport.result(getPreferences(0).getString("temperature_request_id",""),getPreferences(0).getString("temperature_epoch","")),getPreferences(0).getInt("temperature_target",-1));
            }catch(Exception e){temperatureNote("Last temperature result unavailable · check the machine; no command resent");runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check machine settings").setMessage("The previous result is unavailable. After checking the machine’s temperature, dose and yield, you can clear this pending result. No command will be resent.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->{getPreferences(0).edit().remove("temperature_request_id").remove("temperature_epoch").remove("temperature_host").remove("temperature_pin").remove("temperature_target").apply();temperatureNote("Previous result dismissed after machine check");}).show();});}
            finally{if(transport!=null)transport.close();temperatureConnection=null;temperatureBusy.set(false);}
        });
    }
    private boolean pendingYield(){return getPreferences(0).contains("yield_request_id");}
    private void yieldNote(String note){
        java.util.regex.Matcher previous=java.util.regex.Pattern.compile("Last yield update: (\\d+) g").matcher(getPreferences(0).getString("yield_note",""));if(previous.find())getPreferences(0).edit().putInt("yield_last_verified",Integer.parseInt(previous.group(1))).apply();
        getPreferences(0).edit().putString("yield_note",note).apply();
        runOnUiThread(()->{updateYieldDisplay();});
    }
    private void yieldPicker(){
        if(blockDuringRecipe())return;
        NumberPicker picker=new NumberPicker(LightDialogs.context(this));picker.setMinValue(10);picker.setMaxValue(99);picker.setWrapSelectorWheel(false);int initial=getPreferences(0).getInt("yield_last_verified",36);
        if(selectedRecipe!=null)try{initial=YieldRequest.grams(selectedRecipe.optDouble("yield_g",Double.NaN));}catch(Exception ignored){}
        picker.setValue(initial);
        LightDialogs.builder(this).setTitle("Brew yield (g)").setMessage("Temperature and dose stay unchanged. Leave the machine idle and keep its touchscreen clear during the update.").setView(picker).setNegativeButton("Cancel",null).setPositiveButton("Set yield",(d,w)->requestYield(picker.getValue())).show();
    }
    private void requestYield(double requested){
        if(getPreferences(0).contains("pi_request_id")){toast("Check the pending pre-infusion result first");readPreinfusion();return;}
        if(pendingRecipe()){checkRecipeResult();return;}
        if(pendingDose()){checkDoseResult();return;}

        final int grams;try{grams=YieldRequest.grams(requested);}catch(Exception e){yieldNote("Yield not applied: "+e.getMessage());return;}
        if(pendingTemperature()){toast("Check the pending temperature result first");checkTemperatureResult();return;}
        if(pendingYield()){yieldNote("Previous yield result needs checking before another update");checkYieldResult();return;}
        if(!new SecureLinkCredentials(this).configured()){yieldNote("Yield not applied · pair Secure Link and start the yield service");return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current settings operation to finish");return;}
        yieldNote("Checking yield control…");
        temperatureWorker.execute(()->{
            YieldControlTransport transport=null;boolean submitted=false;
            try{
                SecurePairing pairing=new SecureLinkCredentials(this).load();transport=new YieldControlTransport(pairing.pin);yieldConnection=transport;
                if(!temperatureAllowed)throw new IOException("App paused before update");
                transport.connect(pairing.host,9444,pairing.token);JSONObject capabilities=transport.capabilities();
                if(!temperatureAllowed)throw new IOException("App paused before update");
                String id=UUID.randomUUID().toString(),epoch=capabilities.getString("epoch");
                if(!getPreferences(0).edit().putString("yield_request_id",id).putString("yield_epoch",epoch).putString("yield_host",pairing.host).putString("yield_pin",pairing.pin).putInt("yield_target",grams).commit())throw new IOException("Could not save pending yield request");
                submitted=true;yieldNote("Setting brew yield to "+grams+" g…");
                finishYield(transport.setYield(id,epoch,grams),grams);
            }catch(Exception e){yieldNote(submitted?"Yield result not confirmed · check yield result before trying again":"Yield not applied · control service unavailable or connection failed");}
            finally{if(transport!=null)transport.close();yieldConnection=null;temperatureBusy.set(false);}
        });
    }
    private void finishYield(JSONObject result,int target)throws Exception{
        String outcome=result.getString("status");
        if(outcome.equals("verified")||outcome.equals("unchanged")){
            if(result.optInt("grams",-1)!=target)throw new IOException("Yield result differs from pending target");
            if(!getPreferences(0).edit().remove("yield_request_id").remove("yield_epoch").remove("yield_host").remove("yield_pin").remove("yield_target").commit())throw new IOException("Could not retain yield result");
            getPreferences(0).edit().putInt("yield_last_verified",target).apply();
            yieldNote("Last yield update: "+target+" g · "+(outcome.equals("unchanged")?"already set":"verified")+" · temperature and dose unchanged");toast("Yield "+target+" g · "+outcome);
        }else if(outcome.equals("busy")||outcome.equals("rejected")){
            if(!getPreferences(0).edit().remove("yield_request_id").remove("yield_epoch").remove("yield_host").remove("yield_pin").remove("yield_target").commit())throw new IOException("Could not save declined request");
            yieldNote(outcome.equals("busy")?"Yield not applied · another update is running":"Yield request declined · machine settings unchanged");
        }else{
            yieldNote(outcome.equals("pending")?"Yield update still running · check again shortly":"Yield result not verified · check the machine before another update");
            if(!outcome.equals("pending"))runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check machine settings").setMessage("The last update was not fully verified. Check the displayed yield, dose and temperature before starting another update. The command will not be retried automatically.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->{getPreferences(0).edit().remove("yield_request_id").remove("yield_epoch").remove("yield_host").remove("yield_pin").remove("yield_target").apply();yieldNote("Previous result dismissed after machine check");}).show();});
        }
    }
    private void checkYieldResult(){
        if(!pendingYield()){LightDialogs.builder(this).setTitle("Yield result").setMessage("This checks the previous request; it never resends a yield command.\n\n"+getPreferences(0).getString("yield_note","No yield update has been requested.")).setPositiveButton("Close",null).show();return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current settings operation to finish");return;}
        temperatureWorker.execute(()->{
            YieldControlTransport transport=null;
            try{
                SecurePairing pairing=new SecureLinkCredentials(this).load();
                if(!pairing.host.equals(getPreferences(0).getString("yield_host",""))||!pairing.pin.equals(getPreferences(0).getString("yield_pin","")))throw new IOException("Pending request belongs to another pairing");
                transport=new YieldControlTransport(pairing.pin);yieldConnection=transport;
                if(!temperatureAllowed)throw new IOException("App paused");transport.connect(pairing.host,9444,pairing.token);transport.capabilities();
                if(!temperatureAllowed)throw new IOException("App paused");
                finishYield(transport.result(getPreferences(0).getString("yield_request_id",""),getPreferences(0).getString("yield_epoch","")),getPreferences(0).getInt("yield_target",-1));
            }catch(Exception e){yieldNote("Last yield result unavailable · check the machine; no command resent");runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check machine settings").setMessage("The previous result is unavailable. After checking the machine’s yield, dose and temperature, you can clear this pending result. No command will be resent.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->{getPreferences(0).edit().remove("yield_request_id").remove("yield_epoch").remove("yield_host").remove("yield_pin").remove("yield_target").apply();yieldNote("Previous result dismissed after machine check");}).show();});}
            finally{if(transport!=null)transport.close();yieldConnection=null;temperatureBusy.set(false);}
        });
    }
    private boolean pendingDose(){return getPreferences(0).contains("dose_request_id");}
    private void doseNote(String note){getPreferences(0).edit().putString("dose_note",note).apply();runOnUiThread(this::updateDoseDisplay);}
    private void dosePicker(){
        if(blockDuringRecipe())return;
        NumberPicker picker=new NumberPicker(LightDialogs.context(this));picker.setMinValue(10);picker.setMaxValue(309);picker.setWrapSelectorWheel(false);String[] labels=new String[300];for(int i=0;i<300;i++)labels[i]=String.format(Locale.US,"%.1f g",(i+10)/10.0);picker.setDisplayedValues(labels);picker.setValue(getPreferences(0).getInt("dose_last_verified",180));
        LightDialogs.builder(this).setTitle("Target grind dose").setMessage("Temperature and brew yield stay unchanged. This sets the target; it does not start grinding.").setView(picker).setNegativeButton("Cancel",null).setPositiveButton("Set dose",(d,w)->requestDose(picker.getValue()/10.0)).show();
    }
    private void requestDose(double requested){
        if(getPreferences(0).contains("pi_request_id")){toast("Check the pending pre-infusion result first");readPreinfusion();return;}
        if(pendingRecipe()){checkRecipeResult();return;}
        if(pendingYield()){checkYieldResult();return;}

        final int raw;try{raw=DoseRequest.raw(requested);}catch(Exception e){doseNote("Dose not applied: "+e.getMessage());return;}
        if(pendingTemperature()){toast("Check the pending temperature result first");checkTemperatureResult();return;}
        if(pendingDose()){doseNote("Previous dose result needs checking before another update");checkDoseResult();return;}
        if(!new SecureLinkCredentials(this).configured()){doseNote("Dose not applied · pair Secure Link and start the dose service");return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current settings operation to finish");return;}
        doseNote("Checking dose control…");
        temperatureWorker.execute(()->{
            DoseControlTransport transport=null;boolean submitted=false;
            try{
                SecurePairing pairing=new SecureLinkCredentials(this).load();transport=new DoseControlTransport(pairing.pin);doseConnection=transport;
                if(!temperatureAllowed)throw new IOException("App paused before update");
                transport.connect(pairing.host,9444,pairing.token);JSONObject capabilities=transport.capabilities();
                if(!temperatureAllowed)throw new IOException("App paused before update");
                String id=UUID.randomUUID().toString(),epoch=capabilities.getString("epoch");
                if(!getPreferences(0).edit().putString("dose_request_id",id).putString("dose_epoch",epoch).putString("dose_host",pairing.host).putString("dose_pin",pairing.pin).putInt("dose_target",raw).commit())throw new IOException("Could not save pending dose request");
                submitted=true;doseNote("Setting brew dose to "+String.format(Locale.US,"%.1f",raw/10.0)+" g…");
                finishDose(transport.setDose(id,epoch,raw),raw);
            }catch(Exception e){doseNote(submitted?"Dose result not confirmed · check dose result before trying again":"Dose not applied · control service unavailable or connection failed");}
            finally{if(transport!=null)transport.close();doseConnection=null;temperatureBusy.set(false);}
        });
    }
    private void finishDose(JSONObject result,int target)throws Exception{
        String outcome=result.getString("status");
        if(outcome.equals("verified")||outcome.equals("unchanged")){
            if(result.optInt("dose_raw",-1)!=target)throw new IOException("Dose result differs from pending target");
            if(!getPreferences(0).edit().remove("dose_request_id").remove("dose_epoch").remove("dose_host").remove("dose_pin").remove("dose_target").commit())throw new IOException("Could not retain dose result");
            getPreferences(0).edit().putInt("dose_last_verified",target).apply();
            doseNote("Last dose update: "+String.format(Locale.US,"%.1f",target/10.0)+" g · "+(outcome.equals("unchanged")?"already set":"verified")+" · temperature and yield unchanged");toast(String.format(Locale.US,"Dose %.1f g · %s",target/10.0,outcome));
        }else if(outcome.equals("busy")||outcome.equals("rejected")){
            if(!getPreferences(0).edit().remove("dose_request_id").remove("dose_epoch").remove("dose_host").remove("dose_pin").remove("dose_target").commit())throw new IOException("Could not save declined request");
            doseNote(outcome.equals("busy")?"Dose not applied · another update is running":"Dose request declined · machine settings unchanged");
        }else{
            doseNote(outcome.equals("pending")?"Dose update still running · check again shortly":"Dose result not verified · check the machine before another update");
            if(!outcome.equals("pending"))runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check machine settings").setMessage("The last update was not fully verified. Check the displayed dose, yield and temperature before starting another update. The command will not be retried automatically.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->{getPreferences(0).edit().remove("dose_request_id").remove("dose_epoch").remove("dose_host").remove("dose_pin").remove("dose_target").apply();doseNote("Previous result dismissed after machine check");}).show();});
        }
    }
    private void checkDoseResult(){
        if(!pendingDose()){LightDialogs.builder(this).setTitle("Dose result").setMessage("This checks the previous request; it never resends a dose command.\n\n"+getPreferences(0).getString("dose_note","No dose update has been requested.")).setPositiveButton("Close",null).show();return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current settings operation to finish");return;}
        temperatureWorker.execute(()->{
            DoseControlTransport transport=null;
            try{
                SecurePairing pairing=new SecureLinkCredentials(this).load();
                if(!pairing.host.equals(getPreferences(0).getString("dose_host",""))||!pairing.pin.equals(getPreferences(0).getString("dose_pin","")))throw new IOException("Pending request belongs to another pairing");
                transport=new DoseControlTransport(pairing.pin);doseConnection=transport;
                if(!temperatureAllowed)throw new IOException("App paused");transport.connect(pairing.host,9444,pairing.token);transport.capabilities();
                if(!temperatureAllowed)throw new IOException("App paused");
                finishDose(transport.result(getPreferences(0).getString("dose_request_id",""),getPreferences(0).getString("dose_epoch","")),getPreferences(0).getInt("dose_target",-1));
            }catch(Exception e){doseNote("Last dose result unavailable · check the machine; no command resent");runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check machine settings").setMessage("The previous result is unavailable. After checking the machine’s dose, yield and temperature, you can clear this pending result. No command will be resent.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->{getPreferences(0).edit().remove("dose_request_id").remove("dose_epoch").remove("dose_host").remove("dose_pin").remove("dose_target").apply();doseNote("Previous result dismissed after machine check");}).show();});}
            finally{if(transport!=null)transport.close();doseConnection=null;temperatureBusy.set(false);}
        });
    }
    private void readPreinfusion(){
        if(blockDuringRecipe())return;
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current update");return;}
        temperatureWorker.execute(()->{PreinfusionControlTransport t=null;try{
            SecurePairing p=new SecureLinkCredentials(this).load();t=new PreinfusionControlTransport(p.pin);piConnection=t;t.connect(p.host,9444,p.token);JSONObject caps=t.capabilities();if(!caps.optBoolean("preinfusion_navigation_home"))throw new IOException("Pre-infusion needs the updated machine service");String epoch=caps.getString("epoch");
            String pending=getPreferences(0).getString("pi_request_id","");
            if(!pending.isEmpty()){
                if(!p.host.equals(getPreferences(0).getString("pi_host",""))||!p.pin.equals(getPreferences(0).getString("pi_pin","")))throw new IOException("Pairing changed; check the machine");
                JSONObject r=t.exchange(PreinfusionState.result(pending,getPreferences(0).getString("pi_epoch","")));String status=r.optString("status");
                if(status.equals("verified")||status.equals("unchanged")){JSONObject got=r.getJSONObject("preinfusion");if(got.getBoolean("enabled")!=getPreferences(0).getBoolean("pi_target",!got.getBoolean("enabled"))||!PreinfusionState.sameSettings(got,new JSONObject(getPreferences(0).getString("pi_expected","{}"))))throw new IOException("Previous result differs from request");clearPI();toast("Previous pre-infusion update verified. Tap Pre-infusion to read again.");}
                else if(status.equals("pending")||status.equals("busy")){toast("Previous update is still running. No command resent.");}
                else runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check pre-infusion on machine").setMessage("The previous change was not confirmed. Check On/Off, pressure and time on the machine before clearing this result. Nothing is resent.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->clearPI()).show();});
                return;
            }
            JSONObject r=t.exchange(PreinfusionState.query(UUID.randomUUID().toString(),epoch));if(!r.optString("status").equals("verified"))throw new IOException(r.optString("message","Settings unavailable"));
            JSONObject state=r.getJSONObject("preinfusion");String summary=PreinfusionState.summary(state);long readAt=SystemClock.elapsedRealtime();
            runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;
                LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(18),dp(4),dp(18),dp(12));
                panel.addView(text("Read from machine just now",12,MUTED));
                preinfusionRow(panel,"State",state.optBoolean("enabled")?"On":"Off");
                preinfusionRow(panel,"Pressure",state.optInt("pressure_bar")+" bar");
                preinfusionRow(panel,"Duration",state.optInt("duration_s")+" seconds");
                Button toggle=button(state.optBoolean("enabled")?"Turn pre-infusion off":"Turn pre-infusion on",()->{});greenButton(toggle);LinearLayout.LayoutParams toggleLayout=new LinearLayout.LayoutParams(-1,dp(48));toggleLayout.topMargin=dp(12);panel.addView(toggle,toggleLayout);
                AlertDialog dialog=LightDialogs.builder(this).setTitle("Pre-infusion").setView(panel).setNegativeButton("Close",null).setNeutralButton("Refresh",(d,w)->readPreinfusion()).create();
                toggle.setOnClickListener(v->{
                    if(SystemClock.elapsedRealtime()-readAt>15000){toast("Refresh pre-infusion before changing it");return;}
                    if(running.get()){toast("Disconnect recording before changing pre-infusion");return;}
                    dialog.dismiss();
                    LightDialogs.builder(this).setTitle(state.optBoolean("enabled")?"Turn pre-infusion off?":"Turn pre-infusion on?").setMessage("Companion will open Pre-infusion, apply the change and return the machine to Home. Pressure, duration, temperature, dose and yield will stay unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Apply",(x,y)->setPreinfusion(state,!state.optBoolean("enabled"),epoch,p)).show();
                });dialog.show();
            });
        }catch(Exception e){if(getPreferences(0).contains("pi_request_id"))runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check pre-infusion on machine").setMessage("The previous result is unavailable. Check the machine before clearing it. No command is resent.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->clearPI()).show();});else toast("Pre-infusion: "+e.getMessage());}finally{if(t!=null)t.close();piConnection=null;temperatureBusy.set(false);}});
    }
    private void preinfusionRow(LinearLayout parent,String label,String value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(5),0,dp(5));
        TextView name=text(label,14,MUTED);name.setPadding(0,dp(4),dp(12),dp(4));row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView reading=text(value,16,FG);reading.setGravity(Gravity.RIGHT);reading.setPadding(0,dp(4),0,dp(4));row.addView(reading,new LinearLayout.LayoutParams(-2,-2));parent.addView(row);
        View divider=new View(this);divider.setBackgroundColor(0xffe2e6dc);parent.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void clearPI(){getPreferences(0).edit().remove("pi_request_id").remove("pi_epoch").remove("pi_host").remove("pi_pin").remove("pi_target").remove("pi_expected").apply();}
    private void setPreinfusion(JSONObject expected,boolean enabled,String readEpoch,SecurePairing pairing){
        if(!temperatureAllowed||running.get()||!temperatureBusy.compareAndSet(false,true)){toast("Wait for recording or the current update to finish");return;}
        temperatureWorker.execute(()->{PreinfusionControlTransport t=null;boolean journaled=false;try{
            if(getPreferences(0).contains("pi_request_id")||pendingTemperature()||pendingYield()||pendingDose()||getPreferences(0).contains("home_request_id")||getPreferences(0).getBoolean("recipe_pending",false))throw new IOException("Check the previous settings result first");
            t=new PreinfusionControlTransport(pairing.pin);piConnection=t;t.connect(pairing.host,9444,pairing.token);JSONObject caps=t.capabilities();if(!temperatureAllowed)throw new IOException("App paused");if(!readEpoch.equals(caps.getString("epoch")))throw new IOException("Machine service changed; refresh settings");
            String id=UUID.randomUUID().toString();JSONObject request=PreinfusionState.set(id,readEpoch,enabled,expected);
            if(!getPreferences(0).edit().putString("pi_request_id",id).putString("pi_epoch",readEpoch).putString("pi_host",pairing.host).putString("pi_pin",pairing.pin).putBoolean("pi_target",enabled).putString("pi_expected",expected.toString()).commit())throw new IOException("Could not save update progress");journaled=true;
            JSONObject r=t.exchange(request);String status=r.optString("status");
            if(status.equals("verified")||status.equals("unchanged")){clearPI();String message="Pre-infusion "+(enabled?"on":"off")+" · verified";toast(message);runOnUiThread(()->{if(preInfusionLabel!=null){preInfusionLabel.setText(message+" · last update");recoveryHandler.postDelayed(()->{if(!running.get()&&preInfusionLabel!=null)preInfusionLabel.setText("Pre-infusion · tap to read machine settings");},15000);}});}
            else if(status.equals("rejected")||status.equals("busy")){clearPI();toast(r.optString("message","No update sent"));}
            else toast("Pre-infusion not confirmed. Tap Pre-infusion to check the result; no resend.");
        }catch(Exception e){toast(journaled?"Pre-infusion not confirmed. Tap Pre-infusion to check; no resend.":"Pre-infusion: "+e.getMessage());}finally{if(t!=null)t.close();piConnection=null;temperatureBusy.set(false);}});
    }
    private void homeNote(String value){getPreferences(0).edit().putString("home_note",value).apply();runOnUiThread(()->{if(homeAppearanceNote!=null){homeAppearanceNote.setText(value);homeAppearanceNote.setVisibility(View.VISIBLE);}});}
    private void homeAppearanceDialog(){
        if(blockDuringRecipe())return;
        LightDialogs.builder(this).setTitle("Home appearance").setItems(new String[]{"Choose color and name","Restore standard Home","Check appearance"},(d,w)->{if(w==1){try{requestHome(HomeAppearance.value("standard",""));}catch(Exception e){toast(e.getMessage());}}else if(w==2)checkHomeAppearance();else{
            LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(18),dp(8),dp(18),dp(8));Spinner colors=new Spinner(LightDialogs.context(this));colors.setAdapter(new ArrayAdapter<String>(LightDialogs.context(this),android.R.layout.simple_spinner_dropdown_item,HomeAppearance.LABELS));colors.setSelection(1);form.addView(colors);EditText name=new EditText(this);name.setTextColor(FG);name.setSingleLine();name.setHint("Name on Home");name.setText(selectedRecipe==null?"House Espresso":HomeAppearance.suggest(selectedRecipe.optString("name")));name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});form.addView(name);
            LightDialogs.builder(this).setTitle("Home appearance").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Apply",(a,b)->{try{requestHome(HomeAppearance.value(HomeAppearance.COLORS[colors.getSelectedItemPosition()],name.getText().toString().trim()));}catch(Exception e){toast(e.getMessage());}}).show();}}).show();
    }
    private void requestHome(JSONObject home){
        if(blockDuringRecipe())return;
        if(getPreferences(0).contains("pi_request_id")){toast("Check the pending pre-infusion result first");readPreinfusion();return;}
        if(!new SecureLinkCredentials(this).configured()){homeNote("Pair Secure Link to change Home appearance");return;}
        if(getPreferences(0).contains("home_request_id")){checkHomeAppearance();return;}
        if(pendingRecipe()||pendingTemperature()||pendingYield()||pendingDose()){toast("Check the previous settings result first");return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current update");return;}
        temperatureWorker.execute(()->{try{applyHomeAppearance(home);}catch(Exception e){homeNote("Home appearance not confirmed · tap Check appearance");}finally{temperatureBusy.set(false);}});
    }
    private void applyHomeAppearance(JSONObject home)throws Exception{
        if(getPreferences(0).contains("home_request_id"))throw new IOException("Previous Home result needs checking");
        if(!temperatureAllowed||(recipeOperation.active()&&!recipeOperation.permits(temperatureAllowed)))throw new IOException("App paused");SecurePairing pairing=new SecureLinkCredentials(this).load();HomeControlTransport t=new HomeControlTransport(pairing.pin);homeConnection=t;
        try{t.connect(pairing.host,9444,pairing.token);JSONObject capabilities=t.capabilities();if(!temperatureAllowed||(recipeOperation.active()&&!recipeOperation.permits(temperatureAllowed)))throw new IOException("App paused");String id=UUID.randomUUID().toString(),epoch=capabilities.getString("epoch");
            if(!getPreferences(0).edit().putString("home_request_id",id).putString("home_epoch",epoch).putString("home_host",pairing.host).putString("home_pin",pairing.pin).commit())throw new IOException("Could not save Home request");
            if(recipeOperation.active()&&!recipeOperation.permits(temperatureAllowed))throw new IOException("Recipe interrupted");finishHome(t.setHome(id,epoch,home));
        }finally{t.close();homeConnection=null;}
    }
    private void finishHome(JSONObject r)throws Exception{
        String status=r.getString("status");if(Arrays.asList("applied","unchanged","deferred","busy","rejected","error").contains(status)){getPreferences(0).edit().remove("home_request_id").remove("home_epoch").remove("home_host").remove("home_pin").commit();}
        JSONObject home=r.optJSONObject("home");homeNote(status.equals("deferred")?"Home appearance saved · waiting for Home":status.equals("applied")||status.equals("unchanged")||status.equals("standard")?"Home · "+HomeAppearance.summary(home==null?new JSONObject():home):"Home appearance · "+r.optString("message","Check the machine"));
    }
    private void checkHomeAppearance(){
        if(!new SecureLinkCredentials(this).configured()){homeNote("Pair Secure Link to check Home appearance");return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current update");return;}
        temperatureWorker.execute(()->{HomeControlTransport t=null;try{SecurePairing pairing=new SecureLinkCredentials(this).load();t=new HomeControlTransport(pairing.pin);homeConnection=t;t.connect(pairing.host,9444,pairing.token);JSONObject cap=t.capabilities();String pending=getPreferences(0).getString("home_request_id","");
            if(!pending.isEmpty()){if(!pairing.host.equals(getPreferences(0).getString("home_host",""))||!pairing.pin.equals(getPreferences(0).getString("home_pin","")))throw new IOException("Pairing changed");JSONObject res=t.result(pending,getPreferences(0).getString("home_epoch",""));if(res.optString("status").equals("unknown")){homeNote("Previous Home result unavailable · no command resent");runOnUiThread(()->LightDialogs.builder(this).setTitle("Check Home appearance").setMessage("Check the machine. Clearing this result does not resend anything.").setNegativeButton("Keep pending",null).setPositiveButton("I checked Home",(a,b)->getPreferences(0).edit().remove("home_request_id").remove("home_epoch").remove("home_host").remove("home_pin").apply()).show());}else finishHome(res);}
            else {finishHome(t.getHome(UUID.randomUUID().toString(),cap.getString("epoch")));toast("Home appearance checked · see the result on Record");}
        }catch(Exception e){homeNote("Home appearance unavailable · no command resent");if(getPreferences(0).contains("home_request_id"))runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Check Home appearance").setMessage("Check the machine before clearing the previous Home result. Nothing will be resent.").setNegativeButton("Keep pending",null).setPositiveButton("I checked Home",(a,b)->getPreferences(0).edit().remove("home_request_id").remove("home_epoch").remove("home_host").remove("home_pin").apply()).show();});}finally{if(t!=null)t.close();homeConnection=null;temperatureBusy.set(false);}});
    }
    private boolean pendingRecipe(){return getPreferences(0).getBoolean("recipe_pending",false);}
    private boolean blockDuringRecipe(){if(!recipeOperation.active())return false;if(recipeOperation.permits(temperatureAllowed)&&foreground)showRecipeProgress();else toast("Recipe is stopping · wait for its result");return true;}
    private void showRecipeProgress(){if(!foreground||!recipeOperation.permits(temperatureAllowed)||isFinishing()||isDestroyed())return;if(recipeProgress==null)recipeProgress=new RecipeProgressDialog(this);recipeProgress.show(getPreferences(0).getString("recipe_control_note","Checking machine settings…"));}
    private void recipeControlNote(String note){getPreferences(0).edit().putString("recipe_control_note",note).apply();runOnUiThread(()->{updateRecipeControlDisplay();if(recipeOperation.active())showRecipeProgress();});}
    private void updateRecipeControlDisplay(){if(recipeControlWarning==null)return;String note=getPreferences(0).getString("recipe_control_note","");if(pendingRecipe()&&(note.startsWith("Recipe stopped")||note.startsWith("Recipe result uncertain")))note=RecipeResultText.stopped(getPreferences(0).getInt("recipe_completed",0),getPreferences(0).getInt("recipe_total",3),getPreferences(0).getString("recipe_step","setting"),getPreferences(0).getString("recipe_last_step_result",note));recipeControlWarning.setVisibility(pendingRecipe()?View.VISIBLE:View.GONE);recipeControlWarning.setTextColor(0xffe8bb7c);recipeControlWarning.setText((note.isEmpty()?"Recipe result needs checking":note)+(pendingRecipe()?" · Tap to check":""));}
    private RecipePreinfusion.Transport recipePITransport(SecurePairing pairing,String epoch){return new RecipePreinfusion.Transport(){
        public boolean allowed(){return recipeOperation.permits(temperatureAllowed)&&!running.get()&&!retryPending;}
        private JSONObject exchange(JSONObject request)throws Exception{if(!allowed())throw new IOException("App paused or recording connected");try(PreinfusionControlTransport t=new PreinfusionControlTransport(pairing.pin)){piConnection=t;t.connect(pairing.host,9444,pairing.token);JSONObject caps=t.capabilities();if(!caps.optBoolean("preinfusion_navigation_home")||!epoch.equals(caps.getString("epoch"))||!allowed())throw new IOException("Pre-infusion service changed or app paused");return t.exchange(request);}finally{piConnection=null;}}
        public JSONObject read()throws Exception{JSONObject r=exchange(PreinfusionState.query(UUID.randomUUID().toString(),epoch));if(!r.optString("status").equals("verified")||!r.getJSONObject("preinfusion").optBoolean("saved_match"))throw new IOException("Pre-infusion unavailable");return r.getJSONObject("preinfusion");}
        public JSONObject set(String id,boolean enabled,JSONObject expected)throws Exception{return exchange(PreinfusionState.set(id,epoch,enabled,expected));}
    };}
    private void finishRecipePIJournal()throws IOException{if(!getPreferences(0).edit().remove("recipe_pending").remove("recipe_step").remove("recipe_request_id").remove("pi_request_id").remove("pi_epoch").remove("pi_host").remove("pi_pin").remove("pi_target").remove("pi_expected").putInt("recipe_completed",4).commit())throw new IOException("Cannot save recipe completion");}
    private void requestRecipeControls(JSONObject recipe){
        if(blockDuringRecipe())return;
        if(busy.get()||running.get()||retryPending){toast("Disconnect recording before applying a recipe");return;}
        if(pendingRecipe()){checkRecipeResult();return;}
        final String recipePIMode;try{recipePIMode=RecipePreinfusion.mode(recipe);}catch(Exception e){recipeControlNote("Recipe not applied · "+e.getMessage());return;}
        final int recipeTotal=recipePIMode.equals("keep")?3:4;
        if(getPreferences(0).contains("pi_request_id")){toast("Check the pending pre-infusion result first");readPreinfusion();return;}
        final JSONObject recipeHome;try{recipeHome=HomeAppearance.recipe(recipe);}catch(Exception e){recipeControlNote("Recipe not applied · "+e.getMessage());return;}
        final RecipeControls.Plan plan;try{plan=new RecipeControls.Plan(recipe.getDouble("brew_c"),recipe.getDouble("yield_g"),recipe.getDouble("dose_g"),Double.NaN);}catch(Exception e){recipeControlNote("Recipe settings not applied: "+e.getMessage());return;}
        if(getPreferences(0).contains("home_request_id")){checkHomeAppearance();return;}
        if(pendingRecipe()){checkRecipeResult();return;}
        if(pendingTemperature()){checkTemperatureResult();return;}if(pendingYield()){checkYieldResult();return;}if(pendingDose()){checkDoseResult();return;}
        if(!new SecureLinkCredentials(this).configured()){recipeControlNote("Recipe settings not applied · pair Secure Link first");return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current settings operation to finish");return;}
        if(!recipeOperation.begin()){temperatureBusy.set(false);return;}
        recipeControlNote("Checking machine settings…");
        temperatureWorker.execute(()->{
            final String[] epoch={""};final int[] done={0};final boolean[] homeAvailable={false};final JSONObject[] recipePIExpected={null};
            try{
                final SecurePairing pairing=new SecureLinkCredentials(this).load();
                RecipeControls.Journal journal=new RecipeControls.Journal(){
                    public boolean hasPending(){return pendingRecipe();}
                    public void begin(RecipeControls.Plan p)throws IOException{if(!getPreferences(0).edit().putBoolean("recipe_pending",true).putInt("recipe_temperature",p.temperatureC).putInt("recipe_yield",p.yieldGrams).putInt("recipe_dose",p.doseRaw).putInt("recipe_completed",0).putInt("recipe_total",recipeTotal).putString("recipe_host",pairing.host).putString("recipe_pin",pairing.pin).putString("recipe_epoch",epoch[0]).remove("recipe_request_id").remove("recipe_step").commit())throw new IOException("Cannot save recipe progress");}
                    public void before(String kind,int target,String id)throws IOException{if(!getPreferences(0).edit().putString("recipe_step",kind).putString("recipe_request_id",id).putInt("recipe_target",target).commit())throw new IOException("Cannot save pending recipe step");recipeControlNote("Step "+(done[0]+1)+" of "+recipeTotal+" · "+(kind.equals("set_temperature")?"Temperature: "+target+"°C":kind.equals("set_yield")?"Yield: "+target+" g":"Dose: "+String.format(Locale.US,"%.1f g",target/10.0)));}
                    public void confirmed(String kind,int target,String id)throws IOException{done[0]++;String noteKey=kind.equals("set_temperature")?"temperature_note":kind.equals("set_yield")?"yield_note":"dose_note";String note=kind.equals("set_temperature")?"Last temperature update: "+target+"°C · verified via recipe":kind.equals("set_yield")?"Last yield update: "+target+" g · verified via recipe":"Last dose update: "+String.format(Locale.US,"%.1f",target/10.0)+" g · verified via recipe";String key=kind.equals("set_temperature")?"temperature_last_verified":kind.equals("set_yield")?"yield_last_verified":"dose_last_verified";if(!getPreferences(0).edit().putInt(key,target).putString(noteKey,note).putInt("recipe_completed",done[0]).remove("recipe_request_id").commit())throw new IOException("Cannot save verified recipe step");}
                    public void complete()throws IOException{if(recipePIMode.equals("keep")){if(!getPreferences(0).edit().remove("recipe_pending").remove("recipe_step").remove("recipe_request_id").commit())throw new IOException("Cannot retain recipe completion");}else if(!getPreferences(0).edit().putString("recipe_step","preinfusion_ready").remove("recipe_request_id").commit())throw new IOException("Cannot retain recipe progress");}
                };
                RecipeControls.Transport transport=new RecipeControls.Transport(){
                    public boolean allowed(){return recipeOperation.permits(temperatureAllowed)&&!running.get()&&!retryPending;}
                    public boolean allThreeAvailable()throws IOException{
                        TemperatureControlTransport t=null;try{if(!recipeOperation.permits(temperatureAllowed))return false;t=new TemperatureControlTransport(pairing.pin);temperatureConnection=t;t.connect(pairing.host,9444,pairing.token);JSONObject c=t.capabilities();epoch[0]=c.getString("epoch");homeAvailable[0]=c.optBoolean("home_appearance");HomeAppearance.requireService(recipeHome,homeAvailable[0]);if(!recipePIMode.equals("keep")){if(!c.optBoolean("preinfusion_navigation_home"))throw new IOException("Recipe pre-infusion needs the updated machine service");t.close();t=null;temperatureConnection=null;try{recipePIExpected[0]=RecipePreinfusion.prepare(recipePIMode,recipePITransport(pairing,epoch[0]));}catch(Exception piError){throw new IOException(piError);}}if(!c.optBoolean("recipe_return_home"))throw new IOException("Recipe Home return needs the updated machine service");if(!c.optBoolean("navigation_ready_guard"))throw new IOException("Recipe stability needs the updated machine service");return recipeOperation.permits(temperatureAllowed)&&c.optBoolean("yield")&&c.optBoolean("dose");}catch(Exception e){throw new IOException(e);}finally{if(t!=null)t.close();temperatureConnection=null;}
                    }
                    public RecipeControls.Outcome set(String kind,int target,String id)throws IOException{
                        JSONObject r;try{
                            if(!recipeOperation.permits(temperatureAllowed))throw new IOException("Recipe interrupted");
                            if(kind.equals("set_temperature")){try(TemperatureControlTransport t=new TemperatureControlTransport(pairing.pin)){temperatureConnection=t;t.connect(pairing.host,9444,pairing.token);if(!epoch[0].equals(t.capabilities().getString("epoch"))||!recipeOperation.permits(temperatureAllowed))throw new IOException("Control session changed or app paused");r=t.setTemperature(id,epoch[0],target);}}
                            else if(kind.equals("set_yield")){try(YieldControlTransport t=new YieldControlTransport(pairing.pin)){yieldConnection=t;t.connect(pairing.host,9444,pairing.token);if(!epoch[0].equals(t.capabilities().getString("epoch"))||!recipeOperation.permits(temperatureAllowed))throw new IOException("Control session changed or app paused");r=t.setYield(id,epoch[0],target);}}
                            else{try(DoseControlTransport t=new DoseControlTransport(pairing.pin)){doseConnection=t;t.connect(pairing.host,9444,pairing.token);if(!epoch[0].equals(t.capabilities().getString("epoch"))||!recipeOperation.permits(temperatureAllowed))throw new IOException("Control session changed or app paused");r=t.setDoseForRecipe(id,epoch[0],target);}}
                            String status=r.getString("status");getPreferences(0).edit().putString("recipe_last_step_result",r.optString("message",status)).commit();safeDiagnostic("recipe_step_result",kind+" status="+status+" detail="+r.optString("message",""));String key=kind.equals("set_temperature")?"celsius":kind.equals("set_yield")?"grams":"dose_raw";
                            return new RecipeControls.Outcome(status,r.optInt(key,-1),status.equals("verified")||status.equals("unchanged"),status.equals("verified"),r.optInt("celsius",-1),r.optInt("yield_raw",-1)/10,r.optInt("dose_raw",-1));
                        }catch(Exception e){throw new IOException(e);}finally{temperatureConnection=null;yieldConnection=null;doseConnection=null;}
                    }
                };
                RecipeControls.Progress progress=RecipeControls.apply(plan,journal,transport);
                if(progress.complete&&!recipePIMode.equals("keep")){
                    recipeControlNote("Step 4 of 4 · Pre-infusion: "+(recipePIMode.equals("on")?"On":"Off"));
                    RecipePreinfusion.apply(progress,recipePIMode,recipePIExpected[0],recipePITransport(pairing,epoch[0]),new RecipePreinfusion.Journal(){
                        public void before(String id,boolean target,JSONObject expected)throws Exception{if(!getPreferences(0).edit().putString("recipe_step","set_preinfusion").putString("recipe_request_id",id).putString("pi_request_id",id).putString("pi_epoch",epoch[0]).putString("pi_host",pairing.host).putString("pi_pin",pairing.pin).putBoolean("pi_target",target).putString("pi_expected",expected.toString()).commit())throw new IOException("Cannot save recipe pre-infusion progress");}
                        public void confirmed(String id)throws Exception{/* Completion is committed together below. */}
                    });
                    finishRecipePIJournal();
                    runOnUiThread(()->{if(preInfusionLabel!=null)preInfusionLabel.setText("Pre-infusion "+recipePIMode+" · verified via recipe");});
                }
                try{RecipeHomeCompletion.apply(progress,()->{if(homeAvailable[0]){recipeControlNote("Finishing · Home appearance");applyHomeAppearance(recipeHome);}else homeNote("Home appearance unavailable on this service");});}catch(Exception appearanceError){homeNote("Home appearance not confirmed · check Home appearance");safeDiagnostic("home_appearance",appearanceError.getMessage());}
                recipeControlNote(progress.complete?(recipeTotal==4?"Recipe verified · temperature, yield, dose and pre-infusion · Home screen":"Recipe verified · temperature, yield and dose · pre-infusion left unchanged · Home screen"):RecipeResultText.stopped(progress.completed,recipeTotal,progress.stoppedAt,getPreferences(0).getString("recipe_last_step_result","Check the machine before continuing")));
            }catch(Exception e){safeDiagnostic("recipe_apply",e.getMessage());recipeControlNote(pendingRecipe()?(done[0]==3&&!recipePIMode.equals("keep")?"Temperature, yield and dose applied · pre-infusion needs checking": "Recipe stopped · "+getPreferences(0).getInt("recipe_completed",0)+" of"+recipeTotal+" verified · "+e.getMessage()):"Recipe settings not applied · "+e.getMessage());}
            finally{temperatureBusy.set(false);runOnUiThread(()->{recipeOperation.finish();if(recipeProgress!=null)recipeProgress.dismiss();updateTemperatureDisplay();updateYieldDisplay();updateDoseDisplay();boolean resume=recordAction.complete(!pendingRecipe()&&getPreferences(0).getString("recipe_control_note","").startsWith("Recipe verified"),foreground);refreshCompact();if(resume){toast("Recipe settings verified · reconnecting recording");startRecording();}});}
        });
    }
    private void recipeResultDialog(String detail){runOnUiThread(()->{if(!temperatureAllowed||isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Recipe result").setMessage(detail+"\n\nNo command will be resent. Check temperature, yield, dose and pre-infusion before clearing this result.").setNegativeButton("Keep pending",null).setPositiveButton("I checked the machine",(d,w)->{getPreferences(0).edit().remove("recipe_pending").remove("recipe_request_id").remove("recipe_step").remove("pi_request_id").remove("pi_epoch").remove("pi_host").remove("pi_pin").remove("pi_target").remove("pi_expected").commit();recipeControlNote("Previous recipe result cleared after machine check");}).show();});}
    private void checkRecipeResult(){
        if(!pendingRecipe()){LightDialogs.builder(this).setTitle("Recipe result").setMessage(getPreferences(0).getString("recipe_control_note","No recipe settings update requested.")).setPositiveButton("Close",null).show();return;}
        if(!temperatureAllowed||!temperatureBusy.compareAndSet(false,true)){toast("Wait for the current settings operation to finish");return;}
        temperatureWorker.execute(()->{
            try{
                SecurePairing pairing=new SecureLinkCredentials(this).load();String id=getPreferences(0).getString("recipe_request_id","");
                if(!pairing.host.equals(getPreferences(0).getString("recipe_host",""))||!pairing.pin.equals(getPreferences(0).getString("recipe_pin","")))throw new IOException("Pairing differs from pending recipe");
                if(id.isEmpty()){recipeResultDialog(getPreferences(0).getInt("recipe_completed",0)+" of"+getPreferences(0).getInt("recipe_total",3)+" steps were verified. Remaining steps will not resume automatically.");return;}
                String kind=getPreferences(0).getString("recipe_step",""),epoch=getPreferences(0).getString("recipe_epoch","");JSONObject r;
                if(kind.equals("set_temperature")){try(TemperatureControlTransport t=new TemperatureControlTransport(pairing.pin)){temperatureConnection=t;if(!temperatureAllowed)throw new IOException("App paused");t.connect(pairing.host,9444,pairing.token);t.capabilities();r=t.result(id,epoch);}}
                else if(kind.equals("set_yield")){try(YieldControlTransport t=new YieldControlTransport(pairing.pin)){yieldConnection=t;if(!temperatureAllowed)throw new IOException("App paused");t.connect(pairing.host,9444,pairing.token);t.capabilities();r=t.result(id,epoch);}}
                else if(kind.equals("set_dose")){try(DoseControlTransport t=new DoseControlTransport(pairing.pin)){doseConnection=t;if(!temperatureAllowed)throw new IOException("App paused");t.connect(pairing.host,9444,pairing.token);t.capabilities();r=t.result(id,epoch);}}
                else if(kind.equals("set_preinfusion")){try(PreinfusionControlTransport t=new PreinfusionControlTransport(pairing.pin)){piConnection=t;if(!temperatureAllowed)throw new IOException("App paused");t.connect(pairing.host,9444,pairing.token);t.capabilities();r=t.exchange(PreinfusionState.result(id,epoch));}JSONObject got=r.optJSONObject("preinfusion");if(r.optString("status").equals("verified")||r.optString("status").equals("unchanged")){if(got==null||got.getBoolean("enabled")!=getPreferences(0).getBoolean("pi_target",!got.getBoolean("enabled"))||!PreinfusionState.sameSettings(got,new JSONObject(getPreferences(0).getString("pi_expected","{}")))||!got.optBoolean("saved_match")||!got.optBoolean("settings_preserved")||(r.optString("status").equals("verified")&&!got.optBoolean("native_reply")))throw new IOException("Pre-infusion result differs from requested recipe");}}
                else throw new IOException("Unknown recipe step");
                String outcome=r.getString("status");if(kind.equals("set_dose")&&(outcome.equals("verified")||outcome.equals("unchanged"))&&!r.optBoolean("home_verified"))outcome="settings verified; Home return not verified";if(outcome.equals("pending")){recipeControlNote("Recipe step still running · tap to check again");return;}
                recipeResultDialog((outcome.equals("verified")||outcome.equals("unchanged"))?"Last "+kind.replace("set_","")+" result: "+outcome+". "+r.optString("message","")+"\nRemaining steps were not resumed.":RecipeResultText.stopped(getPreferences(0).getInt("recipe_completed",0),getPreferences(0).getInt("recipe_total",3),kind,r.optString("message",outcome)));
            }catch(Exception e){recipeResultDialog("Recipe result unavailable. Check all three displayed settings.");}
            finally{temperatureConnection=null;yieldConnection=null;doseConnection=null;piConnection=null;temperatureBusy.set(false);}
        });
    }
    protected void onResume(){super.onResume();foreground=true;temperatureAllowed=true;waterHandler.removeCallbacks(waterRefresh);waterHandler.post(waterRefresh);}
    protected void onPause(){recordAction.cancel();recipeOperation.interrupt();if(recipeProgress!=null)recipeProgress.dismiss();temperatureAllowed=false;TemperatureControlTransport control=temperatureConnection;if(control!=null)control.close();YieldControlTransport yield=yieldConnection;if(yield!=null)yield.close();DoseControlTransport dose=doseConnection;if(dose!=null)dose.close();HomeControlTransport home=homeConnection;if(home!=null)home.close();PreinfusionControlTransport pi=piConnection;if(pi!=null)pi.close();foreground=false;waterHandler.removeCallbacks(waterRefresh);renderWater();if(retryPending)disconnect();dismissCupReminder();if(replay!=null)replay.pause();super.onPause();}
    private void startRecording(){
        if(recordAction.active()){toast("Wait for the settings change to finish");return;}
        if(blockDuringRecipe())return;
        if(busy.get()||syncing.get()||temperatureBusy.get()) {toast("Wait for the current operation to finish");return;}
        if(!new SecureLinkCredentials(this).configured()){machineConnectionSettings();return;}
        if(getPreferences(0).getString("host","").isEmpty()){machineAddress();return;}
        cancelRecovery();recovery.reset(true);connectionRequested=true;startRecordingAttempt();
    }
    private void startRecordingAttempt(){
        if(syncing.get()){cancelRecovery();show(false);status.setText("Recovery paused for forum sync. Tap Connect when finished.");return;}
        if(!connectionRequested||!foreground||!busy.compareAndSet(false,true))return;retryPending=false;final int generation=connectionGeneration;final boolean recovering=recovery.attempts()>0;running.set(true);String host=ip.getText().toString().trim();try{host=MachineAddress.privateIpv4(host);}catch(Exception e){running.set(false);busy.set(false);connectionRequested=false;toast(e.getMessage());return;}final JSONObject recordingRecipe=selectedRecipe==null?null:new JSONObject();if(recordingRecipe!=null)try{for(Iterator<String> keys=selectedRecipe.keys();keys.hasNext();){String k=keys.next();if(!k.equals("record_background")&&!k.equals("record_photo"))recordingRecipe.put(k,selectedRecipe.get(k));}}catch(Exception e){toast("Could not attach recipe");}
        JSONObject bagAtConnection=null;try{if(recordingRecipe!=null)bagAtConnection=new BagStore(getFilesDir()).find(recordingRecipe.optString("bag_id"));}catch(Exception e){toast("Bag age unavailable; recording will continue");}final JSONObject recordingBag=bagAtConnection;

        recordSample=null;recordSampleAt=0;recordDisplayWeight=null;recordState=Recorder.State.WAITING;recordStart=Double.NaN;recordGrindElapsed=Double.NaN;recordStoppedShot=null;recordConnectionMessage="";recordReferenceKey="";waterReceivedAt=0;renderWater();final String target=host;getPreferences(0).edit().putString("host",host).apply();live=new JSONArray();graphExpanded=false;chart.data(live);weight.setText("— g");scaleLabel.setText("Scale");detail.setText("Preparing verified ARM recording");synchronized(this){captures.clear();}getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);connect.setEnabled(false);stop.setEnabled(true);ip.setEnabled(false);status.setText("Connecting & verifying");workflowStatus.setText("Waiting for samples");refreshCompact();
        worker.execute(()->{
            final boolean[] graphFrozen={false};
            final RecordScaleState recordScale=new RecordScaleState();
            final WaterLowState waterState=new WaterLowState();
            final SteamReminderState steamState=new SteamReminderState();
            final CupReminderState cupState=new CupReminderState();if(recovering)cupState.skipInitialReminder();
            Recorder recorder=new Recorder(new Recorder.Listener(){public void startSkipped(String reason){safeDiagnostic("brew_start_skipped",reason);runOnUiThread(()->{if(lastResult!=null)lastResult.setText(reason+". Recording stays connected for the next shot.");});}public void ground(GrindWorkflow.Result g){try{store.append("grinder-events.jsonl",JsonStore.grind(g));usage.grind(JsonStore.grind(g),recordingBag);recordScale.finished(g.settledDose);}catch(Exception e){throw new RuntimeException("Grind save failed",e);}}public void saved(Recorder.Shot s){try{JSONObject o=JsonStore.shot(s);ShotContext.attach(o,recordingRecipe,recordingBag,java.time.ZoneId.systemDefault());addShot(o);runOnUiThread(()->{if(lastResult!=null)lastResult.setText((ShotInsights.shortEvent(o)?"Short event saved · ":"Saved · ")+ShotAssessment.result(o)+"\n"+ShotAssessment.summary(o));});diagnostic("shot_saved",s.complete?"complete":s.gaps.isEmpty()?"incomplete":"gapped");}catch(Exception e){throw new RuntimeException("Shot save failed",e);}}public void stopped(Recorder.Shot s){graphFrozen[0]=true;try{JSONObject ended=JsonStore.shot(s);runOnUiThread(()->{recordStoppedShot=ended;if(chart!=null)chart.shot(ended);});}catch(Exception e){safeDiagnostic("graph_stop",e.toString());}scheduleCapture(s.samples.get(0).utc);}},0.75);String exit="Disconnected";Throwable failure=null;
            try{log="session-"+System.currentTimeMillis()+".jsonl";store.pruneSessions(log);
                SecurePairing pairing;
                try {
                    pairing = new SecureLinkCredentials(MainActivity.this).load();
                } catch (Exception credentialError) {
                    throw new IOException("Stored pairing could not be read. Pair Secure Link again in Tools.");
                }
                pairing.requireHost(target);
                secureConnection = new SecureRecorderTransport(pairing.pin);
                if (!running.get()) return;
                secureConnection.connect(target, pairing.port, pairing.token);
                diagnostic("secure_transport", "certificate verified; awaiting recorder identity");
                runOnUiThread(() -> {
                    if (generation == connectionGeneration && running.get()) {
                        status.setText("VERIFYING SECURE RECORDER");
                    }
                });
                final double[] previousUptime={Double.NaN};
                SecureArmStream.Sink streamSink=new SecureArmStream.Sink(){
                    public void metadata(JSONObject metadata)throws Exception{metadata.put("transport","paired_tls");store.append(log,metadata);diagnostic("arm_stream_started","500 ms display-cache sampling; acknowledged storage on phone");}
                    public void sample(Sample s,JSONObject nativeRow)throws Exception{
                        if(!running.get())throw new IOException("Disconnected");
                        JSONObject raw=JsonStore.sample(s).put("source","arm_display_cache").put("native_seq",nativeRow.getLong("seq")).put("native_read_duration_ns",nativeRow.getLong("read_duration_ns")).put("native_gap_ns",nativeRow.getLong("gap_ns")).put("raw_preferences",nativeRow.getJSONArray("preferences")).put("native_delivery_queue_ns",nativeRow.optLong("delivery_queue_ns",0)).put("previous_phone_delivery_ns",nativeRow.optLong("previous_phone_delivery_ns",0)).put("previous_phone_processing_ns",nativeRow.optLong("previous_phone_processing_ns",0)).put("phone_interarrival_ns",nativeRow.optLong("phone_interarrival_ns",0)).put("previous_ack_wait_ns",nativeRow.optLong("previous_ack_wait_ns",0)).put("water_low",WaterLowState.low(s.registers));
                        store.appendSession(log,raw);
                    }
                    public void afterAcknowledged(Sample s,JSONObject nativeRow)throws Exception{
                        JSONObject raw=JsonStore.sample(s).put("water_low",WaterLowState.low(s.registers));
                        int waterEvent=waterState.accept(s.registers);
                        if(waterEvent!=0)store.append("diagnostics.jsonl",new JSONObject().put("utc",s.utc).put("event",waterEvent>0?"water_low":"water_low_cleared").put("uptime",s.uptime).put("session",log));
                        final long receivedAt=SystemClock.elapsedRealtime();final long queuedMs=nativeRow.optLong("delivery_queue_ns",0)/1000000;
                    if(Double.isFinite(previousUptime[0])&&s.uptime-previousUptime[0]>0.75){store.append("diagnostics.jsonl",new JSONObject().put("utc",Instant.now().toString()).put("event","sampling_gap").put("before_uptime",previousUptime[0]).put("after_uptime",s.uptime).put("duration_s",s.uptime-previousUptime[0]).put("native_read_duration_ms",nativeRow.getLong("read_duration_ns")/1e6).put("session",log));}
                    Recorder.State before=recorder.state;recorder.accept(s);boolean newShot=recorder.state==Recorder.State.RECORDING&&before!=Recorder.State.RECORDING;if(newShot)graphFrozen[0]=false;final boolean drawLive=!graphFrozen[0]&&s.active&&recorder.state==Recorder.State.RECORDING;final double observedBrewStart=recorder.startTime();
                    final double observedGrindElapsed=recorder.grindElapsed();previousUptime[0]=s.uptime;final boolean steaming=s.registers[24]==1||(s.registers[33]&0x4000)!=0;final boolean steamPrompt=steamState.accept(s.uptime,steaming,GrindWorkflow.grinding(s)||s.active);final CupReminderState.Prompt cupPrompt=cupState.accept(recorder.state==Recorder.State.READY,GrindWorkflow.grinding(s),s.active);
                    final RecordScaleState.Display scaleDisplay=recordScale.accept(s,recorder.state,steaming);
                    String state=(GrindWorkflow.grinding(s)?"GRINDING":recorder.state==Recorder.State.TARING?"WAITING FOR TARE":recorder.state.toString())+(recorder.hasGaps()?" · DATA GAP":"");
                    final Recorder.State frameState=recorder.state;
                    runOnUiThread(()->{if(generation!=connectionGeneration||!running.get())return;lastWaterLow=WaterLowState.low(s.registers);waterReceivedAt=queuedMs>=3000?0:receivedAt;recordSample=s;recordState=frameState;recordSampleAt=Math.max(1,receivedAt-Math.max(0,queuedMs));recordDisplayWeight=scaleDisplay.weight;recordDisplayLabel=scaleDisplay.label;recordDisplayMessage=scaleDisplay.message;recordStart=observedBrewStart;recordGrindElapsed=observedGrindElapsed;if(GrindWorkflow.grinding(s)||s.active||steaming)workflowReminder="";else if(steamPrompt)showCupReminder(CupReminderState.Prompt.STEAM);else if(cupPrompt!=CupReminderState.Prompt.NONE)showCupReminder(cupPrompt);if(newShot){live=new JSONArray();recordStoppedShot=null;if(chart instanceof RecordChart)((RecordChart)chart).start(observedBrewStart);}if(drawLive)live.put(raw);if(live.length()>400){JSONArray tail=new JSONArray();for(int i=live.length()-400;i<live.length();i++)tail.put(live.opt(i));live=tail;}if(detail!=null)detail.setText(RecordPresentation.observed(s));if(preInfusionLabel!=null)preInfusionLabel.setText(PreInfusionDisplay.summary(raw.optJSONObject("preferences")));if(drawLive&&chart!=null)chart.data(live);renderWater();});
                    }
                };
                new SecureArmStream(secureConnection).run(streamSink);
            }catch(Exception e){if(running.get()){failure=e;safeDiagnostic("recording_connection_failure",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));exit=ConnectionRecovery.message(e);}else exit="Disconnected";}
            finally{try{recorder.abort(exit);diagnostic("stopped",exit);}catch(Exception e){failure=new IOException("Could not save the interrupted recording",e);exit+="; saving incomplete record failed";}SecureRecorderTransport secure=secureConnection;if(secure!=null)secure.close();secureConnection=null;running.set(false);busy.set(false);final String message=exit;final Throwable endedFailure=failure;runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(generation!=connectionGeneration){if(!busy.get()&&!connectionRequested){show(false);status.setText("Disconnected");}return;}
                dismissCupReminder();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                int delay=recovery.nextDelaySeconds(endedFailure,connectionRequested,foreground);
                retryPending=delay>0;if(!retryPending)connectionRequested=false;
                recordConnectionMessage=message;show(false);status.setText(message+(retryPending?"\nRetry "+recovery.attempts()+"/"+recovery.limit()+" in "+delay+" seconds. Tap Cancel to stop retrying.":endedFailure!=null?"\nAutomatic recovery stopped. Tap Reconnect when ready.":""));
                if(retryPending)recoveryHandler.postDelayed(()->{if(generation==connectionGeneration&&connectionRequested&&foreground&&!busy.get())startRecordingAttempt();},delay*1000L);
            });}
        });
    }

    
    private void machineConnectionSettings(){
        if(!pairingAllowed()){toast("Disconnect recording and finish the current update before changing machine connection");return;}
        LightDialogs.builder(this).setTitle("Machine connection")
                .setItems(new String[]{"Scan pairing code", "Import pairing file", "Pair Secure Link (Beta)",
                        "Forget Secure Link pairing", "Machine address"}, (dialog, choice) -> {
                    switch (choice) {
                        case 0 -> qrPairing().scan();
                        case 1 -> qrPairing().importFile();
                        case 2 -> securePairingDialog();
                        case 3 -> LightDialogs.builder(this).setTitle("Forget machine pairing?")
                                .setMessage("Your shots and recipes stay on this phone. Secure Link will require pairing again.")
                                .setNegativeButton("Cancel", null).setPositiveButton("Forget", (confirmation, button) -> {
                                    try {
                                        new SecureLinkCredentials(this).clear();
                                        show(false);
                                    } catch (Exception error) {
                                        toast("Could not forget pairing");
                                    }
                                }).show();
                        case 4 -> machineAddress();
                    }
                }).show();
    }
    private void machineAddress(){EditText address=new EditText(this);address.setSingleLine();address.setText(getPreferences(0).getString("host",""));address.setInputType(android.text.InputType.TYPE_CLASS_PHONE);AlertDialog dialog=LightDialogs.builder(this).setTitle("Machine address").setView(address).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{String host=MachineAddress.privateIpv4(address.getText().toString().trim());getPreferences(0).edit().putString("host",host).apply();dialog.dismiss();show(false);}catch(Exception e){address.setError(e.getMessage());}}));dialog.show();}
    private void securePairingDialog(){
        SecureLinkCredentials credentials=new SecureLinkCredentials(this);SecurePairing prior=null;
        if(credentials.configured())try{prior=credentials.load();}catch(Exception e){toast("Stored pairing cannot be read; enter a new pairing");}
        final SecurePairing saved=prior;
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(18),dp(8),dp(18),dp(8));
        form.addView(text("Wake your Meraki and use the same home Wi-Fi. An installed Secure Link 0.2.0 service starts with the machine. For easiest setup use Three dots (⋯) > Machine connection > Scan pairing code or Import pairing file. This manual form accepts the full phone-pairing.json block below, or a fingerprint and key. Machine screenshots are not yet available over Secure Link.",13,MUTED));
        EditText hostField=new EditText(this);hostField.setSingleLine(true);hostField.setHint("Meraki IP address");hostField.setText(saved==null?getPreferences(0).getString("host",""):saved.host);form.addView(hostField);
        EditText fingerprint=new EditText(this);fingerprint.setHint("Certificate fingerprint (64 characters)");fingerprint.setText(saved==null?"":saved.pin);fingerprint.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);fingerprint.setSaveEnabled(false);form.addView(fingerprint);
        EditText keyField=new EditText(this);keyField.setSingleLine(true);keyField.setHint(saved==null?"Pairing key":"Leave blank to keep saved key");keyField.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);keyField.setSaveEnabled(false);keyField.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);form.addView(keyField);
        EditText pairingBlock=new EditText(this);pairingBlock.setHint("Optional: paste complete phone-pairing.json");pairingBlock.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);pairingBlock.setMinLines(2);pairingBlock.setMaxLines(4);pairingBlock.setSaveEnabled(false);pairingBlock.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);form.addView(pairingBlock);
        form.addView(text("The pairing block contains a secret. Do not share it or use device-private.json. Pasting a block replaces both values above when saved.",12,MUTED));
        TextView error=text("",12,CompanionPalette.ACCENT);form.addView(error);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=LightDialogs.builder(this).setTitle("Pair Secure Link").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save pairing",null).create();
        dialog.setOnShowListener(d->{dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{String entered=keyField.getText().toString().trim();if(entered.isEmpty()&&saved!=null)entered=saved.token;
                String block=pairingBlock.getText().toString().trim();
                SecurePairing pairing=block.isEmpty()?new SecurePairing(hostField.getText().toString().trim(),fingerprint.getText().toString().trim(),entered):SecurePairing.fromPhoneFile(hostField.getText().toString().trim(),block);
                credentials.save(pairing);getPreferences(0).edit().putString("host",pairing.host).apply();keyField.setText("");pairingBlock.setText("");dialog.dismiss();show(false);toast("Pairing saved. Tap Reconnect to verify and record.");
            }catch(Exception e){error.setText("Could not save pairing. Check the address and phone-pairing.json block, or both 64-character values.");}
        });});
        dialog.setOnDismissListener(d->{keyField.setText("");pairingBlock.setText("");});dialog.show();
    }

    private void dismissCupReminder(){workflowReminder="";renderWater();}
    private void cupReminderSettings(){
        String[] names={"Dose cup animation","Brew cup animation","Steam animation"};
        String[] keys={"record_animation_dose","record_animation_brew","record_animation_steam"};
        boolean[] enabled={getPreferences(0).getBoolean(keys[0],false),getPreferences(0).getBoolean(keys[1],false),getPreferences(0).getBoolean(keys[2],false)};
        LightDialogs.builder(this).setTitle("Record animations").setMultiChoiceItems(names,enabled,(d,i,on)->{getPreferences(0).edit().putBoolean(keys[i],on).apply();refreshCompact();}).setPositiveButton("Done",null).show();
    }
    private void renderRecordAnimation(boolean fresh){
        if(recordAnimation==null)return;String kind="";
        if(fresh&&recordSample!=null){if(recordSample.registers[24]==1||(recordSample.registers[33]&0x4000)!=0)kind="steam";else if(GrindWorkflow.grinding(recordSample))kind="dose";else if(recordSample.active||recordState==Recorder.State.TARING||recordState==Recorder.State.SETTLING)kind="brew";else kind=workflowReminder;
        if(!kind.isEmpty()&&!getPreferences(0).getBoolean("record_animation_"+kind,false))kind="";}
        recordAnimation.setVisibility(kind.isEmpty()?View.GONE:View.VISIBLE);recordAnimation.show(kind,!kind.isEmpty()&&foreground);
    }
    private void showCupReminder(CupReminderState.Prompt prompt){
        boolean brew=prompt==CupReminderState.Prompt.BREW;boolean steam=prompt==CupReminderState.Prompt.STEAM;
        String key=steam?"record_animation_steam":brew?"record_animation_brew":"record_animation_dose";
        if(!foreground||!running.get()||isFinishing()||!getPreferences(0).getBoolean(key,false))return;
        workflowReminder=steam?"steam":brew?"brew":"dose";
    }

    private void safeDiagnostic(String event,String reason){try{diagnostic(event,reason);}catch(Exception ignored){}}
    private void scheduleCapture(String id) {
        try {
            attachCapture(id, new JSONObject().put("status", "unavailable")
                    .put("reason", "Machine screenshots are not yet supported over Secure Link."));
        } catch (Exception error) {
            safeDiagnostic("screen_save_failed", error.toString());
        }
    }
    private synchronized void attachCapture(String id,JSONObject capture)throws Exception{
        JSONArray next=new JSONArray(shots.toString());for(int i=0;i<next.length();i++){JSONObject shot=next.getJSONObject(i);if(id.equals(shot.optString("start_utc"))){shot.put("screen_capture",capture);store.save(next);shots=next;return;}}captures.put(id,capture);
    }
    private void editNotes(String id){
        if(busy.get()){toast("Disconnect before editing notes");return;}
        String existing="";for(int i=0;i<shots.length();i++)if(id.equals(shots.optJSONObject(i).optString("start_utc")))existing=shots.optJSONObject(i).optString("notes");
        EditText input=new EditText(this);input.setTypeface(face);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setMinLines(4);input.setHint("Taste, beans, puck prep, what to change next time…");input.setText(existing);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8000)});
        LightDialogs.builder(this).setTitle("Shot notes").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String note=input.getText().toString();worker.execute(()->{try{saveNotes(id,note);runOnUiThread(()->show(true));}catch(Exception e){toast("Notes could not be saved");}});}).show();
    }
    private synchronized void saveNotes(String id,String note)throws Exception{JSONArray next=new JSONArray(shots.toString());for(int i=0;i<next.length();i++)if(id.equals(next.getJSONObject(i).optString("start_utc")))next.getJSONObject(i).put("notes",note);store.save(next);shots=next;}
    private void showCapture(JSONObject capture){try{byte[] png=java.util.Base64.getDecoder().decode(capture.getString("png_base64"));android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(png,0,png.length);if(bitmap==null)throw new IOException("Invalid PNG");RoundCaptureView image=new RoundCaptureView(this,bitmap);LightDialogs.builder(this).setTitle("Brew screen").setView(image).setPositiveButton("Close",null).show();}catch(Exception e){toast("Screenshot could not be opened");}}

    private void showConnectionError(String message){
        runOnUiThread(()->{
            if(isFinishing()||isDestroyed())return;
            TextView content=text(message,16,FG);content.setTextIsSelectable(true);content.setPadding(dp(22),dp(16),dp(22),dp(16));
            ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.addView(content);
            LightDialogs.builder(this).setTitle("Forum connection error").setView(scroll).setPositiveButton("Close",null).setNeutralButton("Copy error",(dialog,which)->{
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Forum connection error",message));toast("Error copied");
            }).show();
        });
    }
    private void forumControls(){
        ForumCredentials credentials=new ForumCredentials(this);
        body.addView(text(credentials.configured()?"Private sync · "+credentials.username():"Private journal · stored on this phone",12,MUTED));
        if(credentials.configured())actions(body,button(syncing.get()?"Syncing…":"Sync now",this::confirmSync),button("Forum journal",()->openForum(credentials.base())));
        else actions(body,button("Connect forum",this::forumSettings));
        String last=getPreferences(0).getString("last_forum_sync","");
        if(!last.isEmpty()){
            String headline=last.startsWith("Sync complete")?"Last sync complete": "Last sync stopped · see Tools";
            try{String time=last.substring(last.lastIndexOf('\n')+1);headline+=" · "+java.time.ZonedDateTime.ofInstant(Instant.parse(time),java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"));}catch(Exception ignored){}
            body.addView(text(headline,11,MUTED));
        }
    }
    private void openForum(String base){try{String url=ForumSync.normalizeBase(base)+"/index.php?my-shot-journal/";startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){toast("Could not open forum");}}
    private void forumSettings(){
        if(busy.get()||syncing.get()){toast("Disconnect and finish syncing before changing the connection");return;}
        ForumCredentials credentials=new ForumCredentials(this);
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(20),dp(8),dp(20),dp(8));
        form.addView(text("On the forum, open My Shot Journal and generate a connection key. Paste it below. This is not your forum password.",14,MUTED));
        String savedFingerprint="No saved connection";
        if(credentials.configured()){try{savedFingerprint=ForumSync.fingerprint(credentials.token());}catch(Exception e){savedFingerprint="Saved key could not be read";}}
        TextView fp=text("Saved key fingerprint: "+savedFingerprint,14,FG);fp.setTextIsSelectable(true);form.addView(fp);
        form.addView(text("Compare with the fingerprint in forum connection settings. This identifier is safe to share; it cannot be used as a connection key.",12,MUTED));
        EditText base=new EditText(this);base.setSingleLine();base.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);base.setText(credentials.base());form.addView(base);
        form.addView(button("Open My Shot Journal",()->openForum(base.getText().toString())));
        EditText key=new EditText(this);key.setSingleLine();key.setHint("Paste connection key");key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);form.addView(key);
        TextView pasted=text("Pasted key fingerprint: —",13,MUTED);pasted.setTextIsSelectable(true);form.addView(pasted);
        key.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable value){try{pasted.setText("Pasted key fingerprint: "+ForumSync.fingerprint(value.toString().trim()));}catch(Exception e){pasted.setText("Pasted key fingerprint: enter a complete key");}}});
        AlertDialog dialog=LightDialogs.builder(this).setTitle("Private journal connection").setView(form).setNegativeButton("Cancel",null).setNeutralButton("Forget connection",(d,w)->{credentials.clear();getPreferences(0).edit().remove("last_forum_sync").apply();show(true);toast("Forgot phone connection. Revoke the key on the forum to disable it there.");}).setPositiveButton("Verify connection",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final String address,token=key.getText().toString().trim();try{address=ForumSync.normalizeBase(base.getText().toString());ForumSync.validateKey(token);}catch(Exception e){toast(e.getMessage());return;}
            if(!syncing.compareAndSet(false,true))return;dialog.dismiss();toast("Verifying forum account…");
            worker.execute(()->{try{JSONObject who=new ForumSync().whoami(address,token);runOnUiThread(()->{syncing.set(false);if(isFinishing()||isDestroyed())return;LightDialogs.builder(this).setTitle("Connect to "+who.optString("username")+"?").setMessage("Forum: "+address+"\nMember ID: "+who.optInt("user_id")+"\nNo shots have been uploaded yet.").setNegativeButton("Cancel",null).setPositiveButton("Connect",(confirmDialog,which)->{try{credentials.save(address,token,who.optInt("user_id"),who.optString("username"));getPreferences(0).edit().remove("last_forum_sync").apply();show(true);}catch(Exception e){toast("Could not securely save the connection");}}).show();});}catch(Exception e){syncing.set(false);showConnectionError("Connection failed: "+e.getMessage());}});
        }));dialog.show();
    }
    private void confirmSync(){
        if(busy.get()){toast("Disconnect from the machine before syncing");return;}
        if(syncing.get()){toast("Sync is already running");return;}
        ForumCredentials credentials=new ForumCredentials(this);if(!credentials.configured()){forumSettings();return;}
        if(shots.length()==0){toast("No journal entries to upload");return;}
        LightDialogs.builder(this).setTitle("Sync private journal?").setMessage("Upload "+shots.length()+" saved events, including incomplete events, notes and screenshots, to "+credentials.username()+" on "+credentials.base()+"?\n\nExisting entries will be updated. Nothing is posted publicly. Phone deletions do not delete forum copies.").setNegativeButton("Cancel",null).setPositiveButton("Sync",(d,w)->syncForum(credentials)).show();
    }
    private void syncForum(ForumCredentials credentials){
        if(busy.get()||!syncing.compareAndSet(false,true))return;cancelSync=false;
        final JSONArray snapshot;try{snapshot=new JSONArray(shots.toString());}catch(Exception e){syncing.set(false);toast("Could not read journal");return;}
        AlertDialog progress=LightDialogs.builder(this).setTitle("Sync to Rotary Pumpers").setMessage("Checking connection…").setNegativeButton("Stop after current entry",(d,w)->cancelSync=true).setCancelable(false).create();progress.show();
        worker.execute(()->{
            int created=0,updated=0,unchanged=0,done=0;String failure=null;
            try{
                String base=credentials.base(),key=credentials.token();int uid=credentials.uid();ForumSync client=new ForumSync();JSONObject who=client.whoami(base,key);
                if(who.getInt("user_id")!=uid)throw new IOException("Account identity changed. Reconnect before uploading.");
                for(int i=0;i<snapshot.length()&&!cancelSync;i++){
                    Thread.sleep(1100);if(cancelSync)break;final int current=i+1;runOnUiThread(()->progress.setMessage("Uploading entry "+current+" of "+snapshot.length()+"…"));
                    JSONObject shot=snapshot.getJSONObject(i),reply=client.upload(base,key,shot);
                    if(reply.getInt("user_id")!=uid||!shot.getString("start_utc").equals(reply.optString("start_utc")))throw new IOException("Unexpected sync acknowledgement");
                    switch(reply.optString("result")){case "created":created++;break;case "updated":updated++;break;case "unchanged":unchanged++;break;default:throw new IOException("Unexpected upload result");}done++;
                }
            }catch(Exception e){failure=e.getMessage();}
            final String message=(failure!=null?"Sync stopped: "+failure:cancelSync?"Sync stopped by you":"Sync complete")+"\n"+done+" / "+snapshot.length()+" entries confirmed · "+created+" new · "+updated+" updated · "+unchanged+" unchanged"+(done<snapshot.length()?"\nTap Sync again to retry safely.":"");
            getPreferences(0).edit().putString("last_forum_sync",message+"\n"+Instant.now().toString()).apply();syncing.set(false);
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;progress.dismiss();show(true);LightDialogs.builder(this).setTitle("Forum sync").setMessage(message).setPositiveButton("OK",null).show();});
        });
    }

    private void exportLogs(){if(busy.get()){toast("Disconnect before exporting logs");return;}try{if(LogArchive.select(getFilesDir()).isEmpty()){toast("No recording logs yet");return;}}catch(Exception e){toast(e.getMessage());return;}Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"meraki-recording-logs-"+System.currentTimeMillis()+".zip");startActivityForResult(intent,3);}
    private void confirmDelete(String id){if(busy.get()){toast("Disconnect before deleting a shot");return;}LightDialogs.builder(this).setTitle("Delete this shot?").setMessage("Remove this entry from your journal? This cannot be undone here. Raw recording logs are kept.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(dialog,which)->worker.execute(()->{try{deleteShot(id);runOnUiThread(()->show(true));}catch(Exception e){toast("Could not delete shot: "+e.getMessage());}})).show();}
    private synchronized void deleteShot(String id)throws Exception{shots=store.delete(shots,id);}

    private void disconnect(){recordAction.cancel();recordSampleAt=0;if(preInfusionLabel!=null)preInfusionLabel.setText("Pre-infusion · tap to read machine settings");cancelRecovery();dismissCupReminder();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);running.set(false);waterReceivedAt=0;renderWater();if(connect!=null)connect.setEnabled(!busy.get());if(stop!=null)stop.setEnabled(false);if(status!=null)status.setText("Disconnected");SecureRecorderTransport secure=secureConnection;if(secure!=null)secure.close();}
    private void diagnostic(String event,String reason)throws Exception{store.append("diagnostics.jsonl",new JSONObject().put("utc",Instant.now().toString()).put("event",event).put("reason",reason));}
    private synchronized void addShot(JSONObject o)throws Exception{CaptureColors.upgradeCapture(o.optJSONObject("screen_capture"));usage.shot(o);String id=o.getString("start_utc");JSONObject cap=captures.remove(id);if(cap!=null)o.put("screen_capture",cap);for(int i=0;i<shots.length();i++)if(id.equals(shots.getJSONObject(i).optString("start_utc")))return;JSONArray next=new JSONArray(shots.toString());next.put(o);List<JSONObject> ordered=new ArrayList<>();for(int i=0;i<next.length();i++)ordered.add(next.getJSONObject(i));ordered.sort(Comparator.comparing(x->x.optString("start_utc")));JSONArray sorted=new JSONArray();for(JSONObject x:ordered)sorted.put(x);store.save(sorted);shots=sorted;}
    private void loadDemo(){if(busy.get()){toast("Disconnect before loading saved recordings");return;}worker.execute(()->{try(InputStream in=getAssets().open("recordings.json")){JSONArray data=new JSONArray(new String(readBounded(in,64*1024*1024),StandardCharsets.UTF_8));for(int i=0;i<data.length();i++)addShot(JsonStore.validateImported(data.getJSONObject(i)).put("demo",true));runOnUiThread(()->show(true));}catch(Exception e){toast("Import failed: "+e.getMessage());}});}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==8259){if(result==RESULT_OK&&data!=null){try{JSONObject c=ThingJournal.pair(data.getStringExtra(PairingScanActivity.RESULT));getPreferences(0).edit().putString("thing_pair",c.toString()).apply();syncThing();}catch(Exception e){toast("Could not pair: "+e.getMessage());}}return;}if(qrPairing().result(request,result,data))return;if(visualizer().fileResult(request,result,data))return;if(request==8043){recordPhotoResult(result,data);return;}if(result!=RESULT_OK||data==null||data.getData()==null)return;worker.execute(()->{try{if(request==4){try(InputStream in=getContentResolver().openInputStream(data.getData())){recipes.importText(new String(readBounded(in,RecipeMediaStore.MAX_BACKUP_BYTES),StandardCharsets.UTF_8));}runOnUiThread(this::showRecipes);toast("Recipes imported; existing recipes retained");}else if(request==5){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Cannot open destination");out.write(recipes.exportText().getBytes(StandardCharsets.UTF_8));}toast("Recipe backup saved");}else if(request==3){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Cannot open destination");LogArchive.write(getFilesDir(),out);}toast("Recording logs exported");}else if(request==EXPORT_RECENT_SHOTS){writeRecentShots(data.getData());}else if(request==2){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(new JSONObject().put("format","meraki-companion-backup-v1").put("shots",shots).put("bags",new BagStore(getFilesDir()).load()).put("usage_totals",usage.backup()).toString(2).getBytes(StandardCharsets.UTF_8));}toast("Backup saved");}else if(request==1){byte[] bytes;try(InputStream in=getContentResolver().openInputStream(data.getData())){bytes=readBounded(in,64*1024*1024);}if(bytes.length>64*1024*1024)throw new IOException("Import exceeds 64 MB");String raw=new String(bytes,StandardCharsets.UTF_8).trim();JSONArray list;if(raw.startsWith("["))list=new JSONArray(raw);else{JSONObject o=new JSONObject(raw);list=o.has("shots")?o.getJSONArray("shots"):new JSONArray().put(o);}if(list.length()>1000)throw new IOException("Too many records");List<JSONObject> validated=new ArrayList<>();for(int i=0;i<list.length();i++)validated.add(JsonStore.validateImported(list.getJSONObject(i)));if(raw.startsWith("{")){JSONObject root=new JSONObject(raw);if(root.has("bags"))BagStore.validate(root.getJSONArray("bags"));}for(JSONObject o:validated)addShot(o);if(raw.startsWith("{")){JSONObject backup=new JSONObject(raw);if(backup.has("usage_totals"))usage.merge(backup.getJSONObject("usage_totals"));if(backup.optJSONArray("bags")!=null)new BagStore(getFilesDir()).merge(backup.getJSONArray("bags"));}runOnUiThread(()->show(true));}}catch(Exception e){toast("File operation failed: "+e.getMessage());}});}
    private static byte[] readBounded(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("File exceeds size limit");out.write(buffer,0,n);}return out.toByteArray();}
    @Override public void onBackPressed(){if(currentPage==3){show(true);return;}if(currentPage==1||currentPage==2){show(false);return;}super.onBackPressed();}
    protected void onStop(){super.onStop();disconnect();}
    protected void onDestroy(){if(qrPairing!=null)qrPairing.destroy();if(visualizer!=null)visualizer.destroy();closeDetailPage();if(replay!=null)replay.dismiss();cancelSync=true;disconnect();worker.shutdown();temperatureWorker.shutdownNow();TemperatureControlTransport control=temperatureConnection;if(control!=null)control.close();YieldControlTransport yield=yieldConnection;if(yield!=null)yield.close();DoseControlTransport dose=doseConnection;if(dose!=null)dose.close();HomeControlTransport home=homeConnection;if(home!=null)home.close();PreinfusionControlTransport pi=piConnection;if(pi!=null)pi.close();super.onDestroy();}
}
