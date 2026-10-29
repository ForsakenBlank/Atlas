package com.forsakenblank.atlas.sync

import com.forsakenblank.atlas.ATLAS_VERSION
import com.forsakenblank.atlas.AtlasApp
import com.forsakenblank.atlas.data.SyncPeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class HostInfo(val code: String, val addresses: List<String>, val port: Int)

// a line of text for what is happening, and how far along when that is known
data class SyncStatus(val text: String, val fraction: Float? = null)

data class Peer(val deviceId: String, val name: String, val kind: String)

sealed interface ClientStep {
    data object Idle : ClientStep
    data class Working(val status: SyncStatus) : ClientStep
    // names holds what trackers, accounts and subjects are called, so changes to their rows can be labelled
    class Review(val peer: Peer, val plan: SyncPlan, val firstTime: Boolean, val names: Map<String, String>) : ClientStep
    data class Done(val text: String) : ClientStep
    data class Failed(val text: String) : ClientStep
}

// both halves of a sync: hosting so another device can connect, and connecting to one that hosts
class SyncManager(private val app: AtlasApp) {
    private val store = SyncStore(app.database, app.repository, app.settingsStore)
    private val dao = app.database.sync()
    private val json = SyncWire.json
    private val records = ListSerializer(SyncRecord.serializer())
    private val refs = ListSerializer(RecordRef.serializer())
    private val relinks = ListSerializer(Relink.serializer())
    private val baselines = MapSerializer(String.serializer(), String.serializer())

    val peers: Flow<List<SyncPeer>> = dao.peers()

    private val _host = MutableStateFlow<HostInfo?>(null)
    val host: StateFlow<HostInfo?> = _host.asStateFlow()

    private val _hostStatus = MutableStateFlow<SyncStatus?>(null)
    val hostStatus: StateFlow<SyncStatus?> = _hostStatus.asStateFlow()

    private val _client = MutableStateFlow<ClientStep>(ClientStep.Idle)
    val client: StateFlow<ClientStep> = _client.asStateFlow()

    private var server: SyncServer? = null
    private var hostPhase = ""
    private val applied = HashSet<String>()
    private var session: Session? = null

    private class Session(val address: String, val key: SyncKey, val peer: Peer, val plan: SyncPlan)

    // hosting

    suspend fun startHosting(port: Int = SyncWire.PORT) {
        if (server != null) return
        val code = SyncWire.newCode()
        val key = withContext(Dispatchers.Default) { SyncKey(code) }
        val started = SyncServer(
            key = key,
            scope = app.appScope,
            onLockedOut = {
                stopHosting()
                _hostStatus.value = SyncStatus("Too many wrong codes, hosting stopped. Turn it on again for a new code.")
            },
            handle = { request, _ -> answerOrExplain(request) },
            onProgress = { fraction -> if (fraction != null && hostPhase.isNotEmpty()) _hostStatus.value = SyncStatus(hostPhase, fraction) },
            port = port,
        )
        try {
            withContext(Dispatchers.IO) { started.start() }
        } catch (e: java.io.IOException) {
            _hostStatus.value = SyncStatus("Couldn't start hosting, port ${SyncWire.PORT} is busy. Is Luminescent or another copy of Atlas hosting on this computer?")
            return
        }
        server = started
        _host.value = HostInfo(code, SyncWire.localAddresses(), started.boundPort)
        val hint = withContext(Dispatchers.IO) { SyncWire.firewallHint() }
        _hostStatus.value = SyncStatus(if (hint != null) "Waiting for a device… $hint" else "Waiting for a device…")
    }

    fun stopHosting() {
        server?.stop()
        server = null
        _host.value = null
        hostPhase = ""
        _hostStatus.value = null
    }

