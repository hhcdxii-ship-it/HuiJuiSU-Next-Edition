package com.huijui.sunext.edition.feature.superuser

import com.huijui.sunext.edition.core.ui.language.zakoString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huijui.sunext.edition.core.root.SuperUserManager

@Composable
fun SuperUserScreen() {
    var allowlist by remember { mutableStateOf(SuperUserManager.getAllowlist()) }
    
    // 这里做一个简易的假数据列表，代表系统中申请 Root 的应用
    val mockApps = listOf("com.termux", "com.android.shell", "com.example.hacker")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = zakoString("超级用户管理"),
            fontSize = 24.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = zakoString("哼，这些杂鱼应用想申请本小姐的 Root 权限？"),
            fontSize = 14.sp,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(mockApps) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(Color(0xFF222222)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = app, color = Color.White, modifier = Modifier.padding(16.dp))
                    
                    Switch(
                        checked = allowlist.contains(app),
                        onCheckedChange = { isChecked ->
                            val newList = allowlist.toMutableSet()
                            if (isChecked) newList.add(app) else newList.remove(app)
                            allowlist = newList
                            SuperUserManager.saveAllowlist(newList)
                        }
                    )
                }
            }
        }
    }
}
