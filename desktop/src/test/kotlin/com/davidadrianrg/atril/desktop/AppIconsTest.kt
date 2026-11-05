package com.davidadrianrg.atril.desktop

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class AppIconsTest {
    /** Regresión: el "+" del mando se pintaba relleno y el icono salía como un rectángulo sólido. */
    @Test
    fun iconsWithHolesUseSingleEvenOddPath() {
        for (icon in listOf(AppIcons.Gamepad, AppIcons.Settings, AppIcons.Casino)) {
            val paths = icon.root.filterIsInstance<VectorPath>()
            assertEquals("${icon.name} debe ser un único path", 1, paths.size)
            assertEquals(icon.name, PathFillType.EvenOdd, paths[0].pathFillType)
            assertTrue(icon.name, paths[0].pathData.size > 10)
        }
    }
}
