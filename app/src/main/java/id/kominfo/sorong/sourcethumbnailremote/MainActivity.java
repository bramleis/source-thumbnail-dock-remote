if(data==null||data.isEmpty())return;int comma=data.indexOf(',');String b64=comma>=0?data.substring(comma+1):data;
            byte[] bytes=Base64.decode(b64,Base64.DEFAULT);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)return;
            main.post(()->{ImageView target;if("__PREVIEW__".equals(key))target=previewImage;else if("__PROGRAM__".equals(key))target=programImage;else target=sourceImages.get(key);if(target!=null)target.setImageBitmap(bitmap);});
        }catch(Exception ignored){}finally{main.post(()->{screenshotBusy=false;dispatchScreenshot();});}
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
            screenshotQueue.clear();
            if(!currentPreview.isEmpty())queueScreenshot(currentPreview,"__PREVIEW__");
            if(!currentProgram.isEmpty())queueScreenshot(currentProgram,"__PROGRAM__");
            for(Map.Entry<String,String> e:new ArrayList<>(sourceNames.entrySet()))queueScreenshot(e.getValue(),e.getKey());
            main.postDelayed(this,5000);
        }};
        main.post(pollRunnable);
    }
    private void stopPolling(){polling=false;if(pollRunnable!=null)main.removeCallbacks(pollRunnable);screenshotQueue.clear();screenshotBusy=false;}
    private void parseQr(String raw){
        try{
            String s=raw.trim();
            if(s.startsWith("{")){JsonObject j=JsonParser.parseString(s).getAsJsonObject();if(j.has("host"))hostInput.setText(j.get("host").getAsString());if(j.has("port"))portInput.setText(j.get("port").getAsString());if(j.has("password"))passwordInput.setText(j.get("password").getAsString());}
            else{if(s.startsWith("ws://"))s=s.substring(5);else if(s.startsWith("obsws://"))s=s.substring(8);String authority=s.split("/",2)[0];String[] hp=authority.split(":");hostInput.setText(hp[0]);if(hp.length>1)portInput.setText(hp[1]);if(s.contains("password=")){String pw=s.substring(s.indexOf("password=")+9).split("[& ]",2)[0];passwordInput.setText(URLDecoder.decode(pw,"UTF-8"));}}
            toast("Data QR dimasukkan. Tekan HUBUNGKAN.");
        }catch(Exception e){toast("Format QR tidak dikenali; isi IP dan password secara manual.");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(result!=null&&result.getContents()!=null){parseQr(result.getContents());return;}
        super.onActivityResult(requestCode,resultCode,data);
    }
    private void toast(String s){main.post(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    @Override protected void onDestroy(){stopPolling();if(ws!=null)ws.close();super.onDestroy();}
}
