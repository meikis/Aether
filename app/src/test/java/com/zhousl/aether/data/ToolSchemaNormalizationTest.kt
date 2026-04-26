package com.zhousl.aether.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSchemaNormalizationTest {
    @Test
    fun strictToolParametersMakeOptionalFieldsRequiredAndNullable() {
        val schema = buildStrictToolParameters(
            properties = JSONObject().apply {
                put("path", JSONObject().apply { put("type", "string") })
                put("offset", JSONObject().apply { put("type", "integer") })
                put("limit", JSONObject().apply { put("type", "integer") })
            },
            required = listOf("path"),
        )

        val requiredNames = schema.getJSONArray("required").toStringSet()
        val properties = schema.getJSONObject("properties")

        assertEquals(setOf("path", "offset", "limit"), requiredNames)
        assertEquals("string", properties.getJSONObject("path").getString("type"))
        assertEquals(setOf("integer", "null"), properties.getJSONObject("offset").getJSONArray("type").toStringSet())
        assertEquals(setOf("integer", "null"), properties.getJSONObject("limit").getJSONArray("type").toStringSet())
        assertFalse(schema.getBoolean("additionalProperties"))
    }

    @Test
    fun strictToolParametersPreserveNestedSchemasWhenNullable() {
        val itemSchema = JSONObject().apply {
            put("type", "object")
            put(
                "properties",
                JSONObject().apply {
                    put("oldText", JSONObject().apply { put("type", "string") })
                    put("newText", JSONObject().apply { put("type", "string") })
                },
            )
            put("required", JSONArray().put("oldText").put("newText"))
            put("additionalProperties", false)
        }

        val schema = buildStrictToolParameters(
            properties = JSONObject().apply {
                put(
                    "edits",
                    JSONObject().apply {
                        put("type", "array")
                        put("items", itemSchema)
                    },
                )
            },
            required = emptyList(),
        )

        val editsSchema = schema.getJSONObject("properties").getJSONObject("edits")

        assertEquals(setOf("array", "null"), editsSchema.getJSONArray("type").toStringSet())
        assertEquals(
            setOf("oldText", "newText"),
            editsSchema.getJSONObject("items").getJSONArray("required").toStringSet(),
        )
        assertTrue(
            editsSchema.getJSONObject("items").getJSONObject("properties").has("oldText"),
        )
    }

    private fun JSONArray.toStringSet(): Set<String> =
        buildSet {
            for (index in 0 until length()) {
                add(getString(index))
            }
        }
}
