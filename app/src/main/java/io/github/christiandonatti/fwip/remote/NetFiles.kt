package io.github.christiandonatti.fwip.remote

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.content.Context
import android.net.Uri
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** A file or folder in a network folder. [path] is relative to the folder the user added, with '/'. */
data class NetEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

/** A remote file that can be read at any position. */
interface RandomFile : Closeable {
    val length: Long

    /** Reads up to [len] bytes at [pos]; -1 at the end of the file. */
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int
}

/** What the app needs from a network folder (SMB share or WebDAV). */
interface NetClient {
    fun list(path: String): List<NetEntry>
    fun open(path: String): RandomFile
    fun write(path: String, bytes: ByteArray)
    fun mkdirs(path: String)
    /** Streams a (possibly large) file to [path], replacing what is there. */
    fun upload(path: String, length: Long, input: java.io.InputStream)
    fun delete(path: String)
    /** Renames [from] to [to] in the same share; fails if [to] exists. */
    fun rename(from: String, to: String)

    /** Copies the whole file at [path] into [out]; [onProgress] gets bytes done and the total. */
    fun download(path: String, out: java.io.OutputStream, onProgress: (Long, Long) -> Unit) {
        BlockReader(open(path), blockSize = 1 shl 20, maxBlocks = 2).use { reader ->
            val buf = ByteArray(256 * 1024)
            var done = 0L
            reader.inputStream().use { input ->
                while (true) {
                    val n = input.read(buf, 0, buf.size)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done, reader.length)
                }
            }
        }
    }

    /** Copies the whole file at [path] into [file] (replacing it), maybe over several connections. */
    fun downloadTo(path: String, file: java.io.File, onProgress: (Long, Long) -> Unit) {
        file.outputStream().buffered(1 shl 20).use { download(path, it, onProgress) }
    }
}

/** Network folder files are referenced as fwip-net://<server id>/<path>. */
object NetUris {
    const val SCHEME = "fwip-net"

    fun of(serverId: String, path: String): Uri =
        Uri.Builder().scheme(SCHEME).authority(serverId).path("/" + path.trim('/')).build()

    fun isNet(uri: Uri) = uri.scheme == SCHEME

    fun serverId(uri: Uri): String = uri.authority.orEmpty()

    fun path(uri: Uri): String = uri.path.orEmpty().trim('/')
}

/** One client per server, kept open between uses (an SMB session is slow to set up). */
object NetClients {
    private val clients = ConcurrentHashMap<String, Pair<Server, NetClient>>()

    fun forServer(server: Server): NetClient {
        clients[server.id]?.let { (s, c) -> if (s == server) return c }
        val client: NetClient = when (server.kind) {
            ServerKind.Smb -> SmbNet(server)
            ServerKind.WebDav -> WebDavNet(server)
            else -> throw IOException("Not a network folder")
        }
        clients.put(server.id, server to client)?.second?.let { (it as? Closeable)?.let { c -> runCatching { c.close() } } }
        return client
    }

    fun forUri(context: Context, uri: Uri): NetClient {
        val id = NetUris.serverId(uri)
        clients[id]?.let { return it.second }
        val server = ServerStore(context).load().firstOrNull { it.id == id }
            ?: throw IOException(str(R.string.error_net_server_missing))
        return forServer(server)
    }

    fun forget(serverId: String) {
        clients.remove(serverId)?.second?.let { (it as? Closeable)?.let { c -> runCatching { c.close() } } }
    }
}

/**
 * Reads a [RandomFile] in blocks and keeps recent blocks in memory, so the many small reads
 * archive libraries make turn into a few network requests.
 */
