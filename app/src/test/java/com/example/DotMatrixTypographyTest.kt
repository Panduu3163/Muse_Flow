package com.example

import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.fontFamilyFor
import com.example.ui.theme.museFlowTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotMatrixTypographyTest {
    @Test fun supportingTextAndTitlesBothUseDotoWithoutSyntheticBold() {
        val typography = museFlowTypography(AppFontStyle.DotMatrix)
        val doto = fontFamilyFor(AppFontStyle.DotMatrix)
        assertEquals(doto, typography.titleMedium.fontFamily)
        assertEquals(doto, typography.bodyMedium.fontFamily)
        assertEquals(doto, typography.labelSmall.fontFamily)
        assertEquals(FontWeight.Normal, typography.titleMedium.fontWeight)
        assertEquals(FontWeight.Normal, typography.bodyMedium.fontWeight)
        assertTrue(typography.titleMedium.fontSize > typography.bodyMedium.fontSize)
    }
}
