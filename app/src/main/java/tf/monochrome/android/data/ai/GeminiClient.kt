package tf.monochrome.android.data.ai

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import tf.monochrome.android.domain.model.AiFilter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) {
    companion object {
        private const val GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    }

    suspend fun getAudioRecommendations(
        audioBytes: ByteArray,
        mimeType: String,
        trackContext: String,
        filters: Set<AiFilter>,
        apiKey: String
    ): List<String> {
        val prompt = buildPrompt(trackContext, filters)
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("inline_data", buildJsonObject {
                                put("mime_type", mimeType)
                                put("data", audioBase64)
                            })
                        })
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val response = httpClient.post("$GEMINI_API_URL?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val responseText = response.bodyAsText()
        return parseGeminiResponse(responseText)
    }

    private fun buildPrompt(trackContext: String, filters: Set<AiFilter>): String {
        val filterInstructions = if (filters.contains(AiFilter.ALL) || filters.isEmpty()) {
            AiFilter.ALL.promptInstruction
        } else {
            filters.joinToString("\n") { "- ${it.displayName}: ${it.promptInstruction}" }
        }

        return """
You are a music recommendation engine. Listen to this audio track and analyze it.

Track context: $trackContext

Analysis instructions:
$filterInstructions

Based on your analysis, suggest 8-12 search queries that would help find similar tracks on a music streaming service. Each query should be a concise search string (artist name, song title, genre keywords, or a combination).

IMPORTANT: Respond ONLY with a JSON array of strings. No explanation, no markdown, no code blocks. Just the raw JSON array.
Example: ["artist name song title", "genre keyword mood", "another artist similar track"]
        """.trimIndent()
    }

    private fun parseGeminiResponse(responseJson: String): List<String> {
        return try {
            val root = json.parseToJsonElement(responseJson).jsonObject
            val candidates = root["candidates"]?.jsonArray ?: return emptyList()
            val content = candidates[0].jsonObject["content"]?.jsonObject ?: return emptyList()
            val parts = content["parts"]?.jsonArray ?: return emptyList()
            val text = parts[0].jsonObject["text"]?.jsonPrimitive?.content ?: return emptyList()

            parseSearchQueries(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSearchQueries(text: String): List<String> {
        val cleaned = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val array = json.parseToJsonElement(cleaned)
            if (array is JsonArray) {
                array.map { it.jsonPrimitive.content }.filter { it.isNotBlank() }
            } else {
                fallbackParse(cleaned)
            }
        } catch (_: Exception) {
            fallbackParse(cleaned)
        }
    }

    private fun fallbackParse(text: String): List<String> {
        return text.lines()
            .map { it.trim().removeSurrounding("\"").removePrefix("- ").trim() }
            .filter { it.isNotBlank() && it.length > 2 }
    }
}
