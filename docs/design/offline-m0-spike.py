import json, sys, urllib.request, time
KEY="AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
CL={
 "ANDROID_VR":dict(ctx={"clientName":"ANDROID_VR","clientVersion":"1.65.10","androidSdkVersion":32,"osName":"Android","osVersion":"12","deviceModel":"Quest 3","deviceMake":"Oculus"},ua="com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",id=28),
 "ANDROID":dict(ctx={"clientName":"ANDROID","clientVersion":"21.03.36","androidSdkVersion":36,"osName":"Android","osVersion":"16"},ua="com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip",id=3),
 "IOS":dict(ctx={"clientName":"iOS","clientVersion":"20.11.6","deviceModel":"iPhone10,4","osVersion":"16.7.7.20H330"},ua="com.google.ios.youtube/20.11.6 (iPhone10,4; U; CPU iOS 16_7_7 like Mac OS X)",id=5),
 "TV":dict(ctx={"clientName":"TVHTML5","clientVersion":"7.20260311.12.00"},ua="Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",id=7),
 "WEB":dict(ctx={"clientName":"WEB","clientVersion":"2.20260206.01.00"},ua="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",id=1),
}
def call(client,vid):
    c=CL[client]
    ctx=dict(c["ctx"]); ctx.update(hl="en",gl="US",userAgent=c["ua"])
    body=json.dumps({"context":{"client":ctx},"videoId":vid,"racyCheckOk":True,"contentCheckOk":True}).encode()
    r=urllib.request.Request("https://www.youtube.com/youtubei/v1/player?key="+KEY+"&prettyPrint=false",body,{"Content-Type":"application/json","User-Agent":c["ua"],"X-YouTube-Client-Name":str(c["id"]),"X-YouTube-Client-Version":ctx["clientVersion"]})
    try:
        return json.load(urllib.request.urlopen(r,timeout=25))
    except Exception as e:
        return {"_err":str(e)}
def analyse(j):
    if "_err" in j: return {"status":"ERR:"+j["_err"][:40]}
    ps=j.get("playabilityStatus",{}); sd=j.get("streamingData",{}) or {}
    vd=j.get("videoDetails",{}) or {}
    af=sd.get("adaptiveFormats",[]); mf=sd.get("formats",[])
    def kind(f): return "url" if f.get("url") else "cipher" if (f.get("signatureCipher") or f.get("cipher")) else "none"
    vurl=[f for f in af if f.get("mimeType","").startswith("video") and kind(f)=="url"]
    aurl=[f for f in af if f.get("mimeType","").startswith("audio") and kind(f)=="url"]
    acip=[f for f in af if f.get("mimeType","").startswith("audio") and kind(f)=="cipher"]
    pu=[f for f in mf if kind(f)=="url"]; pc=[f for f in mf if kind(f)=="cipher"]
    return dict(status=ps.get("status"),reason=(ps.get("reason") or "")[:50],live=bool(vd.get("isLive") or vd.get("isLiveContent") and not vd.get("lengthSeconds")),
      secs=vd.get("lengthSeconds"),sabr=bool(sd.get("serverAbrStreamingUrl")),hls=bool(sd.get("hlsManifestUrl")),
      n_af=len(af),n_vurl=len(vurl),n_aurl=len(aurl),n_acip=len(acip),n_pu=len(pu),n_pc=len(pc),
      aurl0=(aurl[0] if aurl else None),pu0=(pu[0] if pu else None))
def probe(f):
    # ranged GET of 256KB to test direct URL works outside the player
    u=f["url"]
    r=urllib.request.Request(u,headers={"Range":"bytes=0-262143","User-Agent":"Mozilla/5.0"})
    try:
        t=time.time(); resp=urllib.request.urlopen(r,timeout=25); d=resp.read(); return resp.status,len(d),round(time.time()-t,2)
    except Exception as e:
        return str(e)[:40],0,0
if __name__=="__main__":
    vids=json.load(open(sys.argv[1])); out=[]
    for name,vid in vids:
        row={"name":name,"id":vid}
        for cl in CL:
            a=analyse(call(cl,vid)); 
            if a.get("aurl0"): a["probe_audio"]=probe(a["aurl0"])
            if a.get("pu0"): a["probe_prog"]=probe(a["pu0"])
            a.pop("aurl0",None); a.pop("pu0",None); row[cl]=a
        out.append(row); print(name,vid,{c:(row[c].get("status"),row[c].get("n_aurl"),row[c].get("n_acip"),row[c].get("n_pu"),row[c].get("sabr"),row[c].get("probe_audio")) for c in CL},flush=True)
        time.sleep(0.5)
    json.dump(out,open(sys.argv[2],"w"),indent=1)
