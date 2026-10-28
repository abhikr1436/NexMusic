package com.nexmusic.app.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val NexIcons.Download: ImageVector
  get() {
    if (_Download != null) {
      return _Download!!
    }
    _Download =
      ImageVector.Builder(
          name = "Download",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(11f, 12.2f)
            lineTo(10.1f, 11.3f)
            quadTo(9.83f, 11.02f, 9.4f, 11.02f)
            reflectiveQuadTo(8.7f, 11.3f)
            reflectiveQuadTo(8.43f, 12f)
            reflectiveQuadTo(8.7f, 12.7f)
            lineToRelative(2.6f, 2.6f)
            quadToRelative(0.3f, 0.3f, 0.7f, 0.3f)
            reflectiveQuadToRelative(0.7f, -0.3f)
            lineToRelative(2.6f, -2.6f)
            quadTo(15.58f, 12.43f, 15.58f, 12f)
            reflectiveQuadTo(15.3f, 11.3f)
            reflectiveQuadTo(14.6f, 11.02f)
            reflectiveQuadTo(13.9f, 11.3f)
            lineTo(13f, 12.2f)
            verticalLineTo(9f)
            quadTo(13f, 8.57f, 12.71f, 8.29f)
            reflectiveQuadTo(12f, 8f)
            reflectiveQuadTo(11.29f, 8.29f)
            reflectiveQuadTo(11f, 9f)
            verticalLineToRelative(3.2f)
            close()
            moveTo(12f, 22f)
            quadTo(9.93f, 22f, 8.1f, 21.21f)
            quadTo(6.28f, 20.43f, 4.93f, 19.08f)
            quadTo(3.58f, 17.73f, 2.79f, 15.9f)
            reflectiveQuadTo(2f, 12f)
            quadTo(2f, 9.92f, 2.79f, 8.1f)
            quadTo(3.58f, 6.27f, 4.93f, 4.93f)
            quadTo(6.28f, 3.57f, 8.1f, 2.79f)
            quadTo(9.93f, 2f, 12f, 2f)
            reflectiveQuadToRelative(3.9f, 0.79f)
            reflectiveQuadToRelative(3.17f, 2.14f)
            quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
            quadTo(22f, 9.92f, 22f, 12f)
            reflectiveQuadToRelative(-0.79f, 3.9f)
            reflectiveQuadToRelative(-2.14f, 3.17f)
            quadToRelative(-1.35f, 1.35f, -3.17f, 2.14f)
            reflectiveQuadTo(12f, 22f)
            close()
          }
        }
        .build()
    return _Download!!
  }

private var _Download: ImageVector? = null
