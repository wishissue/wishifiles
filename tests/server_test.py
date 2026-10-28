import http.client, json, hashlib, os, time, socket
P=int(open("/tmp/st/server.log").read().split("PORT ")[1].split()[0]); T="TESTTOKEN123"; H={"X-Wishi-Token":T}
def req(m,u,body=None,h=H,port=P):
    c=http.client.HTTPConnection("127.0.0.1",port,timeout=10); c.request(m,u,body=body,headers=dict(h)); r=c.getresponse(); d=r.read(); return r.status,dict(r.getheaders()),d
ok=lambda n,c: print(("PASS " if c else "FAIL ")+n)
s,h,d=req("GET","/",h={}); ok("no token -> 401",s==401)
s,h,d=req("GET","/?t="+T,h={}); ok("page 200 + nonce CSP + css + device escaped", s==200 and "nonce-" in h["Content-Security-Policy"] and b"--p:#51a9e8" in d and b"Test &lt;TV&gt;" in d and b"{{" not in d)
nonce=h["Content-Security-Policy"].split("nonce-")[1].split("'")[0]; ok("nonce injected in page", nonce.encode() in d)
s,h,d=req("GET","/api/list"); j=json.loads(d); ok("list shared",s==200 and len(j["shared"])==1 and j["shared"][0]["size"]==5000000)
data=os.urandom(3_000_000); id=hashlib.sha1(data).hexdigest()[:16]
s,h,d=req("POST",f"/api/upload?id={id}&size={len(data)}&name=..%2F..%2Fevil%3A%22x%22.bin"); j=json.loads(d); ok("init offset 0",s==200 and j["offset"]==0)
ok("name sanitised", "/" not in j["name"] and ".." not in j["name"].replace("_.","") and ":" not in j["name"])
s,h,d=req("PUT",f"/api/upload/{id}?offset=0",body=data[:1_000_000]); ok("chunk1",s==200 and json.loads(d)["offset"]==1_000_000)
s,h,d=req("PUT",f"/api/upload/{id}?offset=500",body=data[500:600]); ok("wrong offset -> 409 with true offset",s==409 and json.loads(d)["offset"]==1_000_000)
# interrupted chunk: send headers + partial body then drop connection
so=socket.create_connection(("127.0.0.1",P)); so.sendall((f"PUT /api/upload/{id}?offset=1000000 HTTP/1.1\r\nHost: x\r\nX-Wishi-Token: {T}\r\nContent-Length: 1000000\r\n\r\n").encode()+data[1_000_000:1_400_000]); time.sleep(0.4); so.close(); time.sleep(0.6)
s,h,d=req("POST",f"/api/upload?id={id}&size={len(data)}&name=evil.bin"); off=json.loads(d)["offset"]; ok("resume after drop (offset=%d, partial kept)"%off, 1_000_000<off<=1_400_000)
s,h,d=req("PUT",f"/api/upload/{id}?offset={off}",body=data[off:]); j=json.loads(d); ok("final chunk done",s==200 and j["done"])
f=os.path.join("/tmp/st/recv",j["name"]); ok("file identical + confined to recv dir "+j["name"], open(f,"rb").read()==data and os.path.dirname(os.path.abspath(f))=="/tmp/st/recv")
ok("no leftover .wishipart", not [x for x in os.listdir("/tmp/st/recv") if x.endswith(".wishipart")])
s,h,d=req("POST","/api/upload?id=zz&size=1&name=a"); ok("bad id rejected",s==400)
s,h,d=req("POST",f"/api/upload?id=00000000aa&size=0&name=empty.txt"); ok("zero-byte upload",json.loads(d)["done"] and os.path.exists("/tmp/st/recv/empty.txt"))
s,h,d=req("POST",f"/api/upload?id=00000000bb&size={10**15}&name=huge.bin"); ok("insufficient storage -> 507 message", s==507 and "free space" in json.loads(d)["error"])
sid=json.loads(req("GET","/api/list")[2])["shared"][0]["id"]; src=open("/tmp/st/shared.bin","rb").read()
s,h,d=req("GET","/api/dl/"+sid); ok("download full",s==200 and d==src and "attachment" in h["Content-Disposition"])
s,h,d=req("GET","/api/dl/"+sid,h={**H,"Range":"bytes=100-199"}); ok("range 206",s==206 and d==src[100:200] and h["Content-Range"]=="bytes 100-199/5000000")
s,h,d=req("GET","/api/dl/"+sid,h={**H,"Range":"bytes=-50"}); ok("suffix range",s==206 and d==src[-50:])
s,h,d=req("GET","/api/dl/"+sid,h={**H,"Range":"bytes=9999999-"}); ok("unsatisfiable 416",s==416)
s,h,d=req("GET","/api/dl/../../etc/passwd"); ok("no path traversal on download",s==404)
s,h,d=req("GET","/api/dl/"+sid+"?t="+T,h={}); ok("download via ?t= token (browser link)",s==200 and len(d)==5000000)
for i in range(10): req("GET","/api/list",h={"X-Wishi-Token":"wrong"})
s,h,d=req("GET","/api/list"); ok("lockout after 10 bad tokens (429, even valid token)",s==429)
