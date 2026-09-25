package com.ubad.academy.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ubad.academy.R

/**
 * Leaving the app on purpose: YouTube (D4), Google Forms / Summaries / external pages
 * (Custom Tabs), and sharing private files through FileProvider.
 */
object External {

    /** Custom Tab themed with the app colours; falls back to any browser. Only http(s) URLs. */
    fun openInTab(context: Context, url: String, toolbar: Color? = null) {
        val safe = Web.safeHttpUrl(url) ?: return failed(context)
        val builder = CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_ON)
        if (toolbar != null) builder.setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbar.toArgb()).build())
        try {
            builder.build().launchUrl(context, Uri.parse(safe))
        } catch (e: ActivityNotFoundException) {
            openInBrowser(context, safe)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        val safe = Web.safeHttpUrl(url) ?: return failed(context)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)).addCategory(Intent.CATEGORY_BROWSABLE).newTask(context))
        } catch (e: ActivityNotFoundException) { failed(context) }
    }

    /** YouTube app if installed, otherwise a Custom Tab (never an embedded player). */
    fun openYoutube(context: Context, url: String, toolbar: Color? = null) {
        val id = Web.youtubeVideoId(url) ?: return openInTab(context, url, toolbar)
        val watch = Uri.parse(Web.youtubeWatchUrl(id))
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, watch).setPackage(YOUTUBE_PKG).newTask(context))
        } catch (e: ActivityNotFoundException) {
            openInTab(context, watch.toString(), toolbar)
        }
    }

    fun shareFile(context: Context, uri: Uri, mime: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType(mime.ifBlank { "application/octet-stream" })
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(send, title).newTask(context))
        } catch (e: ActivityNotFoundException) { failed(context) }
    }

    fun shareText(context: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        try { context.startActivity(Intent.createChooser(send, title).newTask(context)) } catch (e: ActivityNotFoundException) { failed(context) }
    }

    /** Opens a private file in another app (e.g. a PDF editor) with temporary read access. */
    fun viewFile(context: Context, uri: Uri, mime: String) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { context.startActivity(view.newTask(context)) } catch (e: ActivityNotFoundException) { failed(context) }
    }

    private fun Intent.newTask(context: Context) = apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    private fun failed(context: Context) {
        Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show()
    }

    private const val YOUTUBE_PKG = "com.google.android.youtube"
}
