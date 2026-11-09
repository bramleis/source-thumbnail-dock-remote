package id.kominfo.sorong.sourcethumbnailremote;

import android.app.*;import android.os.*;import android.graphics.Color;import android.view.*;import android.widget.*;import android.text.InputType;import android.content.*;
import com.google.gson.*;import org.java_websocket.client.WebSocketClient;import org.java_websocket.handshake.ServerHandshake;
import com.google.zxing.integration.android.*;
import java.net.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;

public class MainActivity extends Activity {
  LinearLayout root, scenesBox, sourcesBox; TextView status, program, preview; EditText host,port,password; Button connectBtn; WebSocketClient ws;
  int nextId=1; Map<String,String> pending=new HashMap<>(); Map<String,String> pendingScene=new HashMap<>(); String rpcPassword=""; boolean authenticated=false; String currentProgram="",currentPreview="",selectedSourceScene=""; Handler handler=new Handler(Looper.getMainLooper());
  final int BG=Color.rgb(239,244,240), DARK=Color.rgb(24,38,32), GREEN=Color.rgb(166,200,63), RED=Color.rgb(220,91,68);
  @Override public void onCreate(Bundle b){super.onCreate(b); buildUI();}
  TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(DARK);t.setTextSize(size);t.setPadding(4,8,4,8);return t;}
  Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
  void buildUI(){
    ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(14,12,14,18);root.setBackgroundColor(BG);scroll.addView(root);setContentView(scroll);
    TextView title=label("SOURCE THUMBNAIL DOCK",22);title.setTextColor(DARK);title.setTypeface(null,1);root.addView(title);root.addView(label("REMOTE CONTROL • OBS WEBSOCKET",12));
    status=label("● Belum terhubung",14);status.setTextColor(RED);root.addView(status);
    host=new EditText(this);host.setSingleLine();host.setHint("Alamat IP komputer OBS (contoh 192.168.1.10)");host.setText("192.168.1.10");root.addView(host);
    port=new EditText(this);port.setSingleLine();port.setInputType(InputType.TYPE_CLASS_NUMBER);port.setHint("Port WebSocket");port.setText("4455");root.addView(port);
    password=new EditText(this);password.setSingleLine();password.setHint("Password OBS WebSocket");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(password);
    LinearLayout actions=new LinearLayout(this);connectBtn=btn("Hubungkan");actions.addView(connectBtn,new LinearLayout.LayoutParams(0,-2,1));Button scan=btn("Scan QR Code");actions.addView(scan,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
    connectBtn.setOnClickListener(v->{if(ws!=null&&ws.isOpen())disconnect();else connect();});
    scan.setOnClickListener(v->{IntentIntegrator ii=new IntentIntegrator(this);ii.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);ii.setPrompt("Scan QR Code OBS WebSocket");ii.setBeepEnabled(false);ii.setOrientationLocked(false);ii.initiateScan();});
    root.addView(label("PROGRAM & PREVIEW",18));program=label("PROGRAM: —",14);preview=label("PREVIEW: —",14);root.addView(program);root.addView(preview);
    root.addView(label("DAFTAR SCENE",18));scenesBox=new LinearLayout(this);scenesBox.setOrientation(1);root.addView(scenesBox);
    root.addView(label("SOURCE PADA SCENE AKTIF",18));sourcesBox=new LinearLayout(this);sourcesBox.setOrientation(1);root.addView(sourcesBox);
    Button refresh=btn("Muat Ulang Scene & Source");refresh.setOnClickListener(v->loadScenes());root.addView(refresh);
    TextView note=label("Catatan: HP dan komputer OBS harus berada pada jaringan Wi-Fi/LAN yang sama. Aktifkan OBS WebSocket dan izinkan koneksi jaringan di firewall Windows.",12);root.addView(note);
  }
  void connect(){try{String h=host.getText().toString().trim();if(h.isEmpty())throw new Exception("Alamat IP belum diisi");int p=Integer.parseInt(port.getText().toString().trim());rpcPassword=password.getText().toString();URI uri=new URI("ws://"+h+":"+p);
    status.setText("● Menghubungkan...");ws=new WebSocketClient(uri){@Override public void onOpen(ServerHandshake hand){runOnUiThread(()->status.setText("● Terhubung, menunggu autentikasi..."));}
      @Override public void onMessage(String msg){handleMessage(msg);}
      @Override public void onClose(int c,String r,boolean remote){runOnUiThread(()->{authenticated=false;pending.clear();pendingScene.clear();status.setText("● Koneksi terputus: "+r);connectBtn.setText("Hubungkan");});}
      @Override public void onError(Exception e){runOnUiThread(()->status.setText("● Error: "+e.getMessage()));}};ws.connect();
    }catch(Exception e){toast("Periksa alamat IP, port, dan password: "+e.getMessage());}}
  void disconnect(){try{ws.close();}catch(Exception ignored){}authenticated=false;pending.clear();pendingScene.clear();connectBtn.setText("Hubungkan");status.setText("● Terputus");}
  void send(String type,JsonObject data){ send(type,data,null); }
  void send(String type,JsonObject data,String sceneContext){
    if(ws==null||!ws.isOpen()||!authenticated){runOnUiThread(()->toast("Belum terhubung ke OBS."));return;}
    String id="android-"+(nextId++);
    JsonObject rd=new JsonObject();
    rd.addProperty("requestType",type);
    rd.addProperty("requestId",id);
    rd.add("requestData",data==null?new JsonObject():data);
    JsonObject req=new JsonObject();
    req.addProperty("op",6);
    req.add("d",rd);
    pending.put(id,type);
    if(sceneContext!=null)pendingScene.put(id,sceneContext);
    handler.postDelayed(()->{if(pending.remove(id)!=null){pendingScene.remove(id);status.setText("● OBS terhubung • Tidak ada respons untuk "+type);toast("OBS tidak merespons perintah "+type);}},7000);
    try{ws.send(req.toString());}
    catch(Exception e){pending.remove(id);runOnUiThread(()->toast("Gagal mengirim "+type+": "+e.getMessage()));}
  }
  void handleMessage(String msg){try{JsonObject o=JsonParser.parseString(msg).getAsJsonObject();int op=o.get("op").getAsInt();JsonObject d=o.getAsJsonObject("d");
    if(op==0){JsonObject identify=new JsonObject();identify.addProperty("rpcVersion",1);identify.addProperty("eventSubscriptions",0x7fffffff);if(d.has("authentication")){JsonObject a=d.getAsJsonObject("authentication");String salt=a.get("salt").getAsString(),challenge=a.get("challenge").getAsString();String secret=sha256(rpcPassword+salt);String auth=Base64.getEncoder().encodeToString(hexBytes(sha256(secret+challenge)));identify.addProperty("authentication",auth);}JsonObject packet=new JsonObject();packet.addProperty("op",1);packet.add("d",identify);ws.send(packet.toString());}
    else if(op==2){authenticated=true;runOnUiThread(()->{status.setText("● OBS TERHUBUNG");status.setTextColor(GREEN);connectBtn.setText("Putuskan");});send("GetSceneList",new JsonObject());send("GetCurrentProgramScene",new JsonObject());send("GetCurrentPreviewScene",new JsonObject());}
    else if(op==7){
      String requestId=d.has("requestId")?d.get("requestId").getAsString():"";
      String type=pending.remove(requestId);
      String responseScene=pendingScene.remove(requestId);
      JsonObject response=d.has("responseData")&&!d.get("responseData").isJsonNull()?d.getAsJsonObject("responseData"):new JsonObject();
      JsonObject result=d.has("requestStatus")?d.getAsJsonObject("requestStatus"):new JsonObject();
      boolean ok=result.has("result")&&result.get("result").getAsBoolean();
      if(!ok){
        String reason=result.has("comment")?result.get("comment").getAsString():"OBS menolak perintah (tanpa keterangan).";
        runOnUiThread(()->{status.setText("● OBS terhubung • Perintah gagal: "+reason);status.setTextColor(RED);toast((type==null?"Permintaan":type)+" gagal: "+reason);});
        return;
      }
      if(type==null)return;
      if(type.equals("GetSceneList"))showScenes(response);
      if(type.equals("GetCurrentProgramScene")){currentProgram=response.has("currentProgramSceneName")?response.get("currentProgramSceneName").getAsString():"";runOnUiThread(()->program.setText("PROGRAM: "+currentProgram));}
      if(type.equals("GetCurrentPreviewScene")){currentPreview=response.has("currentPreviewSceneName")?response.get("currentPreviewSceneName").getAsString():"";runOnUiThread(()->preview.setText("PREVIEW: "+currentPreview));}
      if(type.equals("GetSceneItemList"))showSources(response,responseScene==null?selectedSourceScene:responseScene);
      if(type.equals("SetCurrentProgramScene")||type.equals("SetCurrentPreviewScene")||type.equals("SetSceneItemEnabled")){
        runOnUiThread(()->{status.setText("● OBS TERHUBUNG • Perintah berhasil");status.setTextColor(GREEN);});
      }
    }
    else if(op==5){String ev=d.get("eventType").getAsString();if(ev.equals("CurrentProgramSceneChanged")){currentProgram=d.getAsJsonObject("eventData").get("sceneName").getAsString();runOnUiThread(()->program.setText("PROGRAM: "+currentProgram));}if(ev.equals("CurrentPreviewSceneChanged")){currentPreview=d.getAsJsonObject("eventData").get("sceneName").getAsString();runOnUiThread(()->preview.setText("PREVIEW: "+currentPreview));}}
  }catch(Exception e){runOnUiThread(()->status.setText("● Respons OBS tidak dikenali"));}}
  void loadScenes(){send("GetSceneList",new JsonObject());send("GetCurrentProgramScene",new JsonObject());send("GetCurrentPreviewScene",new JsonObject());}
  void showScenes(JsonObject r){runOnUiThread(()->{scenesBox.removeAllViews();JsonArray a=r.getAsJsonArray("scenes");for(JsonElement el:a){String name=el.getAsJsonObject().get("sceneName").getAsString();LinearLayout row=new LinearLayout(this);Button p=btn("PROGRAM • "+name);p.setOnClickListener(v->{JsonObject x=new JsonObject();x.addProperty("sceneName",name);send("SetCurrentProgramScene",x);});row.addView(p,new LinearLayout.LayoutParams(0,-2,1));Button pr=btn("PREVIEW");pr.setOnClickListener(v->{JsonObject x=new JsonObject();x.addProperty("sceneName",name);send("SetCurrentPreviewScene",x);});row.addView(pr);Button src=btn("SOURCE");src.setOnClickListener(v->{selectedSourceScene=name;JsonObject x=new JsonObject();x.addProperty("sceneName",name);send("GetSceneItemList",x,name);});row.addView(src);scenesBox.addView(row);}});}
  void showSources(JsonObject r,String sceneName){runOnUiThread(()->{sourcesBox.removeAllViews();TextView heading=label("SOURCE PADA SCENE: "+sceneName,15);heading.setTypeface(null,1);sourcesBox.addView(heading);JsonArray a=r.getAsJsonArray("sceneItems");for(JsonElement el:a){JsonObject it=el.getAsJsonObject();String name=it.get("sourceName").getAsString();int id=it.get("sceneItemId").getAsInt();boolean enabled=it.get("sceneItemEnabled").getAsBoolean();CheckBox cb=new CheckBox(this);cb.setText(name);cb.setTextColor(DARK);cb.setChecked(enabled);cb.setOnCheckedChangeListener((v,on)->{JsonObject d=new JsonObject();d.addProperty("sceneName",sceneName);d.addProperty("sceneItemId",id);d.addProperty("sceneItemEnabled",on);send("SetSceneItemEnabled",d);});sourcesBox.addView(cb);}if(a.size()==0)sourcesBox.addView(label("Tidak ada Source pada Scene ini.",13));});}
  String sha256(String s)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");return Base64.getEncoder().encodeToString(md.digest(s.getBytes(StandardCharsets.UTF_8)));}
  byte[] hexBytes(String b64){return Base64.getDecoder().decode(b64);}
  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(result!=null&&result.getContents()!=null){parseQr(result.getContents());return;}super.onActivityResult(requestCode,resultCode,data);}
  void parseQr(String raw){try{String s=raw.trim();if(s.startsWith("{")){JsonObject j=JsonParser.parseString(s).getAsJsonObject();if(j.has("host"))host.setText(j.get("host").getAsString());if(j.has("port"))port.setText(j.get("port").getAsString());if(j.has("password"))password.setText(j.get("password").getAsString());}
    else {if(s.startsWith("ws://"))s=s.substring(5);else if(s.startsWith("obsws://"))s=s.substring(8);String authority=s.split("/",2)[0];String[] hp=authority.split(":");host.setText(hp[0]);if(hp.length>1)port.setText(hp[1]);if(s.contains("password=")){String pw=s.substring(s.indexOf("password=")+9).split("[& ]",2)[0];password.setText(URLDecoder.decode(pw,"UTF-8"));}}toast("Data QR dimasukkan. Tekan Hubungkan.");}catch(Exception e){toast("Format QR belum dikenali. Masukkan IP dan password secara manual.");}}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