class BlockReader(
    private val file: RandomFile,
    private val blockSize: Int = 256 * 1024,
    private val maxBlocks: Int = 24,
) : Closeable {
    val length: Long get() = file.length

    private val blocks = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?) = size > maxBlocks
    }

    @Synchronized
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= length) return -1
        var done = 0
        var p = pos
        while (done < len && p < length) {
            val index = p / blockSize
            val block = blocks[index] ?: fetch(index).also { blocks[index] = it }
            val inBlock = (p - index * blockSize).toInt()
            val n = min(len - done, block.size - inBlock)
            if (n <= 0) break
            System.arraycopy(block, inBlock, buf, off + done, n)
            done += n
            p += n
        }
        return done
    }

    private fun fetch(index: Long): ByteArray {
        val start = index * blockSize
        val size = min(blockSize.toLong(), length - start).toInt()
        val b = ByteArray(size)
        var got = 0
        while (got < size) {
            val n = file.read(start + got, b, got, size - got)
            if (n <= 0) throw EOFException()
            got += n
        }
        return b
    }

    fun inputStream(start: Long = 0): InputStream = object : InputStream() {
        private var pos = start
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = this@BlockReader.read(pos, b, off, len)
            if (n > 0) pos += n
            return n
        }
        override fun skip(n: Long): Long {
            val k = min(n, length - pos).coerceAtLeast(0)
            pos += k
            return k
        }
        override fun available(): Int = min(Int.MAX_VALUE.toLong(), length - pos).toInt().coerceAtLeast(0)
    }

    override fun close() = file.close()
}

/** [BlockReader] as a seekable channel for Commons Compress (CBZ, CB7). */
class BlockChannel(private val reader: BlockReader) : SeekableByteChannel {
    private var pos = 0L
    private var open = true

    override fun read(dst: ByteBuffer): Int {
        val want = dst.remaining()
        if (want == 0) return 0
        val tmp = ByteArray(min(want, 1 shl 20))
        val n = reader.read(pos, tmp, 0, tmp.size)
        if (n <= 0) return -1
        dst.put(tmp, 0, n)
        pos += n
        return n
    }

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
    override fun position(): Long = pos
    override fun position(newPosition: Long): SeekableByteChannel { pos = newPosition; return this }
    override fun size(): Long = reader.length
    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
    override fun isOpen(): Boolean = open
    override fun close() { open = false }
}

/** [BlockReader] as a junrar volume, so CBR headers are read without downloading the whole file. */
class NetRarVolumes(private val reader: BlockReader) : com.github.junrar.volume.VolumeManager {
    override fun nextVolume(archive: com.github.junrar.Archive, lastVolume: com.github.junrar.volume.Volume?): com.github.junrar.volume.Volume? {
        if (lastVolume != null) return null
        return object : com.github.junrar.volume.Volume {
            override fun getChannel(): com.github.junrar.io.SeekableReadOnlyByteChannel = RarChannel(reader)
            override fun getLength(): Long = reader.length
            override fun getArchive(): com.github.junrar.Archive = archive
        }
    }

    private class RarChannel(private val reader: BlockReader) : com.github.junrar.io.SeekableReadOnlyByteChannel {
        private var pos = 0L
        override fun getPosition(): Long = pos
        override fun setPosition(p: Long) { pos = p }
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
        }
        override fun read(buffer: ByteArray, off: Int, count: Int): Int {
            val n = reader.read(pos, buffer, off, count)
            if (n > 0) pos += n
            return n
        }
        override fun readFully(buffer: ByteArray, count: Int): Int {
            var got = 0
            while (got < count) {
                val n = read(buffer, got, count - got)
                if (n <= 0) return -1
                got += n
            }
            return got
        }
        override fun close() {}
    }
}

/** Splits "DOMAIN\user" (or "user@domain") into user and domain. */
internal fun splitDomain(user: String): Pair<String, String?> = when {
    user.contains('\\') -> user.substringAfter('\\') to user.substringBefore('\\')
    else -> user to null
}

// ---------------------------------------------------------------- SMB

