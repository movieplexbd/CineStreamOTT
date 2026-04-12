package com.ottapp.moviestream.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import java.util.concurrent.TimeUnit

fun View.show()      { visibility = View.VISIBLE }
fun View.hide()      { visibility = View.GONE    }
fun View.invisible() { visibility = View.INVISIBLE }

fun Context.toast(msg: String) {
    try {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e("Extensions", "Toast error: ${e.message}")
    }
}

fun ImageView.loadImage(url: String, placeholder: Int? = null) {
    try {
        val ctx = context ?: return
        if (ctx is Activity && (ctx.isFinishing || ctx.isDestroyed)) return

        var builder = Glide.with(ctx)
            .load(url.ifBlank { null })
            .transition(DrawableTransitionOptions.withCrossFade(200))
        if (placeholder != null) {
            builder = builder.apply(RequestOptions().placeholder(placeholder).error(placeholder))
        }
        builder.into(this)
    } catch (e: Exception) {
        Log.e("Extensions", "loadImage error: ${e.message}")
    }
}

fun ImageView.loadImageSafe(url: String, placeholder: Int? = null) {
    try {
        val ctx = context ?: return
        if (ctx is Activity && (ctx.isFinishing || ctx.isDestroyed)) return

        var builder = Glide.with(ctx)
            .load(url.ifBlank { null })
            .transition(DrawableTransitionOptions.withCrossFade(200))
        if (placeholder != null) {
            builder = builder.apply(RequestOptions().placeholder(placeholder).error(placeholder))
        }
        builder.into(this)
    } catch (e: Exception) {
        Log.e("Extensions", "loadImageSafe error: ${e.message}")
    }
}

fun Long.toReadableSize(): String {
    if (this <= 0) return "0 B"
    val kb = this / 1024.0
    val mb = kb  / 1024.0
    val gb = mb  / 1024.0
    return when {
        gb >= 1 -> "%.1f GB".format(gb)
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.1f KB".format(kb)
        else    -> "$this B"
    }
}

fun Long.toFormattedTime(): String {
    try {
        val h = TimeUnit.MILLISECONDS.toHours(this)
        val m = TimeUnit.MILLISECONDS.toMinutes(this) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(this) % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
               else       "%02d:%02d".format(m, s)
    } catch (e: Exception) {
        return "00:00"
    }
}

fun Long.toReadableDate(): String {
    return try {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        sdf.format(java.util.Date(this))
    } catch (e: Exception) {
        ""
    }
}
