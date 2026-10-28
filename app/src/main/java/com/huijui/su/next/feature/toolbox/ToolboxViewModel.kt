package com.huijui.su.next.feature.toolbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huijui.su.next.core.root.HuijuiShell
import com.huijui.su.next.core.root.ModuleEngine
import kotlinx.coroutines.launch

class ToolboxViewModel : ViewModel() {
    var rootStatus by mutableStateOf("正在检测纯血引擎...")
    var moduleCount by mutableStateOf(0)

    init {
        viewModelScope.launch {
            val isRoot = HuijuiShell.isRooted()
            rootStatus = if (isRoot) "真·纯血引擎已就绪" else "未获取 Root 权限"
            
            if (isRoot) {
                val modules = ModuleEngine.getInstalledModules()
                moduleCount = modules.size
            }
        }
    }
}
