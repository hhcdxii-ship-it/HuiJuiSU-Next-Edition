import urllib.request, json, os, subprocess

repo = "hhcdxii-ship-it/HuiJuiSU-Next-Edition"
api_url = f"https://api.github.com/repos/{repo}/releases/latest"

print("🔍 正在尝试获取最新 Release...")
try:
    req = urllib.request.Request(api_url, headers={'User-Agent': 'Mozilla/5.0'})
    data = json.loads(urllib.request.urlopen(req, timeout=15).read().decode())
    
    if 'tag_name' not in data:
        print("⚠️ 还没生成 Release 包，可能 build-app 还在跑，等 2 分钟再跑一次！")
    else:
        tag = data['tag_name']
        print(f"✅ 发现最新版本: {tag}")
        assets = data.get('assets', [])
        if not assets:
            print("⚠️ Release 里没有附件，去 Actions 页面下 Artifacts！")
        else:
            original_url = assets[0]['browser_download_url']
            # 使用国内加速镜像下载
            mirror_url = "https://ghproxy.net/" + original_url
            
            print(f"📥 正在通过极速节点下载...")
            req = urllib.request.Request(mirror_url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=60) as resp, open("HuiJuiSU.apk", 'wb') as f:
                f.write(resp.read())
                
            win_desktop = "/mnt/c/Users/Admin/Desktop"
            if os.path.exists(win_desktop):
                subprocess.run(["cp", "HuiJuiSU.apk", win_desktop])
                print("🎉 完美！APK 已经直接复制到你的 Windows 桌面了！")
                subprocess.run(["explorer.exe", win_desktop])
            else:
                print("🎉 完美！APK 已下载到当前目录。")
                
except Exception as e:
    print(f"❌ 失败: {e}")
    print("如果仍然失败，请去 Actions 页面，手动点开 Artifacts 下载！")
