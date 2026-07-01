package com.parsehub.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** ParseHub 固定圆角值 Token(组件级精确控制) */
object Radius {
    val input = 20.dp
    val card = 24.dp
    val chip = 16.dp
    val dialog = 28.dp
    val fab = 20.dp
}

/** 便捷工厂 */
object RadiusShapes {
    val input = RoundedCornerShape(Radius.input)
    val card = RoundedCornerShape(Radius.card)
    val chip = RoundedCornerShape(Radius.chip)
    val dialog = RoundedCornerShape(Radius.dialog)
    val fab = RoundedCornerShape(Radius.fab)
}
