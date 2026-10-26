package io.github.christiandonatti.fwip.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import io.github.christiandonatti.fwip.remote.RemoteComics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Comics sent from a computer's browser over Wi-Fi: while the screen is open, Fwip serves a small
 * page at http://<tablet>:<port>/?k=<code> where files are dropped. Only someone who sees the code
 * on the tablet can send anything, and only comic files are accepted.
 */
object WifiTransfer {
    data class Status(
        val running: Boolean = false,
        val address: String? = null,
        /** Files received in this session, newest last. */
        val received: List<String> = emptyList(),
        /** The file coming in now and how far along (0..1). */
        val current: String? = null,
        val progress: Float = 0f,
        val error: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var server: ServerSocket? = null
    private var pool = Executors.newCachedThreadPool()
    private var token = ""
    /** Where files go: a library folder (document tree) or, when null, the app's downloads. */
    @Volatile private var target: Uri? = null
    private lateinit var appContext: Context

    /** Called after each file lands, so the library can pick it up. */
    @Volatile var onReceived: (() -> Unit)? = null

    fun setTarget(uri: Uri?) { target = uri }

    fun start(context: Context, into: Uri?) {
        stop()
        appContext = context.applicationContext
        target = into
        token = (1000..9999).random().toString()
        val ip = wifiAddress(context)
        val socket = runCatching { ServerSocket(PORT) }.recoverCatching { ServerSocket(0) }.getOrElse {
            _status.value = Status(error = it.message)
            return
        }
        server = socket
        if (pool.isShutdown) pool = Executors.newCachedThreadPool()
        _status.value = Status(running = true, address = ip?.let { "http://$it:${socket.localPort}/?k=$token" })
        pool.execute {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                pool.execute { runCatching { serve(client) }.onFailure { Log.w("Fwip", "wifi transfer", it) } }
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        _status.update { it.copy(running = false, address = null, current = null) }
    }

    private const val PORT = 8080

    /** The device's own address on the local network. */
    fun wifiAddress(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    // ---------------------------------------------------------------- HTTP

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: InputStream)

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun parse(input: InputStream): Request? {
        val line = readLine(input) ?: return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
        }
        val full = parts[1]
        val path = full.substringBefore('?')
        val query = full.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return Request(parts[0], path, query, headers, input)
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Error" }
        out.write("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun serve(socket: Socket) {
        socket.use { s -> handle(s) }
    }

    private fun handle(s: Socket) {
        s.soTimeout = 60_000
        val input = s.getInputStream().buffered(64 * 1024)
        val out = s.getOutputStream().buffered()
        val req = parse(input) ?: return
        if (req.query["k"] != token) {
            respond(out, 403, "text/plain; charset=utf-8", "Fwip: wrong or missing code.".toByteArray())
            return
        }
        when {
            req.method == "GET" && req.path == "/" -> respond(out, 200, "text/html; charset=utf-8", page().toByteArray())
            req.method == "POST" && req.path == "/upload" -> {
                val name = req.query["name"]?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
                val length = req.headers["content-length"]?.toLongOrNull() ?: -1L
                if (name.isEmpty() || name.startsWith(".") || ComicFormat.fromFileName(name) == null || length < 0) {
                    respond(out, 400, "text/plain; charset=utf-8", "Only CBZ, CBR, CB7 and PDF files.".toByteArray())
                    return
                }
                val result = runCatching { receive(name, length, input) }
                result.onSuccess { saved ->
                    _status.update { it.copy(received = it.received + saved, current = null, progress = 0f) }
                    onReceived?.invoke()
                    respond(out, 200, "application/json", """{"ok":true,"name":${json(saved)}}""".toByteArray())
                }.onFailure { e ->
                    _status.update { it.copy(current = null, error = e.message) }
                    respond(out, 500, "text/plain; charset=utf-8", (e.message ?: "error").toByteArray())
                }
            }
            else -> respond(out, 404, "text/plain", "Not found".toByteArray())
        }
    }

    private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Copies exactly [length] bytes of the request into the target folder; returns the saved name. */
    private fun receive(name: String, length: Long, input: InputStream): String {
        _status.update { it.copy(current = name, progress = 0f, error = null) }
        val copy: (OutputStream) -> Unit = { out ->
            val buf = ByteArray(256 * 1024)
            var left = length
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) throw java.io.IOException("connection closed")
                out.write(buf, 0, n)
                left -= n
                _status.update { it.copy(progress = 1f - left / length.coerceAtLeast(1).toFloat()) }
            }
        }
        val tree = target
        if (tree == null) {
            val dir = RemoteComics.downloadsDir(appContext)
            val finalName = freeName(name, dir.list()?.toSet().orEmpty())
            val tmp = File(dir, "$finalName.part")
            try {
                tmp.outputStream().buffered(1 shl 20).use(copy)
                if (!tmp.renameTo(File(dir, finalName))) throw java.io.IOException("rename failed")
            } catch (e: Exception) {
                tmp.delete(); throw e
            }
            return finalName
        }
        val cr = appContext.contentResolver
        val parentId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val taken = mutableSetOf<String>()
        cr.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> while (c.moveToNext()) c.getString(0)?.let(taken::add) }
        val finalName = freeName(name, taken)
        val doc = DocumentsContract.createDocument(cr, parent, "application/octet-stream", finalName) ?: throw java.io.IOException("cannot create file")
        try {
            cr.openOutputStream(doc, "w")!!.buffered(1 shl 20).use(copy)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(cr, doc) }
            throw e
        }
        return finalName
    }

    private fun freeName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var n = 2
        while (true) {
            val c = "$base ($n)" + if (ext.isNotEmpty()) ".$ext" else ""
            if (c !in taken) return c
            n++
        }
    }

    /** The page the computer sees: drop files, watch them go. */
    private fun page(): String = """<!doctype html>
<html lang="pt"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Fwip</title>
<style>
:root{color-scheme:dark;--bg:#14151b;--card:#1f2029;--line:#34353f;--text:#f1eee6;--soft:#a9a6a0;--amber:#f2b544;--ink:#2a1d00}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--text);display:flex;justify-content:center;padding:40px 16px}
main{width:100%;max-width:640px}h1{font-size:28px;margin:0 0 6px}p{color:var(--soft);margin:0 0 24px;line-height:1.5}
#drop{border:2px dashed var(--line);border-radius:22px;padding:48px 20px;text-align:center;cursor:pointer;background:var(--card);transition:.15s}
#drop.on{border-color:var(--amber);background:#2a2618}#drop b{display:block;font-size:18px;margin-bottom:6px}
ul{list-style:none;padding:0;margin:24px 0 0}li{background:var(--card);border-radius:14px;padding:12px 14px;margin-bottom:8px}
.bar{height:4px;border-radius:2px;background:var(--line);margin-top:8px;overflow:hidden}.bar i{display:block;height:100%;width:0;background:var(--amber)}
.ok{color:var(--amber)}.err{color:#ff7a6b}small{color:var(--soft)}
</style></head><body><main>
<h1>Fwip</h1><p>Arraste HQs (CBZ, CBR, CB7, PDF) para cá, ou clique para escolher. Drop comics here, or click to pick them.</p>
<label id="drop"><input id="pick" type="file" multiple accept=".cbz,.cbr,.cb7,.pdf,.zip,.rar,.7z" hidden><b>Soltar arquivos aqui</b><small>Drop files here</small></label>
<ul id="list"></ul></main>
<script>
const k=new URLSearchParams(location.search).get('k');const drop=document.getElementById('drop'),list=document.getElementById('list');
let queue=[],busy=false;
function add(files){for(const f of files){const li=document.createElement('li');li.innerHTML='<div></div><small></small><div class="bar"><i></i></div>';li.firstChild.textContent=f.name;list.prepend(li);queue.push([f,li]);}next();}
function next(){if(busy||!queue.length)return;busy=true;const [f,li]=queue.shift();const x=new XMLHttpRequest();
x.open('POST','/upload?k='+encodeURIComponent(k)+'&name='+encodeURIComponent(f.name));
x.upload.onprogress=e=>{if(e.lengthComputable)li.querySelector('i').style.width=(100*e.loaded/e.total)+'%'};
x.onload=()=>{const s=li.querySelector('small');if(x.status==200){s.textContent='Enviada · Sent';s.className='ok';li.querySelector('i').style.width='100%'}else{s.textContent=x.responseText||('Erro '+x.status);s.className='err'}busy=false;next()};
x.onerror=()=>{const s=li.querySelector('small');s.textContent='Falhou · Failed';s.className='err';busy=false;next()};x.send(f);}
drop.addEventListener('dragover',e=>{e.preventDefault();drop.classList.add('on')});drop.addEventListener('dragleave',()=>drop.classList.remove('on'));
drop.addEventListener('drop',e=>{e.preventDefault();drop.classList.remove('on');add(e.dataTransfer.files)});
document.getElementById('pick').addEventListener('change',e=>add(e.target.files));
</script></body></html>"""
}
