package com.garage.tracker.data

import android.graphics.Color

/** A friendly colour name for a #RRGGBB value, for use in the AI image prompt. */
fun colorName(hex: String): String {
    val argb = runCatching { Color.parseColor(hex) }.getOrDefault(Color.rgb(59, 130, 246))
    val hsv = FloatArray(3)
    Color.colorToHSV(argb, hsv)
    val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
    return when {
        v < 0.18f -> "black"
        s < 0.12f -> when {
            v > 0.88f -> "white"
            v > 0.6f -> "silver"
            v > 0.35f -> "gray"
            else -> "charcoal"
        }
        else -> {
            val base = when {
                h < 15 || h >= 345 -> "red"
                h < 45 -> "orange"
                h < 70 -> "yellow"
                h < 165 -> "green"
                h < 200 -> "teal"
                h < 255 -> "blue"
                h < 290 -> "purple"
                else -> "pink"
            }
            when {
                v < 0.5f -> "dark $base"
                s < 0.4f && v > 0.8f -> "light $base"
                else -> base
            }
        }
    }
}

/** The background colour asked of the AI: green screen, unless the car itself is green or teal. */
fun backgroundFor(hex: String): Pair<String, String> {
    val name = colorName(hex)
    return if (name.contains("green") || name.contains("teal")) "magenta" to "#FF00FF" else "bright green" to "#00FF00"
}

/** A ready-to-paste prompt for ChatGPT, Gemini or any image generator. */
fun carImagePrompt(v: Vehicle): String {
    val color = colorName(v.colorHex)
    val (bgName, bgHex) = backgroundFor(v.colorHex)
    return """
        Create a stylized, cartoonized 3D render of a ${v.year} ${v.make} ${v.model}, painted $color (approximately ${v.colorHex.uppercase()}).

        Show it as a polished collectible toy model in a clean isometric three-quarter view: camera slightly above the car, looking at the front-left corner, with an orthographic look and no perspective distortion. Use smooth simplified shapes, soft shading, subtle highlights, glossy paint and thin clean outlines. Keep the real car's recognisable design for this exact model and generation: body shape, headlights, grille, wheels and proportions.

        The whole car must be fully visible and centred, with a small margin around it.

        Background: one perfectly flat, solid $bgName ($bgHex) background like a green screen, with no floor, no shadow, no reflection, no gradient, no text, no logo and no other objects, so it is easy to remove. (If you can output a transparent PNG, use a transparent background instead.)

        Square image, high resolution.
    """.trimIndent()
}
