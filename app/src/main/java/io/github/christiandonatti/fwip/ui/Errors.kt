package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.data.Comic
import io.github.christiandonatti.fwip.str

/** An error put in words a reader can act on, with the technical detail kept apart (small, for reports). */
data class FriendlyError(val message: String, val detail: String?, val canRetry: Boolean)

/**
 * Why a comic or book did not open, said plainly: access to its folder lost, the server not
 * answering, a damaged or unfinished file, or (failing all those) the raw reason.
 */
fun friendlyError(t: Throwable, comic: Comic?): FriendlyError {
    val chain = generateSequence(t) { it.cause }.take(6).toList()
    // Paths inside the app's own storage mean nothing to a reader: keep the words, drop the path.
    val detail = (t.message ?: t.javaClass.simpleName)
        .replace(Regex("""\s*(from|in|at)?\s*(/[^\s/]+){3,}/?"""), "")
        .trim().trimEnd(':').ifBlank { t.javaClass.simpleName }
        .take(300)
    val net = comic?.remote != null || comic?.uri?.scheme in setOf("smb", "webdav", "dav", "davs", "http", "https")
    return when {
        chain.any { it is SecurityException } ->
            FriendlyError(str(R.string.err_no_access), detail, canRetry = false)
        chain.any { it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.SocketTimeoutException || it is java.net.NoRouteToHostException } ->
            FriendlyError(str(R.string.err_offline), detail, canRetry = true)
        chain.any { it is java.io.FileNotFoundException } && !net ->
            FriendlyError(str(R.string.err_missing), detail, canRetry = true)
        chain.any { it is java.util.zip.ZipException || it is java.io.EOFException || it.javaClass.name.contains("Rar", ignoreCase = true) || it.javaClass.name.contains("SevenZip", ignoreCase = true) } ->
            FriendlyError(str(R.string.err_damaged), detail, canRetry = false)
        chain.any { it is OutOfMemoryError } ->
            FriendlyError(str(R.string.err_memory), detail, canRetry = true)
        net && chain.any { it is java.io.IOException } ->
            FriendlyError(str(R.string.err_offline), detail, canRetry = true)
        else -> FriendlyError(str(R.string.error_open_file), detail, canRetry = true)
    }
}
