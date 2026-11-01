from pathlib import Path

print("🚀 正在修复 protected 报错，换用 exists 探针...")

path = Path('core/root/native/shim/jni_wrapper.cpp')
cpp = path.read_text()

# 把报错的 size() 换成判断 init 文件是否存在
cpp = cpp.replace(
    'if (probe_cpio) { fprintf(probe_cpio, "Entries loaded: %zu\\n", cpio.entries.size()); fclose(probe_cpio); }',
    'if (probe_cpio) { fprintf(probe_cpio, "Init exists: %s\\n", cpio.exists("init") ? "true" : "false"); fclose(probe_cpio); }'
)

path.write_text(cpp)
print("✅ 替换完毕！")