/** A Windows/Samba share (most NAS): smb://host/share/optional/folder. */
/** How files are sent to SMB shares (the network test compares the two ways). */
internal object SmbTuning {
    @Volatile var pipelined = true
    /** The system's AES for packet signing instead of Bouncy Castle's. */
    @Volatile var fastCrypto = true
    /** Largest read or write per request (the NAS may allow less). */
    @Volatile var bufferSize = 4 shl 20
    /** Bytes per write request, and how many may wait for an answer at once. */
    const val chunk = 1 shl 20
    const val depth = 8
    /** Network streams a big download is split over. */
    @Volatile var connections = 2
}

internal class SmbNet(private val server: Server) : NetClient, Closeable {
    private val hostPort: String
    private val shareName: String
    private val base: String

    init {
        val parts = server.url.substringAfter("://").replace('\\', '/').split('/').filter { it.isNotBlank() }
        hostPort = parts.getOrNull(0) ?: throw IOException(str(R.string.error_smb_address))
        shareName = parts.getOrNull(1) ?: throw IOException(str(R.string.error_smb_share))
        base = parts.drop(2).joinToString("/")
    }

    private var client: com.hierynomus.smbj.SMBClient? = null
    private var session: com.hierynomus.smbj.session.Session? = null
    private var share: com.hierynomus.smbj.share.DiskShare? = null