    // a request that fails shows why on this device too, instead of staying on the step it was on,
    // and the other device gets a sentence rather than a bare database error
    private suspend fun answerOrExplain(request: JsonObject): JsonObject = try {
        answer(request)
    } catch (e: SyncException) {
        hostPhase = ""
        _hostStatus.value = SyncStatus(e.message.orEmpty())
        throw e
    } catch (e: Exception) {
        val me = app.platform.kind.label
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        val why = if (request.str("action") == "apply") {
            "The $me couldn't save what the ${request.str("name") ?: "other device"} sent ($detail). Nothing was changed on the $me."
        } else {
            "The $me couldn't get its things ready to send ($detail)."
        }
        hostPhase = ""
        _hostStatus.value = SyncStatus(why)
        throw SyncException(why)
    }

    private suspend fun answer(request: JsonObject): JsonObject {
        val name = request.str("name") ?: "other device"
        return when (request.str("action")) {
            "hello" -> {
                hostPhase = ""
                _hostStatus.value = SyncStatus("$name connected")
                hello()
            }
            "records" -> {
                hostPhase = "Sending to $name"
                _hostStatus.value = SyncStatus("Comparing with $name…")
                val peerId = request.str("peerId").orEmpty()
                val mine = store.records()
                val base = baselineFor(peerId)
                buildJsonObject {
                    put("records", json.encodeToJsonElement(records, mine))
                    put("baseDigest", baselineDigest(base))
                }
            }
            "apply" -> {
                val syncId = request.str("syncId").orEmpty()
                if (syncId.isNotEmpty() && syncId in applied) return buildJsonObject { put("ok", true) }
                hostPhase = ""
                _hostStatus.value = SyncStatus("Saving what $name sent…", 1f)
                val upserts = json.decodeFromJsonElement(records, request.getValue("upserts"))
                val deletes = json.decodeFromJsonElement(refs, request.getValue("deletes"))
                val links = json.decodeFromJsonElement(relinks, request.getValue("relinks"))
                store.apply(upserts, deletes, links)
                val baseline = json.decodeFromJsonElement(baselines, request.getValue("baseline"))
                savePeer(Peer(request.str("peerId").orEmpty(), name, request.str("kind") ?: "Device"), null, baseline)
                if (syncId.isNotEmpty()) applied += syncId
                val count = upserts.size + deletes.size
                _hostStatus.value = SyncStatus("Synced with $name" + if (count > 0) " · ${changes(count)} received" else " · nothing new")
                buildJsonObject { put("ok", true) }
            }
            else -> throw SyncException("This version of Atlas doesn't know that request")
        }
    }

    // connecting

    suspend fun connect(address: String, code: String) {
        _client.value = ClientStep.Working(SyncStatus("Connecting…"))
        try {
            val key = withContext(Dispatchers.Default) { SyncKey(code.trim()) }
            val me = app.settingsStore.deviceId()
            val hello = SyncWire.call(address, key, hello())
            val peer = Peer(hello.str("deviceId").orEmpty(), hello.str("name") ?: "other device", hello.str("kind") ?: "Device")
            if (peer.deviceId == me) throw SyncException("That's this device. Connect to the other one's address.")

            _client.value = ClientStep.Working(SyncStatus("Getting everything from the ${peer.name}…", 0f))
            val answer = SyncWire.call(address, key, request("records")) { fraction ->
                _client.value = ClientStep.Working(SyncStatus("Getting everything from the ${peer.name}…", fraction))
            }
            _client.value = ClientStep.Working(SyncStatus("Comparing…"))
            val theirs = json.decodeFromJsonElement(records, answer.getValue("records"))
            // a device that only keeps some things, like Luminescent's notes and timers, says so up front,
            // everything else stays out of the comparison so it is never mistaken for deleted over there
            val mine = Scope.from(hello["accepts"]).filter(store.records())
            val saved = baselineFor(peer.deviceId)
            // both sides remember the same state after a sync, when they disagree the safe thing is to compare from scratch
            val base = if (baselineDigest(saved) == answer.str("baseDigest")) saved else emptyMap()
            val plan = withContext(Dispatchers.Default) { SyncPlanner.plan(mine, theirs, base) }
            app.updateSettings { it.copy(lastSyncAddress = address.trim()) }
            session = Session(address, key, peer, plan)
            val names = (theirs + mine)
                .filter { it.kind == Kind.ITEM || it.kind == Kind.ACCOUNT || it.kind == Kind.SUBJECT }
                .associate { it.uid to (it.text("name") ?: "") }
            _client.value = ClientStep.Review(peer, plan, firstTime = base.isEmpty(), names = names)
        } catch (e: Exception) {
            _client.value = ClientStep.Failed(e.message ?: "Sync failed")
        }
    }

