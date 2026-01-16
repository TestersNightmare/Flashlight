package com.dex7er.flashlight.activities

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dex7er.flashlight.extensions.config
import com.dex7er.flashlight.screens.BrightDisplayScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

// --- 1. 辅助工具类 (放在类外部) ---

/**
 * 根据背景颜色亮度自动选择黑色或白色作为对比色
 */
fun Int.getContrastColor(): Color {
    val composeColor = Color(this)
    return if (composeColor.luminance() > 0.5) Color.Black else Color.White
}

// --- 2. Activity 主体 ---

class BrightDisplayActivity : ComponentActivity() {
    private val viewModel by viewModels<BrightDisplayViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 强制全屏显示，延伸到状态栏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            MaterialTheme {
                ScreenContent(onRandomizeColor = {
                    // 生成随机 RGB 颜色 (Alpha 固定为 255)
                    val randomColor = android.graphics.Color.rgb(
                        Random.nextInt(256),
                        Random.nextInt(256),
                        Random.nextInt(256)
                    )

                    // 1. 同步到持久化配置 (SharedPreferences)
                    this@BrightDisplayActivity.config.brightDisplayColor = randomColor
                    // 2. 更新 ViewModel 以触发 UI 重绘
                    viewModel.updateBackgroundColor(randomColor)
                })
            }
        }
    }

    @Composable
    private fun ScreenContent(onRandomizeColor: () -> Unit) {
        // 观察背景颜色状态
        val backgroundColorInt by viewModel.backgroundColor.collectAsStateWithLifecycle()

        // 使用 derivedStateOf 确保仅在背景色变化时才重新计算对比色，提升 Compose 性能
        val contrastColorInt by remember(backgroundColorInt) {
            derivedStateOf { backgroundColorInt.getContrastColor().toArgb() }
        }

        // 调用刚才修改的全屏点击 Screen
        BrightDisplayScreen(
            backgroundColor = backgroundColorInt,
            contrastColor = contrastColorInt,
            onChangeColorPress = onRandomizeColor
        )
    }

    // --- 3. 亮度与屏幕常亮控制 ---

    override fun onResume() {
        super.onResume()
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 进入时亮度调至最高
        toggleBrightness(true)

        // 强制方向控制
        requestedOrientation = if (config.forcePortraitMode)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    override fun onPause() {
        super.onPause()
        // 释放屏幕常亮
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 退出时恢复系统亮度
        toggleBrightness(false)
    }

    private fun toggleBrightness(increase: Boolean) {
        val layout = window.attributes
        // 1f 表示最高亮度，-1f 表示跟随系统原始设置
        layout.screenBrightness = if (increase) 1f else -1f
        window.attributes = layout
    }

    // --- 4. ViewModel ---

    internal class BrightDisplayViewModel(
        application: Application
    ) : AndroidViewModel(application) {

        // 初始化颜色从持久化配置中读取
        private val _backgroundColor = MutableStateFlow(application.config.brightDisplayColor)
        val backgroundColor = _backgroundColor.asStateFlow()

        fun updateBackgroundColor(color: Int) {
            _backgroundColor.value = color
        }
    }
}