    @Synchronized
    private fun share(): com.hierynomus.smbj.share.DiskShare {
        share?.takeIf { it.isConnected }?.let { return it }
        reset()
        val config = com.hierynomus.smbj.SmbConfig.builder()
            .withSecurityProvider(if (SmbTuning.fastCrypto) FastSecurityProvider() else com.hierynomus.security.bc.BCSecurityProvider())
            .withTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .withSoTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            // Bigger reads and writes per request (the NAS caps them at what it supports).
            .withReadBufferSize(SmbTuning.bufferSize)
            .withWriteBufferSize(SmbTuning.bufferSize)
            .build()
        val c = com.hierynomus.smbj.SMBClient(config)
        client = c
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "").toIntOrNull() ?: 445
        val connection = c.connect(host, port)
        val auth = if (server.user.isBlank()) {
            com.hierynomus.smbj.auth.AuthenticationContext.guest()
        } else {
            val (user, domain) = splitDomain(server.user)
            com.hierynomus.smbj.auth.AuthenticationContext(user, server.password.toCharArray(), domain)
        }
        val s = connection.authenticate(auth)
        session = s
        runCatching {
            android.util.Log.i(
                "Fwip",
                "smb: ${connection.negotiatedProtocol.dialect}, signing ${if (s.isSigningRequired) "on" else "off"}, " +
                    "encryption ${if (s.shouldEncryptData()) "on" else "off"}, max read ${connection.negotiatedProtocol.maxReadSize shr 10} KB, " +
                    "max write ${connection.negotiatedProtocol.maxWriteSize shr 10} KB",
            )
        }
        val sh = s.connectShare(shareName) as? com.hierynomus.smbj.share.DiskShare
            ?: throw IOException(str(R.string.error_smb_share))
        share = sh
        return sh
    }

    @Synchronized
    internal fun reset() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { client?.close() }
        share = null
        session = null
        client = null
    }

    /** Runs [block], reconnecting once if the old connection went away (Wi-Fi change, NAS asleep). */
    private fun <T> withShare(block: (com.hierynomus.smbj.share.DiskShare) -> T): T = try {
        block(share())
    } catch (e: com.hierynomus.mssmb2.SMBApiException) {
        throw e
    } catch (e: Exception) {
        reset()
        block(share())
    }

    private fun full(path: String) = listOf(base, path.trim('/')).filter { it.isNotEmpty() }.joinToString("\\").replace('/', '\\')

    override fun list(path: String): List<NetEntry> = withShare { sh ->
        sh.list(full(path)).mapNotNull { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@mapNotNull null
            val attrs = info.fileAttributes
            val dir = attrs and com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val hidden = attrs and com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_HIDDEN.value != 0L
            if (hidden || name.startsWith(".") || name.startsWith("@") || name == "#recycle") return@mapNotNull null
            NetEntry(
                name = name,
                path = listOf(path.trim('/'), name).filter { it.isNotEmpty() }.joinToString("/"),
                isDir = dir,
                size = info.endOfFile,
                modified = runCatching { info.lastWriteTime.toEpochMillis() }.getOrDefault(0L),
            )
        }
    }

    override fun open(path: String): RandomFile = withShare { sh ->
        val f = sh.openFile(
            full(path),
            java.util.EnumSet.of(com.hierynomus.msdtyp.AccessMask.GENERIC_READ),
            null,
            com.hierynomus.mssmb2.SMB2ShareAccess.ALL,
            com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        val len = f.getFileInformation(com.hierynomus.msfscc.fileinformation.FileStandardInformation::class.java).endOfFile
        object : RandomFile {
            override val length: Long = len
            override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int = f.read(buf, pos, off, len)
            override fun close() { runCatching { f.close() } }
        }
    }

    private fun openRead(path: String): com.hierynomus.smbj.share.File = withShare { sh ->
        sh.openFile(
            full(path),
            java.util.EnumSet.of(com.hierynomus.msdtyp.AccessMask.GENERIC_READ),
            null,
            com.hierynomus.mssmb2.SMB2ShareAccess.ALL,
            com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OPEN,
            null,
        )
    }

    private fun lengthOf(f: com.hierynomus.smbj.share.File): Long =
        f.getFileInformation(com.hierynomus.msfscc.fileinformation.FileStandardInformation::class.java).endOfFile

    /**
     * Reads [from] until [to] of [f], handing each piece to [sink] in order (position, bytes, count)
     * and the count to [moved]. Like sending: several pieces are asked for at once. Pieces never ask
     * for more than the NAS answers in one go (old SMB 2.0 servers: 64 KB), and about 8 MB stay on
     * the way whatever their size. After [deadline] (System.nanoTime) it stops asking and returns.
     */
    private fun readRange(
        f: com.hierynomus.smbj.share.File,
        from: Long,
        to: Long,
        deadline: Long = Long.MAX_VALUE,
        moved: (Int) -> Unit,
        sink: (Long, ByteArray, Int) -> Unit,
    ) {
        if (!SmbTuning.pipelined) {
            var pos = from
            val buf = ByteArray(SmbTuning.chunk)
            while (pos < to && System.nanoTime() < deadline) {
                val n = f.read(buf, pos, 0, minOf(buf.size.toLong(), to - pos).toInt())
                if (n <= 0) break
                sink(pos, buf, n)
                pos += n
                moved(n)
            }
            return
        }
        val chunk = minOf(SmbTuning.chunk, com.hierynomus.smbj.share.FwipReads.readSize(f).coerceAtLeast(64 * 1024))
        val depth = (SmbTuning.depth * SmbTuning.chunk / chunk).coerceIn(SmbTuning.depth, 64)
        val inFlight = ArrayDeque<Triple<java.util.concurrent.Future<com.hierynomus.mssmb2.messages.SMB2ReadResponse>, Long, Int>>()
        var next = from
        while (inFlight.isNotEmpty() || next < to) {
            while (inFlight.size < depth && next < to && System.nanoTime() < deadline) {
                val n = minOf(chunk.toLong(), to - next).toInt()
                inFlight.addLast(Triple(com.hierynomus.smbj.share.FwipReads.readAsync(f, next, n), next, n))
                next += n
            }
            if (inFlight.isEmpty()) break
            val (future, at, asked) = inFlight.removeFirst()
            val data = future.get(60, java.util.concurrent.TimeUnit.SECONDS).data
            if (data.isEmpty()) throw IOException(str(R.string.error_read_file))
            sink(at, data, data.size)
            moved(data.size)
            var got = data.size
            // A short answer (rare): fetch the rest of that piece before moving on.
            while (got < asked) {
                val rest = ByteArray(asked - got)
                val n = f.read(rest, at + got, 0, rest.size)
                if (n <= 0) throw IOException(str(R.string.error_read_file))
                sink(at + got, rest, n)
                moved(n)
                got += n
            }
        }
    }

    override fun download(path: String, out: java.io.OutputStream, onProgress: (Long, Long) -> Unit) {
        // Not through withShare: half the file may already be written out.
        openRead(path).use { f ->
            val length = lengthOf(f)
            var done = 0L
            readRange(f, 0, length, moved = { done += it; onProgress(done, length) }) { _, data, n -> out.write(data, 0, n) }
        }
    }

    /** Extra connections to the same share, each its own network stream, for big downloads. */
    private val helpers = mutableMapOf<Int, SmbNet>()

    @Synchronized
    private fun helper(k: Int): SmbNet = helpers.getOrPut(k) { SmbNet(server) }

    /**
     * Reads [path] over [connections] network streams at once, each a slice of the file. Wi-Fi often
     * gives two streams more than twice what it gives one, and the NAS answers each on its own core.
     * Slices whose connection fails are read again on the first one. Returns the file's length.
     */
    internal fun readParallel(
        path: String,
        connections: Int,
        deadline: Long = Long.MAX_VALUE,
        onProgress: (Long, Long) -> Unit,
        sink: (Long, ByteArray, Int) -> Unit,
    ): Long = openRead(path).use { f ->
        val length = lengthOf(f)
        val n = if (length < 24L shl 20) 1 else connections.coerceIn(1, 4)
        val done = java.util.concurrent.atomic.AtomicLong()
        val lock = Any()
        fun report(k: Int) { val d = done.addAndGet(k.toLong()); synchronized(lock) { onProgress(d, length) } }
        val step = ((length + n - 1) / n + SmbTuning.chunk - 1) / SmbTuning.chunk * SmbTuning.chunk
        val slices = (0 until n).map { it * step to minOf(length, (it + 1) * step) }.filter { it.first < it.second }
        if (slices.size == 1) {
            readRange(f, 0, length, deadline, ::report, sink)
            return@use length
        }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(slices.size - 1)
        try {
            val others = slices.drop(1).mapIndexed { i, (a, b) ->
                pool.submit(java.util.concurrent.Callable<Long> {
                    var moved = 0L
                    try {
                        helper(i + 1).openRead(path).use { g -> readRange(g, a, b, deadline, { moved += it; report(it) }, sink) }
                        -1L
                    } catch (e: Exception) {
                        runCatching { android.util.Log.w("Fwip", "smb: extra connection failed, " + e) }
                        runCatching { helper(i + 1).reset() }
                        done.addAndGet(-moved)
                        a
                    }
                })
            }
            readRange(f, slices[0].first, slices[0].second, deadline, ::report, sink)
            others.forEachIndexed { i, fut ->
                if (fut.get() >= 0) {
                    val (a, b) = slices[i + 1]
                    readRange(f, a, b, deadline, ::report, sink)
                }
            }
        } finally {
            pool.shutdownNow()
        }
        length
    }

    /**
     * How fast this NAS sends to this device, in MB/s, over [connections] streams: reads [path]
     * for about [seconds] and throws the bytes away, so the device's storage plays no part.
     */
    internal fun measure(path: String, connections: Int, seconds: Double): Double {
        val start = System.nanoTime()
        var bytes = 0L
        readParallel(path, connections, start + (seconds * 1e9).toLong(), onProgress = { d, _ -> bytes = d }) { _, _, _ -> }
        return bytes / 1e6 / ((System.nanoTime() - start) / 1e9).coerceAtLeast(0.001)
    }

    /** The biggest file in the first levels of the share (a good one to measure with). */
    internal fun biggestFile(): NetEntry? {
        var best: NetEntry? = null
        var looked = 0
        fun walk(path: String, depth: Int) {
            if (looked > 400) return
            val entries = runCatching { list(path) }.getOrDefault(emptyList())
            looked += entries.size
            entries.filter { !it.isDir }.maxByOrNull { it.size }?.let { if (it.size > (best?.size ?: 0)) best = it }
            if ((best?.size ?: 0) >= 200L shl 20 || depth >= 3) return
            entries.filter { it.isDir && !it.name.startsWith(".") && !it.name.startsWith("@") && !it.name.startsWith("#") }.take(12).forEach { walk(it.path, depth + 1) }
        }
        walk("", 0)
        return best
    }

    override fun downloadTo(path: String, file: java.io.File, onProgress: (Long, Long) -> Unit) {
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val channel = raf.channel
            readParallel(path, SmbTuning.connections, onProgress = onProgress) { pos, data, n ->
                val buf = java.nio.ByteBuffer.wrap(data, 0, n)
                var at = pos
                while (buf.hasRemaining()) at += channel.write(buf, at)
            }
        }
    }

    override fun write(path: String, bytes: ByteArray) {
        withShare { sh ->
            sh.openFile(
                full(path),
                java.util.EnumSet.of(com.hierynomus.msdtyp.AccessMask.GENERIC_WRITE),
                null,
                com.hierynomus.mssmb2.SMB2ShareAccess.ALL,
                com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null,
            ).use { f -> f.outputStream.use { it.write(bytes) } }
        }
    }

    override fun mkdirs(path: String) {
        withShare { sh ->
            var current = ""
            for (part in path.trim('/').split('/').filter { it.isNotEmpty() }) {
                current = if (current.isEmpty()) part else "$current/$part"
                if (!sh.folderExists(full(current))) sh.mkdir(full(current))
            }
        }
    }

    override fun upload(path: String, length: Long, input: java.io.InputStream) {
        // Not through withShare: a half-read stream cannot be sent again.
        share().openFile(
            full(path),
            java.util.EnumSet.of(com.hierynomus.msdtyp.AccessMask.GENERIC_WRITE),
            null,
            com.hierynomus.mssmb2.SMB2ShareAccess.ALL,
            com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null,
        ).use { f ->
            if (!SmbTuning.pipelined) {
                f.outputStream.use { out -> input.copyTo(out, 1 shl 20) }
                return
            }
            // Several writes in flight at once: waiting for the NAS to answer each piece before
            // sending the next one wastes most of the time on Wi-Fi.
            val inFlight = ArrayDeque<Pair<java.util.concurrent.Future<Long>, ByteArray>>()
            val spare = ArrayDeque<ByteArray>()
            var offset = 0L
            fun waitOldest() {
                val (future, buf) = inFlight.removeFirst()
                future.get(60, java.util.concurrent.TimeUnit.SECONDS)
                spare.addLast(buf)
            }
            while (true) {
                val buf = spare.removeFirstOrNull() ?: ByteArray(SmbTuning.chunk)
                var n = 0
                while (n < buf.size) {
                    val r = input.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                if (n == 0) break
                inFlight.addLast(f.writeAsync(buf, offset, 0, n) to buf)
                offset += n
                while (inFlight.size >= SmbTuning.depth) waitOldest()
                if (n < buf.size) break
            }
            while (inFlight.isNotEmpty()) waitOldest()
        }
    }

    override fun delete(path: String) {
        withShare { sh -> sh.rm(full(path)) }
    }

    override fun rename(from: String, to: String) {
        withShare { sh ->
            sh.openFile(
                full(from),
                java.util.EnumSet.of(com.hierynomus.msdtyp.AccessMask.DELETE, com.hierynomus.msdtyp.AccessMask.GENERIC_WRITE),
                null,
                com.hierynomus.mssmb2.SMB2ShareAccess.ALL,
                com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { f -> f.rename(full(to), false) }
        }
    }

    override fun close() {
        reset()
        synchronized(this) { helpers.values.forEach { runCatching { it.reset() } }; helpers.clear() }
    }
}

// ---------------------------------------------------------------- WebDAV

/** A WebDAV folder (Nextcloud, NAS web folders, Apache/nginx). */
internal class WebDavNet(private val server: Server) : NetClient {
    private val base = server.url.trim().trimEnd('/')
    private val basePath: String = Uri.decode(Uri.parse(base).encodedPath.orEmpty()).trimEnd('/')

    private val http: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun url(path: String, dir: Boolean = false): String {
        val encoded = path.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }
        return base + (if (encoded.isEmpty()) "" else "/$encoded") + (if (dir) "/" else "")
    }

    private fun request(url: String) = okhttp3.Request.Builder().url(url).apply {
        server.authHeader?.let { header("Authorization", it) }
    }

    private fun check(r: okhttp3.Response): okhttp3.Response {
        if (!r.isSuccessful) {
            val code = r.code
            r.close()
            throw HttpException(code, if (code == 401 || code == 403) str(R.string.error_login) else str(R.string.error_http, code))
        }
        return r
    }

    override fun list(path: String): List<NetEntry> {
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
        val req = request(url(path, dir = true))
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .build()
        val xml = check(http.newCall(req).execute()).use { it.body?.string().orEmpty() }
        val self = (basePath + "/" + path.trim('/')).trimEnd('/')
        return parseMultistatus(xml).mapNotNull { (href, dir, size, modified) ->
            val hrefPath = if (href.contains("://")) Uri.parse(href).encodedPath.orEmpty() else href
            val decoded = Uri.decode(hrefPath).trimEnd('/')
            if (decoded == self || decoded.isEmpty()) return@mapNotNull null
            val name = decoded.substringAfterLast('/')
            if (name.startsWith(".")) return@mapNotNull null
            NetEntry(name, listOf(path.trim('/'), name).filter { it.isNotEmpty() }.joinToString("/"), dir, size, modified)
        }
    }

    private data class DavItem(val href: String, val dir: Boolean, val size: Long, val modified: Long)

    private fun parseMultistatus(xml: String): List<DavItem> {
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(xml.reader())
        val out = mutableListOf<DavItem>()
        var href = ""
        var dir = false
        var size = 0L
        var modified = 0L
        var text = StringBuilder()
        while (true) {
            when (parser.next()) {
                org.xmlpull.v1.XmlPullParser.END_DOCUMENT -> break
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (parser.name) {
                        "response" -> { href = ""; dir = false; size = 0L; modified = 0L }
                        "collection" -> dir = true
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> text.append(parser.text)
                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                    "href" -> href = text.toString().trim()
                    "getcontentlength" -> size = text.toString().trim().toLongOrNull() ?: 0L
                    "getlastmodified" -> modified = runCatching {
                        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).parse(text.toString().trim())?.time
                    }.getOrNull() ?: 0L
                    "response" -> if (href.isNotEmpty()) out += DavItem(href, dir, size, modified)
                }
            }
        }
        return out
    }

    override fun open(path: String): RandomFile {
        val target = url(path)
        val len = check(http.newCall(request(target).head().build()).execute()).use { r ->
            r.header("Content-Length")?.toLongOrNull() ?: -1L
        }.takeIf { it >= 0 } ?: throw IOException(str(R.string.error_read_file))
        return object : RandomFile {
            override val length: Long = len
            override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
                if (pos >= length) return -1
                val end = min(pos + len, length) - 1
                val r = check(http.newCall(request(target).header("Range", "bytes=$pos-$end").build()).execute())
                r.use { resp ->
                    val stream = resp.body?.byteStream() ?: return -1
                    // A server that ignores Range sends the whole file from the start: skip ahead.
                    if (resp.code == 200 && pos > 0) {
                        var skipped = 0L
                        while (skipped < pos) {
                            val s = stream.skip(pos - skipped)
                            if (s <= 0) return -1
                            skipped += s
                        }
                    }
                    var got = 0
                    val want = (end - pos + 1).toInt()
                    while (got < want) {
                        val n = stream.read(buf, off + got, want - got)
                        if (n <= 0) break
                        got += n
                    }
                    return if (got == 0) -1 else got
                }
            }
            override fun close() {}
        }
    }

    override fun write(path: String, bytes: ByteArray) {
        val req = request(url(path)).put(bytes.toRequestBody("application/octet-stream".toMediaType())).build()
        check(http.newCall(req).execute()).close()
    }

    override fun upload(path: String, length: Long, input: java.io.InputStream) {
        val body = object : okhttp3.RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: okio.BufferedSink) {
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    sink.write(buf, 0, n)
                }
            }
        }
        check(http.newCall(request(url(path)).put(body).build()).execute()).close()
    }

    override fun delete(path: String) {
        check(http.newCall(request(url(path)).delete().build()).execute()).close()
    }

    override fun download(path: String, out: java.io.OutputStream, onProgress: (Long, Long) -> Unit) {
        // One plain request for the whole file, instead of a ranged request per block.
        check(http.newCall(request(url(path)).build()).execute()).use { r ->
            val body = r.body ?: throw IOException(str(R.string.error_read_file))
            val total = body.contentLength()
            val buf = ByteArray(256 * 1024)
            var done = 0L
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done, if (total > 0) total else done)
                }
            }
        }
    }

    override fun rename(from: String, to: String) {
        val req = request(url(from)).method("MOVE", null).header("Destination", url(to)).header("Overwrite", "F").build()
        check(http.newCall(req).execute()).close()
    }

    override fun mkdirs(path: String) {
        var current = ""
        for (part in path.trim('/').split('/').filter { it.isNotEmpty() }) {
            current = if (current.isEmpty()) part else "$current/$part"
            val r = http.newCall(request(url(current, dir = true)).method("MKCOL", null).build()).execute()
            // 405: it already exists.
            r.close()
            if (!r.isSuccessful && r.code != 405) throw HttpException(r.code, str(R.string.error_http, r.code))
        }
    }

}

