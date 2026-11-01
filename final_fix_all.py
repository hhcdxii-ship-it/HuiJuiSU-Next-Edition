import os
import re
from pathlib import Path

print("🚀 开始执行终极缝合：重建 ZakoText 并修复全项目引用...")

# 1. 删除之前拆分出来的多余文件
utils_zako = Path('app/src/main/java/com/huijui/sunext/edition/core/utils/ZakoString.kt')
if utils_zako.exists():
    utils_zako.unlink()
    print("✅ 已清理误建的核心文件")

# 2. 全量重写 ZakoText.kt，包含字典、纯函数和 Composable
zako_path = Path('app/src/main/java/com/huijui/sunext/edition/core/ui/language/ZakoText.kt')
zako_path.parent.mkdir(parents=True, exist_ok=True)

zako_content = '''package com.huijui.sunext.edition.core.ui.language

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.huijui.sunext.edition.core.utils.AppSettings
import kotlin.math.abs

private val zakoDict = mapOf(
    "主页" to "杂鱼的狗窝", "工具" to "玩具箱", "超管" to "大小姐", "模块" to "插模块", "设置" to "调教",
    "🚀 一键智能提取并刷入" to "把破镜像塞进去！", "工作中 (HUI)..." to "本小姐在干活啦...",
    "HuiJuiSU Next - 引擎重载" to "本小姐的底层重载", "纯血独立 / 全自动 LKM 注入" to "纯血杂鱼，自动塞入内核"
)

fun zakoString(text: String): String {
    if (AppSettings.currentLanguage != "ZAKO") return text
    zakoDict[text]?.let { return it }
    
    // V6 引擎正则拦截
    if (text.contains("[模块] 匹配内置纯血内核: ")) {
        return text.replace("[模块] 匹配内置纯血内核: ", "本小姐匹配到了这只内核: ")
    }
    if (text.contains("[读取] 镜像读取成功，大小: ")) {
        return text.replace("[读取] 镜像读取成功，大小: ", "偷到了底层镜像，才").replace("MB，准备塞入杂鱼内核...", "MB捏~")
    }
    if (text.contains("执行异常: ")) {
        return text.replace("执行异常: ", "杂鱼你干了什么破事: ")
    }

    // 防重复智能兜底
    if (text.matches(Regex(""".*[\\u4e00-\\u9fa5].*"""))) {
        val hash = abs(text.hashCode())
        val suffixes = listOf(" 懂不懂啊？", " 真是拿你没办法~", " 杂鱼就是杂鱼。", " 哼，本小姐才不想管你呢！")
        return "$text" + suffixes[hash % suffixes.size]
    }
    return text
}

@Composable
fun ZakoText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null, fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = zakoString(text), modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, style = style
    )
}
'''
zako_path.write_text(zako_content)
print("✅ ZakoText.kt 已全量复原并修复！")

# 3. 全局修复 import 和 MainActivity 的 onBack
base_dir = Path('app/src/main/java')
for kt in base_dir.rglob('*.kt'):
    c = kt.read_text()
    orig = c
    
    # 统一把错误的引用改回来
    c = c.replace('import com.huijui.sunext.edition.core.utils.zakoString', 'import com.huijui.sunext.edition.core.ui.language.zakoString')
    c = c.replace('ZakoZakoText', 'ZakoText')
    
    # 给 MainActivity 擦屁股
    if kt.name == 'MainActivity.kt':
        c = re.sub(r'CoreOverrideScreen\([^)]*onBack[^)]*\)', 'CoreOverrideScreen()', c)
        # 删掉孤立的 onBack
        lines = c.split('\n')
        c = '\n'.join([l for l in lines if 'onBack' not in l])

    if c != orig:
        kt.write_text(c)

print("🎉 终极修复完成！直接执行 ./gradlew assembleDebug")
