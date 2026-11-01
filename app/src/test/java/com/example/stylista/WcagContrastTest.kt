package com.example.stylista

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

class WcagContrastTest {

    private fun parseHexColor(hex: String): Triple<Int, Int, Int> {
        val clean = hex.removePrefix("#").trim()
        val rgb = when (clean.length) {
            3 -> clean.map { "$it$it" }.joinToString("")
            6 -> clean
            8 -> clean.substring(2)
            else -> throw IllegalArgumentException("Invalid hex: $hex")
        }
        val r = rgb.substring(0, 2).toInt(16)
        val g = rgb.substring(2, 4).toInt(16)
        val b = rgb.substring(4, 6).toInt(16)
        return Triple(r, g, b)
    }

    private fun channelToLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    fun calculateLuminance(hex: String): Double {
        val (r, g, b) = parseHexColor(hex)
        val lr = channelToLinear(r)
        val lg = channelToLinear(g)
        val lb = channelToLinear(b)
        return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb
    }

    fun calculateContrastRatio(fgHex: String, bgHex: String): Double {
        val l1 = calculateLuminance(fgHex)
        val l2 = calculateLuminance(bgHex)
        val brighter = max(l1, l2)
        val darker = min(l1, l2)
        return (brighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testPrimaryButterYellowButtonContrast_LightAndDark() {
        val lightRatio = calculateContrastRatio("#212529", "#FFF3B0")
        val darkRatio = calculateContrastRatio("#1C1B1F", "#FFF3B0")

        println("Butter Yellow Button Contrast (Light): $lightRatio:1")
        println("Butter Yellow Button Contrast (Dark): $darkRatio:1")

        assertTrue("Light butter yellow button text must meet WCAG AA (>= 4.5:1)", lightRatio >= 4.5)
        assertTrue("Dark butter yellow button text must meet WCAG AA (>= 4.5:1)", darkRatio >= 4.5)
    }

    @Test
    fun testSecondaryBabyBlueButtonContrast_LightAndDark() {
        val lightRatio = calculateContrastRatio("#212529", "#A2C2E8")
        val darkRatio = calculateContrastRatio("#1C1B1F", "#A2C2E8")

        println("Baby Blue Button Contrast (Light): $lightRatio:1")
        println("Baby Blue Button Contrast (Dark): $darkRatio:1")

        assertTrue("Light baby blue button text must meet WCAG AA (>= 4.5:1)", lightRatio >= 4.5)
        assertTrue("Dark baby blue button text must meet WCAG AA (>= 4.5:1)", darkRatio >= 4.5)
    }

    @Test
    fun testTextContrastAgainstBackgrounds() {
        val lightBodyText = calculateContrastRatio("#212529", "#FFF9EA")
        val darkBodyText = calculateContrastRatio("#F5F5F5", "#121212")
        val darkSurfaceText = calculateContrastRatio("#F5F5F5", "#242424")
        val darkSecondaryText = calculateContrastRatio("#B0B0B0", "#242424")

        println("Light body text contrast: $lightBodyText:1")
        println("Dark body text contrast: $darkBodyText:1")
        println("Dark surface text contrast: $darkSurfaceText:1")
        println("Dark secondary text contrast: $darkSecondaryText:1")

        assertTrue("Light body text must meet WCAG AA", lightBodyText >= 4.5)
        assertTrue("Dark body text must meet WCAG AA", darkBodyText >= 4.5)
        assertTrue("Dark surface text must meet WCAG AA", darkSurfaceText >= 4.5)
        assertTrue("Dark secondary text must meet WCAG AA", darkSecondaryText >= 4.5)
    }

    @Test
    fun testDarkModeNavAndCardBorders() {
        val navIconRatio = calculateContrastRatio("#B0B0B0", "#1E1E1E")
        val cardBorderRatio = calculateContrastRatio("#666666", "#121212")

        println("Dark Nav Icon Contrast: $navIconRatio:1")
        println("Dark Card Border Contrast: $cardBorderRatio:1")

        assertTrue("Dark unselected nav icons must meet WCAG AA (>= 4.5:1)", navIconRatio >= 4.5)
        assertTrue("Dark essential boundaries/borders must meet WCAG (>= 3.0:1)", cardBorderRatio >= 3.0)
    }
}