    suspend fun run(choices: Map<String, Choice>) {
        val current = session ?: return
        val peer = current.peer
        try {
            val result = withContext(Dispatchers.Default) { current.plan.resolve(choices, "from ${peer.name}") }
            _client.value = ClientStep.Working(SyncStatus("Saving here…"))
            // this side first, if sending fails nothing is remembered and the same changes are offered again
            store.apply(result.localUpserts, result.localDeletes, emptyList())

            val request = buildJsonObject {
                put("action", "apply")
                put("syncId", app.settingsStore.deviceId() + "-" + System.currentTimeMillis())
                identity()
                put("upserts", json.encodeToJsonElement(records, result.remoteUpserts))
                put("deletes", json.encodeToJsonElement(refs, result.remoteDeletes))
                put("relinks", json.encodeToJsonElement(relinks, result.relinks))
                put("baseline", json.encodeToJsonElement(baselines, result.baseline))
            }
            SyncWire.call(current.address, current.key, request) { fraction ->
                _client.value = ClientStep.Working(SyncStatus("Sending to the ${peer.name}…", fraction))
            }
            savePeer(peer, current.address, result.baseline)

            val here = result.localUpserts.size + result.localDeletes.size
            val there = result.remoteUpserts.size + result.remoteDeletes.size
            _client.value = ClientStep.Done(
                if (here + there == 0) "Already in sync with the ${peer.name}" else "Synced with the ${peer.name}: ${changes(here)} here, ${changes(there)} there",
            )
        } catch (e: Exception) {
            _client.value = ClientStep.Failed(e.message ?: "Sync failed")
        } finally {
            session = null
        }
    }

    fun reset() {
        session = null
        _client.value = ClientStep.Idle
    }

    // makes the next sync with this device compare everything from scratch
    suspend fun forget(peerId: String) = dao.forgetPeer(peerId)

    // helpers

    private suspend fun hello(): JsonObject = buildJsonObject {
        put("action", "hello")
        put("app", "atlas")
        put("version", ATLAS_VERSION)
        put("protocol", SyncWire.PROTOCOL)
        identity()
    }

    private suspend fun request(action: String): JsonObject = buildJsonObject {
        put("action", action)
        identity()
    }

    private suspend fun kotlinx.serialization.json.JsonObjectBuilder.identity() {
        put("deviceId", app.settingsStore.deviceId())
        put("peerId", app.settingsStore.deviceId())
        put("name", app.platform.deviceName)
        put("kind", app.platform.kind.label)
    }

    private suspend fun baselineFor(peerId: String): Map<String, String> =
        dao.peer(peerId)?.baseline?.let { runCatching { json.decodeFromString(baselines, it) }.getOrNull() } ?: emptyMap()

    private suspend fun savePeer(peer: Peer, address: String?, baseline: Map<String, String>) {
        val old = dao.peer(peer.deviceId)
        dao.savePeer(
            SyncPeer(
                peerId = peer.deviceId,
                name = peer.name,
                kind = peer.kind,
                lastSync = System.currentTimeMillis(),
                address = address ?: old?.address,
                baseline = json.encodeToString(baselines, baseline),
            ),
        )
    }

    private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun changes(n: Int) = if (n == 1) "1 change" else "$n changes"
}
