package com.forsakenblank.atlas.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// how two devices talk: one tcp connection per request, each way a frame of
//   "ATLS" | 1 byte type | 4 byte length | body
// type 0 is json sealed with aes-gcm under a key made from the 6 digit code, type 1 is a plain error.
// a wrong code can not open the frame, so it is turned away before anything is read.
object SyncWire {
    const val PORT = 47830
    const val PROTOCOL = 1
    private val MAGIC = byteArrayOf('A'.code.toByte(), 'T'.code.toByte(), 'L'.code.toByte(), 'S'.code.toByte())
    private const val SEALED: Byte = 0
    private const val ERROR: Byte = 1
    private const val MAX_FRAME = 512 * 1024 * 1024

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun newCode(): String = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    // this computer's addresses on the local network, the ones the other device can reach
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    // a linux pc with ufw or firewalld switched on drops incoming connections unless the port is allowed,
    // which looks exactly like the other device not being there. android has no such firewall
    fun firewallHint(): String? = runCatching {
        if (!System.getProperty("os.name").orEmpty().contains("Linux") || System.getProperty("java.vm.name").orEmpty().contains("Dalvik")) {
            return@runCatching null
        }
        fun active(unit: String) = ProcessBuilder("systemctl", "is-active", "--quiet", unit).start().waitFor() == 0
        when {
            active("ufw") -> "Your firewall (ufw) is on. If the other device can't connect, run: sudo ufw allow $PORT/tcp"
            active("firewalld") -> "Your firewall (firewalld) is on. If the other device can't connect, run: sudo firewall-cmd --permanent --add-port=$PORT/tcp && sudo firewall-cmd --reload"
            else -> null
        }
    }.getOrNull()

    fun write(out: OutputStream, type: Byte, body: ByteArray, onProgress: (Float) -> Unit = {}) {
        val data = DataOutputStream(out.buffered(64 * 1024))
        data.write(MAGIC)
        data.writeByte(type.toInt())
        data.writeInt(body.size)
        var sent = 0
        while (sent < body.size) {
            val n = minOf(64 * 1024, body.size - sent)
            data.write(body, sent, n)
            sent += n
            onProgress(sent.toFloat() / body.size)
        }
        data.flush()
    }

    // returns the type and body of the next frame
    fun read(input: InputStream, onProgress: (Float) -> Unit = {}): Pair<Byte, ByteArray> {
        val data = DataInputStream(input.buffered(64 * 1024))
        val magic = ByteArray(4)
        data.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw SyncException("That is not an Atlas device")
        val type = data.readByte()
        val length = data.readInt()
        if (length < 0 || length > MAX_FRAME) throw SyncException("The other device sent something too big")
        val body = ByteArray(length)
        var got = 0
        while (got < length) {
            val n = data.read(body, got, minOf(64 * 1024, length - got))
            if (n < 0) throw EOFException()
            got += n
            onProgress(got.toFloat() / length)
        }
        return type to body
    }

    fun sealedReply(out: OutputStream, key: SyncKey, reply: JsonObject, onProgress: (Float) -> Unit = {}) =
        write(out, SEALED, key.seal(reply.toString().toByteArray()), onProgress)

    fun errorReply(out: OutputStream, message: String) = write(out, ERROR, message.toByteArray())

    // sends one request and waits for its answer, progress covers the upload then the download
    suspend fun call(address: String, key: SyncKey, request: JsonObject, onProgress: (Float) -> Unit = {}): JsonObject =
        withContext(Dispatchers.IO) {
            val (host, port) = splitAddress(address)
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), 8_000)
                socket.soTimeout = 5 * 60 * 1000
                write(socket.getOutputStream(), SEALED, key.seal(request.toString().toByteArray())) { onProgress(it * 0.5f) }
                val (type, body) = read(socket.getInputStream()) { onProgress(0.5f + it * 0.5f) }
                if (type == ERROR) throw SyncException(body.decodeToString())
                val text = runCatching { key.open(body) }.getOrElse { throw SyncException("The other device answered with a different code") }
                json.parseToJsonElement(text.decodeToString()).jsonObject
            } catch (e: SyncException) {
                throw e
            } catch (e: java.net.ConnectException) {
                // something answered at that address, but nothing is listening for atlas
                throw SyncException("$host is there, but Atlas isn't hosting on it. Turn on Let another device connect on that device (or check the address).")
            } catch (e: java.net.SocketTimeoutException) {
                // no answer at all: a firewall on the host drops it, or the address is on another network
                throw SyncException(
                    "No answer from $host. If it's a PC, its firewall is probably blocking Atlas: allow port $PORT " +
                        "(on Linux: sudo ufw allow $PORT/tcp, or sudo firewall-cmd --permanent --add-port=$PORT/tcp then sudo firewall-cmd --reload). " +
                        "Also check both are on the same Wi-Fi and not a guest network.",
                )
            } catch (e: java.net.UnknownHostException) {
                throw SyncException("\"$host\" isn't an address. Type the numbers the other device shows, like the ones under Let another device connect.")
            } catch (e: Exception) {
                throw SyncException("Couldn't reach $host (${e.javaClass.simpleName}: ${e.message}). Check the address and that both are on the same Wi-Fi.")
            } finally {
                runCatching { socket.close() }
            }
        }

    fun splitAddress(address: String): Pair<String, Int> {
        val clean = address.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
        val port = clean.substringAfterLast(':', "").toIntOrNull()
        return if (port != null) clean.substringBeforeLast(':') to port else clean to PORT
    }
}

class SyncException(message: String) : Exception(message)

// the shared secret both sides make from the code, slow on purpose so codes can not be guessed quickly
class SyncKey(code: String) {
    private val key: SecretKeySpec = run {
        val spec = PBEKeySpec(code.toCharArray(), "atlas-sync-1".toByteArray(), 120_000, 256)
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }
    private val random = SecureRandom()

    fun seal(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        return cipher.doFinal(sealed, 12, sealed.size - 12)
    }
}

// listens for one device at a time while hosting is on
class SyncServer(
    private val key: SyncKey,
    private val scope: CoroutineScope,
    private val onLockedOut: () -> Unit,
    private val handle: suspend (request: JsonObject, progress: (Float) -> Unit) -> JsonObject,
    private val onProgress: (Float?) -> Unit,
) {
    private var socket: ServerSocket? = null
    private var job: Job? = null
    private var wrongCodes = 0

    fun start() {
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(SyncWire.PORT))
        socket = server
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = try {
                    server.accept()
                } catch (e: SocketException) {
                    break
                }
                runCatching { serve(client) }
                runCatching { client.close() }
                onProgress(null)
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
    }

    private suspend fun serve(client: Socket) {
        client.soTimeout = 5 * 60 * 1000
        val out = client.getOutputStream()
        val (type, body) = SyncWire.read(client.getInputStream()) { onProgress(it) }
        if (wrongCodes >= 10) {
            SyncWire.errorReply(out, "Too many wrong codes. Turn hosting off and on again on this device.")
            return
        }
        val plain = if (type == 0.toByte()) runCatching { key.open(body) }.getOrNull() else null
        if (plain == null) {
            wrongCodes++
            SyncWire.errorReply(out, "Wrong code")
            if (wrongCodes >= 10) onLockedOut()
            return
        }
        val request = SyncWire.json.parseToJsonElement(plain.decodeToString()).jsonObject
        val reply = try {
            handle(request) { onProgress(it) }
        } catch (e: Exception) {
            SyncWire.errorReply(out, e.message ?: "Something went wrong on this device")
            return
        }
        SyncWire.sealedReply(out, key, reply) { onProgress(it) }
    }
}
