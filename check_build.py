import urllib.request, json, sys

repo = "hhcdxii-ship-it/HuiJuiSU-Next-Edition"
url = f"https://api.github.com/repos/{repo}/actions/runs?per_page=1"

try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    data = json.loads(urllib.request.urlopen(req).read().decode())
    
    if 'workflow_runs' not in data or len(data['workflow_runs']) == 0:
        print("⚠️ 没有找到任何构建记录")
        sys.exit(0)
        
    run = data['workflow_runs'][0]
    print(f"📌 提交信息: {run['display_title']}")
    print(f"⚙️ 状态: {run['status']}")
    print(f"🏁 结论: {run['conclusion']}")
    
    if run['status'] == 'completed':
        if run['conclusion'] == 'success':
            print("\n✅ App 编译成功！")
            print("你可以直接去这个网址下载 APK（如果你有代理的话）：")
            print(run['html_url'])
        else:
            print("\n❌ 编译失败，请去网页看日志或使用 gh 命令。")
    else:
        print("\n⏳ 还在拼命编译中，喝口水再来看...")
except Exception as e:
    print("获取状态失败，大概率是 WSL 网络连不上 GitHub API。请检查代理。")
    print(e)
