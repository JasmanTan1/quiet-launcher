// SPDX-License-Identifier: Apache-2.0
// Modified for the standalone launcher.
package org.quietlauncher.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Programmatic hills and optional sun; no external artwork or media. */
fun DrawScope.drawBackdrop(glow: Color, withSun: Boolean, sunR: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0E1A33), Color(0xFF173452), Color(0xFF0A1020))))
    val c = Offset(size.width * .5f, size.height * .4f)
    drawCircle(Brush.radialGradient(listOf(glow.copy(0.35f), Color.Transparent), c, size.width * .3f), size.width * .3f, c)
    val sc = Offset(size.width * .497f, size.height * .39f)
    if (withSun) {
        drawCircle(Brush.radialGradient(listOf(Color(0x55D8BC8E), Color.Transparent), sc, sunR * 3.2f), sunR * 3.2f, sc)
        drawCircle(Color(0xFFD8BC8E), sunR, sc)
    }
    // Layered hills, far to near, the far one peaking just under the sun.
    fun hill(y0: Float, peakX: Float, peakY: Float, y1: Float, color: Color) = Path().apply {
        moveTo(0f, size.height * y0)
        cubicTo(size.width * (peakX * .5f), size.height * (y0 - .02f), size.width * (peakX - .12f), size.height * peakY, size.width * peakX, size.height * peakY)
        cubicTo(size.width * (peakX + .15f), size.height * peakY, size.width * .8f, size.height * (y1 + .02f), size.width, size.height * y1)
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }.let { drawPath(it, color) }
    hill(.78f, .48f, .50f, .72f, Color(0xFF1E3B58).copy(0.55f))
    hill(.84f, .30f, .66f, .66f, Color(0xFF13294A).copy(0.8f))
    hill(.92f, .70f, .74f, .78f, Color(0xFF0B1730).copy(0.9f))
}