/** Comics in network folders: building them from listings, and copying them for offline reading. */
object NetComics {
    fun comic(serverId: String, e: NetEntry, folder: String): io.github.christiandonatti.fwip.data.Comic? {
        val format = io.github.christiandonatti.fwip.data.ComicFormat.fromFileName(e.name) ?: return null
        val uri = NetUris.of(serverId, e.path)
        return io.github.christiandonatti.fwip.data.Comic(
            id = uri.toString(),
            uri = uri,
            fileName = e.name,
            title = io.github.christiandonatti.fwip.data.prettyTitle(e.name),
            folder = folder,
            format = format,
            size = e.size,
            lastModified = e.modified,
        )
    }

    /** Walks a network folder and its subfolders (hidden and system folders skipped). */
    fun walk(client: NetClient, serverId: String, path: String, folder: String, depth: Int, out: MutableMap<String, io.github.christiandonatti.fwip.data.Comic>) {
        if (depth > 8) return
        val entries = client.list(path)
        for (e in entries) {
            if (!e.isDir) comic(serverId, e, folder)?.let { out[it.id] = it }
        }
        for (e in entries) {
            if (e.isDir) walk(client, serverId, e.path, e.name, depth + 1, out)
        }
    }

    /** Copies a comic from a network folder into the app's downloads, for reading offline. */
    suspend fun download(
        context: Context,
        comic: io.github.christiandonatti.fwip.data.Comic,
        onProgress: (Float) -> Unit,
    ): java.io.File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val client = NetClients.forUri(context, comic.uri)
        val dir = RemoteComics.downloadsDir(context)
        val target = java.io.File(dir, comic.fileName)
        val tmp = java.io.File(dir, comic.fileName + ".part")
        try {
            client.downloadTo(NetUris.path(comic.uri), tmp) { done, total -> onProgress(done / total.coerceAtLeast(1).toFloat()) }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) throw IOException(str(R.string.error_read_file))
        target
    }
}
