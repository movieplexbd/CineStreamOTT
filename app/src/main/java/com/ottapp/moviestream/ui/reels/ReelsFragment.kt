package com.ottapp.moviestream.ui.reels

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.MovieRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReelsFragment : Fragment() {

    private var viewPager: ViewPager2? = null
    private val movies = mutableListOf<Movie>()
    private val repo = MovieRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_reels, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewPager = view.findViewById(R.id.viewpager_reels)
        loadMovies()
    }

    private fun loadMovies() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val all = repo.getAllMovies()
                withContext(Dispatchers.Main) {
                    if (isAdded && viewPager != null) {
                        movies.clear()
                        movies.addAll(all)
                        setupPager()
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun setupPager() {
        val adapter = ReelsAdapter(movies)
        viewPager?.apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            this.adapter = adapter
            offscreenPageLimit = 1
        }
    }

    override fun onDestroyView() {
        viewPager?.adapter = null
        viewPager = null
        super.onDestroyView()
    }
}

class ReelsAdapter(private val items: List<Movie>) :
    RecyclerView.Adapter<ReelsAdapter.ReelViewHolder>() {

    inner class ReelViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val webView: WebView = view.findViewById(R.id.webview_reel)
        val ivBg: ImageView = view.findViewById(R.id.iv_reel_bg)
        val tvTitle: TextView = view.findViewById(R.id.tv_reel_title)
        val tvCategory: TextView = view.findViewById(R.id.tv_reel_category)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reel, parent, false)
        return ReelViewHolder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ReelViewHolder, position: Int) {
        val movie = items[position]
        holder.tvTitle.text = movie.title
        holder.tvCategory.text = movie.category

        val youtubeId = extractYouTubeId(movie.videoStreamUrl)
        if (youtubeId != null) {
            holder.webView.visibility = View.VISIBLE
            holder.ivBg.visibility = View.GONE
            setupYouTubePlayer(holder.webView, youtubeId)
        } else {
            holder.webView.visibility = View.GONE
            holder.ivBg.visibility = View.VISIBLE
            if (movie.bannerImageUrl.isNotEmpty()) {
                Glide.with(holder.ivBg.context).load(movie.bannerImageUrl).centerCrop().into(holder.ivBg)
            }
        }
    }

    override fun onViewRecycled(holder: ReelViewHolder) {
        super.onViewRecycled(holder)
        try { holder.webView.loadUrl("about:blank") } catch (e: Exception) { }
    }

    private fun extractYouTubeId(url: String): String? {
        if (url.isBlank()) return null
        listOf(
            Regex("youtu\\.be/([\\w-]+)"),
            Regex("youtube\\.com/watch\\?v=([\\w-]+)"),
            Regex("youtube\\.com/embed/([\\w-]+)")
        ).forEach { p -> p.find(url)?.groupValues?.getOrNull(1)?.let { return it } }
        return null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupYouTubePlayer(webView: WebView, videoId: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        val html = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>*{margin:0;padding:0;background:#000;}body{width:100vw;height:100vh;display:flex;align-items:center;justify-content:center;}iframe{width:100%;height:100%;border:none;}</style>
</head><body>
<iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&mute=1&loop=1&playlist=$videoId&controls=0&showinfo=0&rel=0&modestbranding=1" allow="autoplay;fullscreen" allowfullscreen></iframe>
</body></html>""".trimIndent()
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
    }
}
