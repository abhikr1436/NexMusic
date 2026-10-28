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
val NexIcons.Lyrics: ImageVector
  get() {
    if (_Lyrics != null) {
      return _Lyrics!!
    }
    _Lyrics =
      ImageVector.Builder(
          name = "Lyrics",
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
            moveTo(7f, 14f)
            horizontalLineTo(9f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(10f, 13.43f, 10f, 13f)
            reflectiveQuadTo(9.71f, 12.29f)
            reflectiveQuadTo(9f, 12f)
            horizontalLineTo(7f)
            quadTo(6.58f, 12f, 6.29f, 12.29f)
            reflectiveQuadTo(6f, 13f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(7f, 14f)
            close()
            moveTo(19f, 12f)
            quadToRelative(-1.25f, 0f, -2.13f, -0.88f)
            reflectiveQuadTo(16f, 9f)
            reflectiveQuadTo(16.88f, 6.88f)
            reflectiveQuadTo(19f, 6f)
            quadToRelative(0.28f, 0f, 0.53f, 0.05f)
            reflectiveQuadTo(20f, 6.18f)
            verticalLineTo(2f)
            quadTo(20f, 1.57f, 20.29f, 1.29f)
            reflectiveQuadTo(21f, 1f)
            horizontalLineToRelative(2f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(24f, 2f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(23f, 3f)
            horizontalLineTo(22f)
            verticalLineTo(9f)
            quadToRelative(0f, 1.25f, -0.88f, 2.13f)
            reflectiveQuadTo(19f, 12f)
            close()
            moveTo(7f, 11f)
            horizontalLineToRelative(5f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(13f, 10.43f, 13f, 10f)
            quadTo(13f, 9.57f, 12.71f, 9.29f)
            reflectiveQuadTo(12f, 9f)
            horizontalLineTo(7f)
            quadTo(6.58f, 9f, 6.29f, 9.29f)
            reflectiveQuadTo(6f, 10f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(7f, 11f)
            close()
            moveTo(7f, 8f)
            horizontalLineToRelative(5f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(13f, 7.43f, 13f, 7f)
            reflectiveQuadTo(12.71f, 6.29f)
            reflectiveQuadTo(12f, 6f)
            horizontalLineTo(7f)
            quadTo(6.58f, 6f, 6.29f, 6.29f)
            reflectiveQuadTo(6f, 7f)
            reflectiveQuadTo(6.29f, 7.71f)
            reflectiveQuadTo(7f, 8f)
            close()
            moveToRelative(8f, 10f)
            horizontalLineTo(6f)
            lineTo(3.7f, 20.3f)
            quadTo(3.55f, 20.45f, 3.38f, 20.53f)
            reflectiveQuadTo(3f, 20.6f)
            quadToRelative(-0.4f, 0f, -0.7f, -0.29f)
            reflectiveQuadTo(2f, 19.58f)
            verticalLineTo(4f)
            quadTo(2f, 3.17f, 2.59f, 2.59f)
            reflectiveQuadTo(4f, 2f)
            horizontalLineTo(15f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(17f, 4f)
            verticalLineTo(4.42f)
            quadTo(16.4f, 4.7f, 15.9f, 5.1f)
            reflectiveQuadTo(15f, 6f)
            verticalLineTo(4f)
            horizontalLineTo(4f)
            verticalLineTo(17.18f)
            lineTo(5.18f, 16f)
            horizontalLineTo(15f)
            verticalLineTo(12f)
            quadToRelative(0.4f, 0.5f, 0.9f, 0.9f)
            reflectiveQuadTo(17f, 13.58f)
            verticalLineTo(16f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(15f, 18f)
            close()
            moveTo(4f, 16f)
            verticalLineTo(4f)
            verticalLineTo(6f)
            quadTo(4f, 6.63f, 4f, 7.39f)
            reflectiveQuadTo(4f, 9f)
            reflectiveQuadToRelative(0f, 1.61f)
            quadTo(4f, 11.38f, 4f, 12f)
            verticalLineToRelative(4f)
            close()
          }
        }
        .build()
    return _Lyrics!!
  }

private var _Lyrics: ImageVector? = null
