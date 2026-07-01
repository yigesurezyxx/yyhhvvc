package com.parsehub.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/** ParseHub 动画时长 Token(硬规则:任何动画不超过 400ms,仅 Success 允许 600ms) */
object Motion {
    const val UltraFast = 80
    const val Fast = 150
    const val Normal = 250
    const val Slow = 400
    const val ExtraSlow = 600  // 仅 Success 动画
}

/** Easing 预设 */
object Easings {
    val Standard: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EaseOut: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val EaseInOut: Easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f)
}

/** 常用 Spring 实例 */
object Springs {
    val Button = spring<Float>(
        dampingRatio = 0.5f,
        stiffness = Spring.StiffnessMedium
    )
    val Card = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = 400f
    )
    val Logo = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessLow
    )
}

/** 动效规则映射(场景 → 动效 + 时长) */
object MotionSpec {
    /** 按钮点击:Scale 0.96 */
    const val ButtonScaleTarget = 0.96f
    /** 解析成功:Scale 1.0 → 1.05 → 1.0 */
    const val SuccessScalePeak = 1.05f
    /** 错误提示:Shake 幅度 */
    const val ShakeAmplitude = 8f
    const val ShakeRepeat = 3
    /** 卡片入场 SlideUp 距离 */
    const val CardSlideUp = 16f
    /** Stagger 间隔 */
    const val StaggerMs = 50
}
