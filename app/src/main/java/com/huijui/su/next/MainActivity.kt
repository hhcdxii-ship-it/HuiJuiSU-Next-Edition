package com.huijui.su.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.huijui.su.next.core.ui.theme.HuijuiTheme
import com.huijui.su.next.feature.toolbox.ToolboxScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HuijuiTheme {
                ToolboxScreen()
            }
        }
    }
}
