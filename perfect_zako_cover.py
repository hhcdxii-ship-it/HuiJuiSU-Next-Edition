import os
import re
from pathlib import Path

print("🚀 正在执行 ZakoText 终极全量覆盖...")

zako_path = Path('app/src/main/java/com/huijui/sunext/edition/core/ui/language/ZakoText.kt')
content = zako_path.read_text()

# ==========================================
# 1. 扩充 zakoDict 字典
# ==========================================
new_dict_entries = '''
    // 🚀 V6 引擎专属词条
    "🚀 一键智能提取并刷入" to "把破镜像塞进去！",
    "工作中 (HUI)..." to "本小姐在干活啦...",
    "HuiJuiSU Next - 引擎重载" to "本小姐的底层重载",
    "纯血独立 / 全自动 LKM 注入" to "纯血杂鱼，自动塞入内核",
    "哼，本小姐才不想管你呢！正在唤醒 hsu 守护进程..." to "哼，居然要本小姐亲自动手！",
    "[环境] 正在执行底层提取，别乱动哦！" to "[环境] 正在偷你的底层文件，别动哦！",
    "[策略] 正在备份原厂镜像，以防你这个杂鱼搞坏手机..." to "[策略] 搞坏了本小姐可不管，随便备份一下...",
    "[修补] 重新打包镜像，正在写回底层，准备见证奇迹！" to "[修补] 塞进去了，准备重启挨揍吧！",
    "✅ 工作中(HUI)完成！重启后本小姐给你 Root 权限！" to "✅ 搞定了！重启后本小姐赏你点权限！",
    "❌ 杂鱼就是杂鱼，连底层分区节点都找不到！" to "❌ 连个分区都找不到，纯废物！",
    "❌ 提取失败！快检查有没有给本小姐 Root 权限！" to "❌ 敢不给本小姐 Root 权限？活该！",
    "❌ 选错分区了吧？这玩意大于20MB，绝对要变砖，本小姐才不干呢！" to "❌ 瞎选什么？要变砖了！",
    "❌ 底层 C++ 引擎罢工了！" to "❌ 底层崩了，不关本小姐的事！",
    "❌ 写入失败！请使用备份包救砖！" to "❌ 彻底玩脱了吧，用备份去！",
'''

# 插入到 zakoDict 的最后
content = content.replace('    // 🚀 其他零星词', new_dict_entries + '\n    // 🚀 其他零星词')

# ==========================================
# 2. 增强 zakoString 的正则拦截
# ==========================================
new_regex_logic = '''
    // 🚀 V6 引擎正则拦截
    if (text.contains("[模块] 匹配内置纯血内核: ")) {
        return text.replace("[模块] 匹配内置纯血内核: ", "本小姐匹配到了这只内核: ")
    }
    if (text.contains("[引擎] 激活纯血 hsu 引擎，接管 ")) {
        return text.replace("[引擎] 激活纯血 hsu 引擎，接管 ", "本小姐接管了底层: ")
    }
    if (text.contains("[读取] 镜像读取成功，大小: ")) {
        return text.replace("[读取] 镜像读取成功，大小: ", "偷到了底层镜像，才").replace("MB，准备塞入杂鱼内核...", "MB捏~")
    }
    if (text.contains("[修补] 正在向 ramdisk 注入 init 替换与 .ko 模块...")) {
        return "往底层塞点好东西..."
    }
    if (text.contains("执行异常: ")) {
        return text.replace("执行异常: ", "杂鱼你干了什么破事: ")
    }
'''

# 插入到防重复智能兜底之前
content = content.replace('    // 3. 🚀 防重复智能兜底', new_regex_logic + '\n    // 3. 🚀 防重复智能兜底')
zako_path.write_text(content)
print("✅ ZakoText.kt 字典与正则已全面扩充！")

# ==========================================
# 3. 修改 HsudInstaller.kt，所有日志全部走 zakoString
# ==========================================
installer_path = Path('app/src/main/java/com/huijui/sunext/edition/core/root/HsudInstaller.kt')
if installer_path.exists():
    ins_content = installer_path.read_text()
    
    # 引入 zakoString
    if 'import com.huijui.sunext.edition.core.ui.language.zakoString' not in ins_content:
        ins_content = ins_content.replace('import java.io.File', 'import java.io.File\nimport com.huijui.sunext.edition.core.ui.language.zakoString')
        
    # 所有的 emit 都包裹上 zakoString
    ins_content = re.sub(r'emit\(PatchState\.(Progress|Success|Error)\((.*?)\)\)', r'emit(PatchState.\1(zakoString(\2)))', ins_content)
    
    installer_path.write_text(ins_content)
    print("✅ HsudInstaller.kt 底层输出已全量套壳 zakoString！")

# ==========================================
# 4. 修改 CoreOverrideScreen.kt，UI 全部走 ZakoText
# ==========================================
ui_path = Path('app/src/main/java/com/huijui/sunext/edition/feature/toolbox/CoreOverrideScreen.kt')
if ui_path.exists():
    ui_content = ui_path.read_text()
    
    # 引入 ZakoText
    if 'import com.huijui.sunext.edition.core.ui.language.ZakoText' not in ui_content:
        ui_content = ui_content.replace('import androidx.compose.material3.*', 'import androidx.compose.material3.*\nimport com.huijui.sunext.edition.core.ui.language.ZakoText')
        
    # 把普通的 Text() 全部换成 ZakoText()
    # 注意避开按钮里面的 Text
    ui_content = ui_content.replace('Text(\n                text = "HuiJuiSU Next - 引擎重载",', 'ZakoText(\n                "HuiJuiSU Next - 引擎重载",')
    ui_content = ui_content.replace('Text(\n                text = "纯血独立 / 全自动 LKM 注入",', 'ZakoText(\n                "纯血独立 / 全自动 LKM 注入",')
    
    # 控制台输出这里，因为已经用 Flow 接过了 zakoString，不需要再套 ZakoText
    # 但是按钮的状态需要换
    ui_content = ui_content.replace('Text(\n                    text = if (isProcessing) "工作中 (HUI)..." else "🚀 一键智能提取并刷入",', 'ZakoText(\n                    if (isProcessing) "工作中 (HUI)..." else "🚀 一键智能提取并刷入",')
    
    # 初始文案换成zakoString
    ui_content = ui_content.replace('mutableStateOf("> " + zakoString("杂鱼，准备好接受本小姐的调教了吗？"))', 'mutableStateOf("> " + zakoString("杂鱼，准备好接受本小姐的调教了吗？"))')
    
    ui_path.write_text(ui_content)
    print("✅ CoreOverrideScreen.kt 已彻底替换为 ZakoText 组件！")

print("🎉 彻底优化覆盖完成！直接执行 ./gradlew assembleDebug！")
