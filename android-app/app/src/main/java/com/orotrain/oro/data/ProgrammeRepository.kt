package com.orotrain.oro.data

import com.orotrain.oro.model.Programme
import com.orotrain.oro.model.Zone
import com.orotrain.oro.model.ZoneLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProgrammeRepository(private val storageDir: File) {

    private val file get() = File(storageDir, "programmes.json")

    fun loadAll(): List<Programme> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { array.getJSONObject(it).toProgramme() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAll(programmes: List<Programme>) {
        val array = JSONArray()
        programmes.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }

    private fun JSONObject.toProgramme(): Programme {
        val zonesArray = getJSONArray("zones")
        return Programme(
            id = getString("id"),
            name = getString("name"),
            zones = (0 until zonesArray.length()).map { zonesArray.getJSONObject(it).toZone() }
        )
    }

    private fun JSONObject.toZone(): Zone = Zone(
        id = getString("id"),
        strokes = getInt("strokes"),
        sets = getInt("sets"),
        level = ZoneLevel.valueOf(getString("level"))
    )

    private fun Programme.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("zones", JSONArray(zones.map { it.toJson() }))
    }

    private fun Zone.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("strokes", strokes)
        put("sets", sets)
        put("level", level.name)
    }
}
