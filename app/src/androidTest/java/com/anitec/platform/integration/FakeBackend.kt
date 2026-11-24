package com.anitec.platform.integration

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for the AniTec API that follows the rules the apps depend on, so the repositories can be exercised with the
 * real Retrofit and the real Room, without the network or the server:
 *
 * - a POST with an `Idempotency-Key` that was already used returns the stored answer and creates nothing,
 * - a PUT with an `If-Match` that does not name the current version answers 412 with the version and the current copy,
 *   and changes nothing; every accepted change raises the version by one,
 * - the consent endpoints of the rancher answer like the real ones (409 when a request was already answered).
 */
class FakeBackend : Dispatcher() {
    val received = CopyOnWriteArrayList<RecordedRequest>()

    private val records = linkedMapOf<Int, JsonObject>()
    private val stored = mutableMapOf<String, Pair<Int, String>>()
    private var nextId = 100

    /** The links of the rancher, by id, as `status` text. */
    val links = linkedMapOf(1 to "Pending", 2 to "Accepted")

    /** When set, every request answers with this status, as a server that is failing. */
    @Volatile var failWith: Int? = null

    /** Number of financial records the server holds. */
    val recordCount: Int get() = records.size

    fun record(id: Int): JsonObject? = records[id]

    /** Changes a record behind the app's back, as another device would. */
    fun changeElsewhere(id: Int, field: String, value: String) {
        val current = records.getValue(id)
        records[id] = JsonObject(current + (field to JsonPrimitive(value)) + ("version" to JsonPrimitive(current.getValue("version").jsonPrimitive.int + 1)))
    }

    private fun json(code: Int, body: JsonObject) = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body.toString()).build()

    private fun jsonArray(items: Collection<JsonObject>) =
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(items.joinToString(prefix = "[", postfix = "]") { it.toString() }).build()

    override fun dispatch(request: RecordedRequest): MockResponse {
        received += request
        failWith?.let { return MockResponse.Builder().code(it).build() }
        val path = request.url.encodedPath.removePrefix("/api/v1/")
        val body = request.body?.utf8().orEmpty()
        return when {
            request.method == "GET" && path == "financial-records" -> jsonArray(records.values)
            request.method == "POST" && path == "financial-records" -> create(request.headers["Idempotency-Key"], body)
            request.method == "PUT" && path.startsWith("financial-records/") -> update(path.substringAfter('/').toInt(), request.headers["If-Match"], body)
            request.method == "GET" && path.endsWith("/veterinarian-links") -> jsonArray(links.map { (id, status) -> link(id, status) })
            request.method == "POST" && path.contains("/veterinarian-links/") -> answer(path)
            else -> MockResponse.Builder().code(404).build()
        }
    }

    private fun create(key: String?, body: String): MockResponse {
        if (key != null) stored[key]?.let { (status, answer) -> return MockResponse.Builder().code(status).addHeader("Content-Type", "application/json").addHeader("Idempotent-Replayed", "true").body(answer).build() }
        val sent = Json.parseToJsonElement(body).jsonObject
        val id = nextId++
        val created = JsonObject(sent + ("id" to JsonPrimitive(id)) + ("version" to JsonPrimitive(1)))
        records[id] = created
        if (key != null) stored[key] = 201 to created.toString()
        return json(201, created)
    }

    private fun update(id: Int, ifMatch: String?, body: String): MockResponse {
        val current = records[id] ?: return MockResponse.Builder().code(404).build()
        val version = current.getValue("version").jsonPrimitive.int
        if (ifMatch != null && ifMatch.trim('"') != version.toString()) {
            return json(412, buildJsonObject {
                put("message", JsonPrimitive("The record was changed after the version you based your change on."))
                put("currentVersion", JsonPrimitive(version))
                put("current", current)
            })
        }
        val sent = Json.parseToJsonElement(body).jsonObject
        val updated = JsonObject(sent + ("id" to JsonPrimitive(id)) + ("version" to JsonPrimitive(version + 1)))
        records[id] = updated
        return json(200, updated)
    }

    private fun link(id: Int, status: String) = buildJsonObject {
        put("id", JsonPrimitive(id)); put("veterinarianId", JsonPrimitive(100 + id)); put("veterinarianName", JsonPrimitive("Vet $id"))
        put("veterinarianUsername", JsonPrimitive("vet$id")); put("status", JsonPrimitive(status)); put("requestedAt", JsonPrimitive("2026-11-23T10:00:00"))
    }

    private fun answer(path: String): MockResponse {
        val id = path.substringAfter("veterinarian-links/").substringBefore('/').toInt()
        val action = path.substringAfterLast('/')
        val status = links[id] ?: return MockResponse.Builder().code(404).build()
        return when (action) {
            "accept", "reject" -> if (status != "Pending") json(409, message("The request was already answered.")) else {
                links[id] = if (action == "accept") "Accepted" else "Rejected"; json(200, link(id, links.getValue(id)))
            }
            "revoke" -> if (status != "Accepted") json(404, message("There is no active access for this veterinarian.")) else {
                links[id] = "Revoked"; json(200, link(id, "Revoked"))
            }
            else -> MockResponse.Builder().code(404).build()
        }
    }

    private fun message(text: String) = buildJsonObject { put("message", JsonPrimitive(text)) }
}
