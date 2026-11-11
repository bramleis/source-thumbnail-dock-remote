package id.kominfo.sorong.sourcethumbnailremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.text.InputType;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.google.gson.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final int bgColor=0xfff2f5f1, ink=0xff1c2c24, green=0xff739b36, blue=0xff286b91, amber=0xffa87828, red=0xffbd493f, panel=0xffffffff;
    private LinearLayout root, sceneList, audioList;
    private TextView status, previewName, programName, audioStatus;
    private EditText hostInput, portInput, passwordInput;
    private Button connectButton;
    private ImageView previewImage, programImage;
    private WebSocketClient ws;
    private String password="", currentPreview="", currentProgram="";
    private boolean connected=false, studioMode=false, polling=false;
    private int requestNo=1;
    private final Map<String,Pending> pending=new HashMap<>();
    private final Map<String,ImageView> sourceImages=new HashMap<>();
    private final Map<String,String> sourceNames=new LinkedHashMap<>();
    private final Map<String,Boolean> muteStates=new HashMap<>();
    private final Map<String,ProgressBar> meterBars=new HashMap<>();
    private final Map<String,SeekBar> volumeSliders=new HashMap<>();
    private final Map<String,Button> muteButtons=new HashMap<>();
    private final List<String> audioInputs=new ArrayList<>();
    private final Queue<ScreenshotJob> screenshotQueue=new ArrayDeque<>();
    private Runnable pollRunnable;
    private boolean screenshotBusy=false;
    private String activeScreenshotRequestId=null;
    private Runnable screenshotTimeout=null;
    private long lastSourceRefresh=0;
    private int sourceCursor=0;
    private static class Pending { String type, context; Pending(String t,String c){type=t;context=c;} }
    private static class ScreenshotJob { String source,key; ScreenshotJob(String s,String k){source=s;key=k;} }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(ink);
        getWindow().setNavigationBarColor(ink);
        buildUi();
    }

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView text(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(ink);t.setTextSize(size);t.setPadding(dp(4),dp(5),dp(4),dp(5));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button button(String s,int color){
        Button b=new Button(this);b.setText(s);b.setTextSize(11);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setPadding(dp(5),0,dp(5),0);b.setBackground(shape(color,0));return b;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void section(LinearLayout parent,String title){TextView t=text(title,16,true);t.setPadding(0,dp(13),0,dp(5));parent.addView(t);}
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);root=column();root.setPadding(dp(12),dp(10),dp(12),dp(24));root.setBackgroundColor(bgColor);scroll.addView(root);setContentView(scroll);
        root.addView(text("KOMSOS THUMBNAIL REMOTE",21,true));
        root.addView(text("OBS STUDIO CONTROL  •  DIRECT WEBSOCKET",10,false));
        status=text("● Belum terhubung",12,true);status.setTextColor(red);root.addView(status);

        LinearLayout connect=column();connect.setPadding(dp(10),dp(6),dp(10),dp(8));connect.setBackground(shape(panel,0xffd9e0d8));root.addView(connect);
        hostInput=new EditText(this);hostInput.setSingleLine(true);hostInput.setText("192.168.1.10");hostInput.setHint("IP laptop OBS");hostInput.setInputType(InputType.TYPE_CLASS_TEXT);connect.addView(hostInput);
        LinearLayout credentials=row();
        portInput=new EditText(this);portInput.setSingleLine(true);portInput.setText("4455");portInput.setHint("Port");portInput.setInputType(InputType.TYPE_CLASS_NUMBER);credentials.addView(portInput,new LinearLayout.LayoutParams(0,-2,1));
        passwordInput=new EditText(this);passwordInput.setSingleLine(true);passwordInput.setHint("Password OBS WebSocket");passwordInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);credentials.addView(passwordInput,new LinearLayout.LayoutParams(0,-2,2));connect.addView(credentials);
        LinearLayout connectActions=row();connectButton=button("HUBUNGKAN",green);connectActions.addView(connectButton,new LinearLayout.LayoutParams(0,-2,1));
        Button scan=button("SCAN QR",blue);connectActions.addView(scan,new LinearLayout.LayoutParams(0,-2,1));connect.addView(connectActions);
        connectButton.setOnClickListener(v->{if(ws!=null&&ws.isOpen())disconnect();else connect();});
        scan.setOnClickListener(v->{IntentIntegrator ii=new IntentIntegrator(this);ii.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);ii.setPrompt("Pindai QR OBS WebSocket");ii.setBeepEnabled(false);ii.setOrientationLocked(false);ii.initiateScan();});

        section(root,"MONITOR PREVIEW / PROGRAM");
        LinearLayout monitors=row();root.addView(monitors);
        LinearLayout p=monitorCard("PREVIEW");previewName=text("—",11,true);p.addView(previewName);previewImage=monitorImage();p.addView(previewImage);
        LinearLayout pr=monitorCard("PROGRAM");programName=text("—",11,true);pr.addView(programName);programImage=monitorImage();pr.addView(programImage);
        monitors.addView(p,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=dp(7);monitors.addView(pr,rp);
        previewImage.setOnClickListener(v->fullscreen(previewImage,"PREVIEW"));programImage.setOnClickListener(v->fullscreen(programImage,"PROGRAM"));
        LinearLayout transition=row();Button take=button("TAKE / TRANSITION",amber);transition.addView(take,new LinearLayout.LayoutParams(0,-2,2));
        Button studio=button("STUDIO MODE: OFF",blue);transition.addView(studio,new LinearLayout.LayoutParams(0,-2,1));root.addView(transition);
        take.setOnClickListener(v->take());studio.setOnClickListener(v->toggleStudio(studio));

        section(root,"SCENES & SOURCES");
        root.addView(text("Setiap Scene menampilkan Source miliknya. Pilih PREVIEW untuk menyiapkan Scene, lalu TAKE untuk menayangkan.",10,false));
        sceneList=column();root.addView(sceneList);

        LinearLayout ah=row();ah.addView(text("AUDIO MIXER • REALTIME",15,true),new LinearLayout.LayoutParams(0,-2,1));
        Button muteAll=button("MUTE ALL",amber);ah.addView(muteAll);root.addView(ah);
        audioStatus=text("Menunggu event level audio dari OBS…",10,false);root.addView(audioStatus);
        audioList=column();root.addView(audioList);muteAll.setOnClickListener(v->muteAll());

        section(root,"OUTPUT CONTROL");
        LinearLayout outputs=row();Button stream=button("STREAM ON / OFF",blue),record=button("RECORD ON / OFF",amber);
        outputs.addView(stream,new LinearLayout.LayoutParams(0,-2,1));outputs.addView(record,new LinearLayout.LayoutParams(0,-2,1));root.addView(outputs);
        stream.setOnClickListener(v->toggleOutput("GetStreamStatus","StartStream","StopStream"));
        record.setOnClickListener(v->toggleOutput("GetRecordStatus","StartRecord","StopRecord"));
        Button refresh=button("MUAT ULANG SCENE & AUDIO",green);root.addView(refresh);refresh.setOnClickListener(v->refreshAll());
        root.addView(text("Gambar adalah screenshot berkala, bukan video kontinu. HP dan laptop OBS harus berada pada jaringan yang sama. Port standar OBS WebSocket: 4455.",10,false));
    }
    private LinearLayout monitorCard(String title){LinearLayout c=column();c.setPadding(dp(6),dp(6),dp(6),dp(6));c.setBackground(shape(panel,0xffd9e0d8));TextView h=text(title,11,true);h.setTextColor(blue);c.addView(h);return c;}
    private ImageView monitorImage(){ImageView im=new ImageView(this);im.setBackgroundColor(0xff111713);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(110)));return im;}
    private void fullscreen(ImageView source,String title){
        if(source.getDrawable()==null){toast("Gambar monitor belum tersedia.");return;}
        ImageView big=new ImageView(this);big.setImageDrawable(source.getDrawable());big.setBackgroundColor(Color.BLACK);big.setAdjustViewBounds(true);big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(big).setPositiveButton("TUTUP",(d,w)->d.dismiss()).create();
        dialog.getWindow();dialog.show();dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
    }

    private void connect(){
        try{
            String h=hostInput.getText().toString().trim();int port=Integer.parseInt(portInput.getText().toString().trim());password=passwordInput.getText().toString();
            if(h.isEmpty())throw new Exception("IP laptop belum diisi");
            setStatus("● Menghubungkan ke OBS…",amber);
            ws=new WebSocketClient(new URI("ws://"+h+":"+port)){
                @Override public void onOpen(ServerHandshake hand){main.post(()->setStatus("● WebSocket terbuka; autentikasi…",blue));}
                @Override public void onMessage(String msg){handleMessage(msg);}
                @Override public void onClose(int code,String reason,boolean remote){main.post(()->{connected=false;stopPolling();pending.clear();connectButton.setText("HUBUNGKAN");setStatus("● Terputus: "+reason,red);});}
                @Override public void onError(Exception e){main.post(()->setStatus("● Koneksi gagal: "+e.getMessage(),red));}
            };
            ws.connect();
        }catch(Exception e){toast("Periksa IP dan port: "+e.getMessage());}
    }
    private void disconnect(){stopPolling();connected=false;pending.clear();if(ws!=null)ws.close();connectButton.setText("HUBUNGKAN");setStatus("● Terputus",red);}
    private void setStatus(String s,int color){status.setText(s);status.setTextColor(color);}
    private void send(String type,JsonObject data){send(type,data,null);}
    private String send(String type,JsonObject data,String context){
        if(ws==null||!ws.isOpen()||!connected)return null;
        String id="kremote-"+(requestNo++);
        JsonObject rd=new JsonObject();rd.addProperty("requestType",type);rd.addProperty("requestId",id);rd.add("requestData",data==null?new JsonObject():data);
        JsonObject packet=new JsonObject();packet.addProperty("op",6);packet.add("d",rd);
        pending.put(id,new Pending(type,context));
        try{ws.send(packet.toString());return id;}catch(Exception e){pending.remove(id);return null;}
    }
    private void handleMessage(String msg){
        try{
            JsonObject packet=JsonParser.parseString(msg).getAsJsonObject();int op=packet.get("op").getAsInt();JsonObject d=packet.getAsJsonObject("d");
            if(op==0){
                JsonObject identify=new JsonObject();identify.addProperty("rpcVersion",1);
                // Scenes, inputs, outputs, scene items and InputVolumeMeters.
                identify.addProperty("eventSubscriptions",4|8|64|128|65536);
                if(d.has("authentication")){
                    JsonObject a=d.getAsJsonObject("authentication");
                    String secret=sha256(password+a.get("salt").getAsString());
                    identify.addProperty("authentication",sha256(secret+a.get("challenge").getAsString()));
                }
                JsonObject out=new JsonObject();out.addProperty("op",1);out.add("d",identify);ws.send(out.toString());
            }else if(op==2){
                connected=true;main.post(()->{setStatus("● OBS TERHUBUNG",green);connectButton.setText("PUTUSKAN");});
                refreshAll();send("GetStudioModeEnabled",new JsonObject());startPolling();
            }else if(op==7){
                String id=d.has("requestId")?d.get("requestId").getAsString():"";Pending p=pending.remove(id);if(p==null)return;
                if(id.equals(activeScreenshotRequestId)) finishScreenshotRequest();
                JsonObject st=d.has("requestStatus")?d.getAsJsonObject("requestStatus"):new JsonObject();
                if(!st.has("result")||!st.get("result").getAsBoolean()){
                    String why=st.has("comment")?st.get("comment").getAsString():"OBS menolak perintah";
                    main.post(()->{setStatus("● OBS terhubung • "+p.type+" gagal",red);toast(p.type+": "+why);});return;
                }
                JsonObject response=d.has("responseData")&&!d.get("responseData").isJsonNull()?d.getAsJsonObject("responseData"):new JsonObject();
                handleResponse(p,response);
            }else if(op==5)handleEvent(d);
        }catch(Exception e){main.post(()->setStatus("● Respons OBS tidak dikenali",red));}
    }
    private String sha256(String s)throws Exception{return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    private void handleResponse(Pending p,JsonObject r){
        switch(p.type){
            case "GetSceneList":showScenes(r);break;
            case "GetSceneItemList":showSources(p.context,r);break;
            case "GetCurrentProgramScene":currentProgram=getString(r,"currentProgramSceneName");main.post(()->programName.setText(currentProgram));break;
            case "GetCurrentPreviewScene":currentPreview=getString(r,"currentPreviewSceneName");main.post(()->previewName.setText(currentPreview));break;
            case "GetSourceScreenshot":showScreenshot(p.context,getString(r,"imageData"));break;
            case "GetStudioModeEnabled":studioMode=r.has("studioModeEnabled")&&r.get("studioModeEnabled").getAsBoolean();break;
            case "GetInputList":handleInputList(r);break;
            case "GetInputMute":
                if(p.context!=null){boolean muted=r.has("inputMuted")&&r.get("inputMuted").getAsBoolean();muteStates.put(p.context,muted);Button mb=muteButtons.get(p.context);if(mb!=null)main.post(()->mb.setText(muted?"UNMUTE":"MUTE"));}
                break;
            case "GetInputVolume":
                if(p.context!=null&&r.has("inputVolumeMul")){SeekBar slider=volumeSliders.get(p.context);if(slider!=null){int value=(int)Math.max(0,Math.min(100,r.get("inputVolumeMul").getAsDouble()*100));main.post(()->slider.setProgress(value));}}
                break;
            case "GetStreamStatus":case "GetRecordStatus":
                boolean active=r.has("outputActive")&&r.get("outputActive").getAsBoolean();
                if(p.context!=null&&p.context.startsWith("OUTPUT:")){
                    String[] parts=p.context.split(":");
                    if(parts.length==3)send(active?parts[2]:parts[1],new JsonObject());
                }
                break;
        }
    }
    private String getString(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private void refreshAll(){send("GetSceneList",new JsonObject());send("GetCurrentProgramScene",new JsonObject());send("GetCurrentPreviewScene",new JsonObject());send("GetInputList",new JsonObject());}
    private void showScenes(JsonObject response){
        JsonArray arr=response.has("scenes")?response.getAsJsonArray("scenes"):new JsonArray();
        main.post(()->{sceneList.removeAllViews();sourceImages.clear();sourceNames.clear();
            for(JsonElement e:arr){String name=getString(e.getAsJsonObject(),"sceneName");addSceneCard(name);}
        });
        for(JsonElement e:arr){String name=getString(e.getAsJsonObject(),"sceneName");JsonObject q=new JsonObject();q.addProperty("sceneName",name);send("GetSceneItemList",q,name);}
    }
    private void addSceneCard(String name){
        LinearLayout card=column();card.setPadding(dp(8),dp(6),dp(8),dp(8));card.setBackground(shape(panel,0xffd9e0d8));
        LinearLayout header=row();TextView title=text(name,14,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button prev=button("PREVIEW",blue),prog=button("PROGRAM",amber);header.addView(prev);header.addView(prog);card.addView(header);
        prev.setOnClickListener(v->{JsonObject q=new JsonObject();q.addProperty("sceneName",name);send("SetCurrentPreviewScene",q);});
        prog.setOnClickListener(v->{JsonObject q=new JsonObject();q.addProperty("sceneName",name);send("SetCurrentProgramScene",q);});
        GridLayout grid=new GridLayout(this);grid.setColumnCount(3);card.addView(grid);card.setTag("SCENE:"+name);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);sceneList.addView(card,lp);
    }
    private LinearLayout findScene(String name){for(int i=0;i<sceneList.getChildCount();i++){View v=sceneList.getChildAt(i);if(v.getTag()!=null&&v.getTag().equals("SCENE:"+name))return (LinearLayout)v;}return null;}
    private void showSources(String scene,JsonObject response){
        if(scene==null)return;JsonArray items=response.has("sceneItems")?response.getAsJsonArray("sceneItems"):new JsonArray();
        main.post(()->{
            LinearLayout card=findScene(scene);if(card==null)return;GridLayout grid=(GridLayout)card.getChildAt(1);grid.removeAllViews();
            for(JsonElement e:items)addSourceTile(scene,e.getAsJsonObject(),grid);
        });
    }
    private void addSourceTile(String scene,JsonObject item,GridLayout grid){
        String source=getString(item,"sourceName");int itemId=item.has("sceneItemId")?item.get("sceneItemId").getAsInt():-1;
        boolean enabled=item.has("sceneItemEnabled")&&item.get("sceneItemEnabled").getAsBoolean();String key=scene+"::"+itemId;
        LinearLayout tile=column();tile.setPadding(dp(3),dp(3),dp(3),dp(3));tile.setBackground(shape(0xfff0f3ef,0xffdce2dc));
        ImageView image=monitorImage();image.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(64)));tile.addView(image);
        TextView title=text(source,10,true);title.setMaxLines(2);tile.addView(title);
        CheckBox visible=new CheckBox(this);visible.setText("Tampil");visible.setTextSize(9);visible.setChecked(enabled);tile.addView(visible);
        visible.setOnCheckedChangeListener((v,on)->{JsonObject q=new JsonObject();q.addProperty("sceneName",scene);q.addProperty("sceneItemId",itemId);q.addProperty("sceneItemEnabled",on);send("SetSceneItemEnabled",q);});
        tile.setOnClickListener(v->{JsonObject q=new JsonObject();q.addProperty("sceneName",scene);send("SetCurrentPreviewScene",q);toast("Scene "+scene+" disiapkan di Preview.");});
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(tile,lp);
        sourceImages.put(key,image);sourceNames.put(key,source);queueScreenshot(source,key);
    }
    private void queueScreenshot(String source,String key){
        if(source==null||source.isEmpty()||!connected)return;
        // Keep at most one queued refresh per view; never let old polling cycles accumulate.
        for(ScreenshotJob j:screenshotQueue)if(j.key.equals(key))return;
        screenshotQueue.add(new ScreenshotJob(source,key));dispatchScreenshot();
    }
    private void dispatchScreenshot(){
        if(screenshotBusy||!connected||screenshotQueue.isEmpty())return;
        ScreenshotJob job=screenshotQueue.poll();if(job==null)return;screenshotBusy=true;
        JsonObject q=new JsonObject();q.addProperty("sourceName",job.source);q.addProperty("imageFormat","jpg");q.addProperty("imageWidth",240);q.addProperty("imageHeight",135);q.addProperty("imageCompressionQuality",40);
        activeScreenshotRequestId=send("GetSourceScreenshot",q,job.key);
        if(activeScreenshotRequestId==null){finishScreenshotRequest();return;}
        screenshotTimeout=()->{
            if(activeScreenshotRequestId!=null){pending.remove(activeScreenshotRequestId);finishScreenshotRequest();}
        };
        main.postDelayed(screenshotTimeout,6000);
    }
    private void finishScreenshotRequest(){
        if(screenshotTimeout!=null)main.removeCallbacks(screenshotTimeout);
        screenshotTimeout=null;activeScreenshotRequestId=null;screenshotBusy=false;
        if(connected)main.postDelayed(this::dispatchScreenshot,250);
    }
    private void showScreenshot(String key,String data){
        try{
            if(data==null||data.isEmpty())return;int comma=data.indexOf(',');String b64=comma>=0?data.substring(comma+1):data;
            byte[] bytes=Base64.decode(b64,Base64.DEFAULT);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)return;
            main.post(()->{ImageView target;if("__PREVIEW__".equals(key))target=previewImage;else if("__PROGRAM__".equals(key))target=programImage;else target=sourceImages.get(key);if(target!=null)target.setImageBitmap(bitmap);});
        }catch(Exception ignored){}
    }
    private void handleEvent(JsonObject d){
        String type=getString(d,"eventType");JsonObject e=d.has("eventData")?d.getAsJsonObject("eventData"):new JsonObject();
        if(type.equals("CurrentProgramSceneChanged")){currentProgram=getString(e,"sceneName");main.post(()->programName.setText(currentProgram));}
        else if(type.equals("CurrentPreviewSceneChanged")){currentPreview=getString(e,"sceneName");main.post(()->previewName.setText(currentPreview));}
        else if(type.equals("StudioModeStateChanged"))studioMode=e.has("studioModeEnabled")&&e.get("studioModeEnabled").getAsBoolean();
        else if(type.equals("InputVolumeMeters"))renderAudioMeters(e);
        else if(type.equals("InputMuteStateChanged")){String name=getString(e,"inputName");muteStates.put(name,e.has("inputMuted")&&e.get("inputMuted").getAsBoolean());}
    }
    private void handleInputList(JsonObject response){
        JsonArray inputs=response.has("inputs")?response.getAsJsonArray("inputs"):new JsonArray();
        audioInputs.clear();muteStates.clear();meterBars.clear();volumeSliders.clear();muteButtons.clear();
        main.post(()->audioList.removeAllViews());
        for(JsonElement el:inputs){
            JsonObject input=el.getAsJsonObject();String name=getString(input,"inputName");String kind=getString(input,"inputKind").toLowerCase(Locale.ROOT);
            if(name.isEmpty())continue;
            boolean audio=kind.contains("audio")||kind.contains("wasapi")||kind.contains("pulse")||kind.contains("alsa")||kind.contains("coreaudio")||kind.contains("jack")||kind.contains("input_capture");
            if(!audio)continue;
            audioInputs.add(name);
            JsonObject q=new JsonObject();q.addProperty("inputName",name);
            send("GetInputMute",q,name);send("GetInputVolume",q,name);
            main.post(()->addAudioControl(name));
        }
        main.post(()->{if(audioInputs.isEmpty())audioStatus.setText("Input audio belum terdeteksi. Pastikan sumber audio aktif di OBS.");});
    }
    private void addAudioControl(String name){
        LinearLayout row=row();row.setPadding(0,dp(3),0,dp(3));
        TextView label=text(name,10,true);label.setMaxLines(1);row.addView(label,new LinearLayout.LayoutParams(dp(105),-2));
        ProgressBar meter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);meter.setMax(100);meter.setProgress(0);row.addView(meter,new LinearLayout.LayoutParams(0,dp(13),1));
        Button mute=button("MUTE",amber);row.addView(mute);
        SeekBar volume=new SeekBar(this);volume.setMax(100);volume.setProgress(100);row.addView(volume,new LinearLayout.LayoutParams(dp(105),dp(30)));
        meterBars.put(name,meter);muteButtons.put(name,mute);volumeSliders.put(name,volume);mute.setText(muteStates.getOrDefault(name,false)?"UNMUTE":"MUTE");
        mute.setOnClickListener(v->{boolean next=!muteStates.getOrDefault(name,false);JsonObject q=new JsonObject();q.addProperty("inputName",name);q.addProperty("inputMuted",next);send("SetInputMute",q);muteStates.put(name,next);mute.setText(next?"UNMUTE":"MUTE");});
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){JsonObject q=new JsonObject();q.addProperty("inputName",name);q.addProperty("inputVolumeMul",bar.getProgress()/100.0);send("SetInputVolume",q);}
        });
        audioList.addView(row);
    }
    private void renderAudioMeters(JsonObject data){
        JsonArray inputs=data.has("inputs")?data.getAsJsonArray("inputs"):new JsonArray();
        main.post(()->{
            for(JsonElement el:inputs){
                JsonObject input=el.getAsJsonObject();String name=getString(input,"inputName");ProgressBar meter=meterBars.get(name);if(meter==null)continue;
                int pct=(int)Math.max(0,Math.min(100,readPeak(input)*100));meter.setProgress(pct);
                Button mute=muteButtons.get(name);if(mute!=null)mute.setText(muteStates.getOrDefault(name,false)?"UNMUTE":"MUTE");
            }
            audioStatus.setText("Audio meter realtime • "+inputs.size()+" input terpantau");
        });
    }
    private double readPeak(JsonObject input){
        String[] keys={"inputLevelsMul","inputLevels","inputLevelsDb"};
        for(String key:keys)if(input.has(key)&&input.get(key).isJsonArray()){
            JsonArray channels=input.getAsJsonArray(key);double max=0;
            for(JsonElement channel:channels)if(channel.isJsonArray())for(JsonElement val:channel.getAsJsonArray()){
                try{double x=val.getAsDouble();if(key.endsWith("Db")||key.equals("inputLevels"))x=Math.pow(10,x/20.0);max=Math.max(max,x);}catch(Exception ignored){}
            }
            return max;
        }return 0;
    }
    private void muteAll(){
        if(audioInputs.isEmpty()){toast("Daftar input audio belum tersedia.");return;}
        for(String name:new ArrayList<>(audioInputs)){JsonObject q=new JsonObject();q.addProperty("inputName",name);q.addProperty("inputMuted",true);send("SetInputMute",q);muteStates.put(name,true);Button b=muteButtons.get(name);if(b!=null)b.setText("UNMUTE");}
        toast("Perintah mute dikirim ke seluruh input audio.");
    }
    private void toggleOutput(String statusType,String start,String stop){
        send(statusType,new JsonObject(),"OUTPUT:"+start+":"+stop);
    }
    private void take(){
        if(studioMode)send("TriggerStudioModeTransition",new JsonObject());
        else if(!currentPreview.isEmpty()){JsonObject q=new JsonObject();q.addProperty("sceneName",currentPreview);send("SetCurrentProgramScene",q);}
        else toast("Pilih Scene Preview terlebih dahulu.");
    }
    private void toggleStudio(Button button){
        JsonObject q=new JsonObject();q.addProperty("studioModeEnabled",!studioMode);send("SetStudioModeEnabled",q);studioMode=!studioMode;button.setText(studioMode?"STUDIO MODE: ON":"STUDIO MODE: OFF");
    }
    private void startPolling(){
        if(polling)return;polling=true;
        pollRunnable=new Runnable(){@Override public void run(){
            if(!polling||!connected)return;
            // Monitors are refreshed first. Source thumbnails are refreshed slowly and round-robin.
            if(!currentPreview.isEmpty())queueScreenshot(currentPreview,"__PREVIEW__");
            if(!currentProgram.isEmpty())queueScreenshot(currentProgram,"__PROGRAM__");
            long now=System.currentTimeMillis();
            List<String> keys=new ArrayList<>(sourceNames.keySet());
            if(!keys.isEmpty() && now-lastSourceRefresh>=15000){
                if(sourceCursor>=keys.size())sourceCursor=0;
                String key=keys.get(sourceCursor++);String src=sourceNames.get(key);
                queueScreenshot(src,key);lastSourceRefresh=now;
            }
            main.postDelayed(this,10000);
        }};
        main.post(pollRunnable);
    }
    private void stopPolling(){polling=false;if(pollRunnable!=null)main.removeCallbacks(pollRunnable);screenshotQueue.clear();if(activeScreenshotRequestId!=null)pending.remove(activeScreenshotRequestId);finishScreenshotRequest();}
    private void parseQr(String raw){
        try{
            String s=raw.trim();
            if(s.startsWith("{")){JsonObject j=JsonParser.parseString(s).getAsJsonObject();if(j.has("host"))hostInput.setText(j.get("host").getAsString());if(j.has("port"))portInput.setText(j.get("port").getAsString());if(j.has("password"))passwordInput.setText(j.get("password").getAsString());}
            else{if(s.startsWith("ws://"))s=s.substring(5);else if(s.startsWith("obsws://"))s=s.substring(8);String authority=s.split("/",2)[0];String[] hp=authority.split(":");hostInput.setText(hp[0]);if(hp.length>1)portInput.setText(hp[1]);if(s.contains("password=")){String pw=s.substring(s.indexOf("password=")+9).split("[& ]",2)[0];passwordInput.setText(URLDecoder.decode(pw,"UTF-8"));}}
            toast("Data QR dimasukkan. Tekan HUBUNGKAN.");
        }catch(Exception e){toast("Format QR tidak dikenali; isi IP dan password secara manual.");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,@Nullable Intent data){
        IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(result!=null&&result.getContents()!=null){parseQr(result.getContents());return;}
        super.onActivityResult(requestCode,resultCode,data);
    }
    private void toast(String s){main.post(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    @Override protected void onDestroy(){stopPolling();if(ws!=null)ws.close();super.onDestroy();}
}
