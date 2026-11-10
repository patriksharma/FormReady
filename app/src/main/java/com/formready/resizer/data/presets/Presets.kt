package com.formready.resizer.data.presets

import com.formready.resizer.core.image.SizeUnit
import com.formready.resizer.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Preset(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val unit: SizeUnit = SizeUnit.PX,
    val maxKb: Int?,
    val premium: Boolean = false,
    val custom: Boolean = false
) {
    fun serialize(): String = listOf(id, name, width, height, unit.name, maxKb ?: -1).joinToString("|")

    companion object {
        fun deserialize(raw: String): Preset? {
            val parts = raw.split("|")
            if (parts.size < 6) return null
            return runCatching {
                Preset(
                    id = parts[0],
                    name = parts[1],
                    width = parts[2].toInt(),
                    height = parts[3].toInt(),
                    unit = SizeUnit.valueOf(parts[4]),
                    maxKb = parts[5].toInt().takeIf { it > 0 },
                    custom = true
                )
            }.getOrNull()
        }
    }
}

object DefaultPresets {
    val photo = listOf(
        Preset("photo_200x230", "Application photo 200x230", 200, 230, SizeUnit.PX, 50),
        Preset("photo_300x300", "Square 300x300", 300, 300, SizeUnit.PX, 100),
        Preset("photo_35x45", "Passport 35x45 mm", 35, 45, SizeUnit.MM, 200),
        Preset("photo_2x2in", "US passport 2x2 in", 2, 2, SizeUnit.INCH, 240),
        Preset("photo_600x600", "Visa 600x600", 600, 600, SizeUnit.PX, 240),
        Preset("photo_413x531", "ID photo 413x531", 413, 531, SizeUnit.PX, 100),
        Preset("photo_uk_45x35", "UK 45x35 mm", 45, 35, SizeUnit.MM, 200, premium = true),
        Preset("photo_schengen", "Schengen 35x45 mm 300dpi", 35, 45, SizeUnit.MM, 500, premium = true)
    )

    val signature = listOf(
        Preset("sign_140x60", "Signature 140x60", 140, 60, SizeUnit.PX, 20),
        Preset("sign_300x80", "Signature 300x80", 300, 80, SizeUnit.PX, 50),
        Preset("sign_35x15", "Signature 35x15 mm", 35, 15, SizeUnit.MM, 30),
        Preset("sign_200x100", "Signature 200x100", 200, 100, SizeUnit.PX, 40),
        Preset("sign_wide", "Wide signature 500x150", 500, 150, SizeUnit.PX, 100, premium = true)
    )

    val kbLimits = listOf(10, 20, 50, 100, 200, 500, 1024)
}

@Singleton
class PresetRepository @Inject constructor(
    private val settings: SettingsRepository
) {
    fun photoPresets(): Flow<List<Preset>> =
        settings.customPresets.map { DefaultPresets.photo + it.mapNotNull(Preset::deserialize) }

    fun signaturePresets(): Flow<List<Preset>> =
        settings.customPresets.map { DefaultPresets.signature + it.mapNotNull(Preset::deserialize) }

    suspend fun save(preset: Preset) = settings.saveCustomPreset(preset.serialize())

    suspend fun delete(preset: Preset) = settings.deleteCustomPreset(preset.serialize())
}
