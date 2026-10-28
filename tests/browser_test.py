import os, hashlib, time
os.environ["PLAYWRIGHT_BROWSERS_PATH"]="/opt/pw-browsers"
from playwright.sync_api import sync_playwright
P=8080; T="TESTTOKEN123"; errs=[]
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={"width":420,"height":860})
    pg.on("console",lambda m: errs.append(m.text) if m.type=="error" else None); pg.on("pageerror",lambda e: errs.append(str(e)))
    cnt={"n":0}
    def flaky(route):
        cnt["n"]+=1
        if cnt["n"]==2: route.abort("connectionreset")   # kill the 2nd chunk
        else: route.continue_()
    pg.route("**/api/upload/*",flaky)
    pg.goto(f"http://127.0.0.1:{P}/?t={T}"); pg.wait_for_selector("#st.ok",timeout=8000)
    print("status:",pg.inner_text("#st"),"| url cleaned:",pg.url.endswith("/"), "| device:", pg.inner_text(".sub"))
    pg.set_input_files("#pick","/tmp/st/big.bin")
    for _ in range(300):
        t=pg.inner_text("#up .meta") if pg.query_selector("#up .meta") else ""
        if t.startswith("Saved on TV"): break
        time.sleep(0.2)
    print("meta:",pg.inner_text("#up .meta"),"| chunk requests seen:",cnt["n"])
    time.sleep(0.3); pg.screenshot(path="/tmp/st/page.png")
    pg.wait_for_selector("#dl a.btn"); 
    with pg.expect_download() as d: pg.click("#dl a.btn")
    path=d.value.path(); same=hashlib.md5(open(path,"rb").read()).hexdigest()==hashlib.md5(open("/tmp/st/shared.bin","rb").read()).hexdigest()
    print("download filename:",d.value.suggested_filename,"| identical:",same)
    print("console errors:",errs); b.close()
r=[f for f in os.listdir("/tmp/st/recv")]; print("recv:",r, "identical:", hashlib.md5(open("/tmp/st/recv/"+r[0],"rb").read()).hexdigest()==hashlib.md5(open("/tmp/st/big.bin","rb").read()).hexdigest())
