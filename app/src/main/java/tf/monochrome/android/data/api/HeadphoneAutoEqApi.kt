package tf.monochrome.android.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tf.monochrome.android.domain.model.AutoEqMeasurement
import tf.monochrome.android.domain.model.Headphone
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * HeadphoneAutoEqApi - Fetches headphone profiles from jaakkopasanen/AutoEq GitHub repo
 *
 * Repo structure: results/{source}/{type}/{headphone_name}/
 * e.g. results/oratory1990/over-ear/Sennheiser HD 600/
 *
 * Uses the Git Trees API (recursive) to fetch the full file tree in a single call,
 * then parses headphone entries from the paths.
 */
class HeadphoneAutoEqApi {
    companion object {
        private const val TAG = "HeadphoneAutoEqApi"
        private const val REPO_OWNER = "jaakkopasanen"
        private const val REPO_NAME = "AutoEq"
        private const val TREE_URL =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/git/trees/master?recursive=1"
        private const val RAW_BASE =
            "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/master"
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)

        // Known measurement sources in the results/ folder
        private val KNOWN_TYPES = setOf("over-ear", "in-ear", "earbud")
    }

    private var cachedHeadphones: List<Headphone>? = null
    private var lastFetchTime: Long = 0L

    /**
     * Fetch all headphones from the AutoEq repo using Git Trees API.
     *
     * Parses paths like: results/oratory1990/over-ear/AKG K240 Studio
     * Deduplicates by headphone name (same model from multiple sources → multiple measurements).
     */
    suspend fun fetchHeadphones(): Result<List<Headphone>> = withContext(Dispatchers.IO) {
        try {
            if (isCacheValid()) {
                return@withContext Result.success(cachedHeadphones ?: emptyList())
            }
            val url = URL(TREE_URL)
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "GitHub API returned $responseCode")
                return@withContext Result.failure(Exception("GitHub API returned $responseCode"))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(responseBody).jsonObject
            val tree = root["tree"]?.jsonArray ?: return@withContext Result.failure(
                Exception("No tree in response")
            )

            // Parse: results/{source}/{type}/{headphone_name}
            // We look for "tree" type entries at depth 4 under results/
            data class HeadphoneEntry(
                val name: String,
                val type: String,      // over-ear, in-ear, earbud
                val source: String,    // oratory1990, crinacle, etc.
                val path: String       // full path in repo
            )

            val entries = mutableListOf<HeadphoneEntry>()

            for (item in tree) {
                val obj = item.jsonObject
                val path = obj["path"]?.jsonPrimitive?.content ?: continue
                val nodeType = obj["type"]?.jsonPrimitive?.content ?: continue

                if (nodeType != "tree") continue
                if (!path.startsWith("results/")) continue

                val parts = path.split("/")
                // results / source / type / headphone_name
                if (parts.size != 4) continue

                val source = parts[1]
                val hpType = parts[2]
                val hpName = parts[3]

                if (hpType !in KNOWN_TYPES) continue

                entries.add(HeadphoneEntry(hpName, hpType, source, path))
            }

            // Group by headphone name → deduplicate, merge measurements
            val grouped = entries.groupBy { it.name }
            val headphones = grouped.map { (name, group) ->
                val primaryType = group.first().type
                val measurements = group.map { entry ->
                    AutoEqMeasurement(
                        source = entry.source,
                        target = "AutoEq",
                        path = entry.path,
                        fileName = entry.name
                    )
                }
                Headphone(
                    id = name.replace(" ", "_").lowercase(),
                    name = name,
                    type = primaryType,
                    data = emptyList(),
                    measurements = measurements
                )
            }.sortedBy { it.name.lowercase() }

            cachedHeadphones = headphones
            lastFetchTime = System.currentTimeMillis()

            Result.success(headphones)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch measurement CSV for a specific headphone.
     * Tries multiple known file names in the first available measurement path.
     */
    suspend fun fetchHeadphoneMeasurement(headphoneId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                // Find the headphone in cache to get its path
                val headphone = cachedHeadphones?.find { it.id == headphoneId }
                val measurement = headphone?.measurements?.firstOrNull()
                val basePath = measurement?.path

                if (basePath != null) {
                    // Try known measurement file names
                    val fileNames = listOf(
                        "$headphone.csv",
                        "raw.csv",
                        "${headphone?.name}.csv"
                    )
                    for (fileName in fileNames) {
                        try {
                            val rawUrl = "$RAW_BASE/$basePath/$fileName"
                            val csvData = fetchUrl(rawUrl)
                            if (csvData != null) {
                                return@withContext Result.success(csvData)
                            }
                        } catch (_: Exception) { }
                    }
                }

                // Fallback: try common patterns
                val encodedId = headphoneId.replace("_", " ")
                val fallbackUrls = listOf(
                    "$RAW_BASE/results/oratory1990/over-ear/$encodedId/$encodedId.csv",
                    "$RAW_BASE/results/crinacle/over-ear/$encodedId/$encodedId.csv",
                    "$RAW_BASE/results/oratory1990/in-ear/$encodedId/$encodedId.csv",
                    "$RAW_BASE/results/crinacle/in-ear/$encodedId/$encodedId.csv",
                )
                for (url in fallbackUrls) {
                    try {
                        val csvData = fetchUrl(url)
                        if (csvData != null) {
                            return@withContext Result.success(csvData)
                        }
                    } catch (_: Exception) { }
                }

                Result.failure(Exception("No measurement data found for $headphoneId"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun searchHeadphones(query: String): Result<List<Headphone>> {
        val all = fetchHeadphones().getOrNull() ?: return Result.success(emptyList())
        return Result.success(all.filter { it.name.contains(query, ignoreCase = true) })
    }

    suspend fun getHeadphonesByType(type: String): Result<List<Headphone>> {
        val all = fetchHeadphones().getOrNull() ?: return Result.success(emptyList())
        return Result.success(all.filter { it.type == type })
    }

    fun clearCache() {
        cachedHeadphones = null
        lastFetchTime = 0L
    }

    private fun isCacheValid(): Boolean =
        cachedHeadphones != null && (System.currentTimeMillis() - lastFetchTime) < CACHE_TTL_MS

    private fun fetchUrl(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
        }
        return if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else null
    }
}
