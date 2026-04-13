package com.ottapp.moviestream.ui.reels

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.Reel
import com.ottapp.moviestream.data.repository.ReelRepository
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch

class ReelsFragment : Fragment() {

    private var viewPager: ViewPager2? = null
    private val reels = mutableListOf<Reel>()
    private val repo = ReelRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_reels, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewPager = view.findViewById(R.id.viewpager_reels)
        loadReels()
    }

    private fun loadReels() {
        lifecycleScope.launch {
            try {
                val all = repo.getAllReels()
                if (isAdded && viewPager != null) {
                    reels.clear()
                    reels.addAll(all)
                    setupPager()
                }
            } catch (e: Exception) {
                requireContext().toast("রিলস লোড করতে সমস্যা হয়েছে")
            }
        }
    }

    private fun setupPager() {
        val adapter = ReelsAdapter(reels) { reel, action ->
            when (action) {
                "search" -> {
                    val bundle = bundleOf("query" to reel.movieTitle)
                    findNavController().navigate(R.id.searchFragment, bundle)
                }
                "download" -> {
                    if (reel.videoUrl.isNotEmpty() && !reel.videoUrl.contains("youtube.com") && !reel.videoUrl.contains("youtu.be")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reel.videoUrl))
                        startActivity(intent)
                    } else {
                        requireContext().toast("এই ভিডিওটি ডাউনলোড করা সম্ভব নয়")
                    }
                }
            }
        }
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

class ReelsAdapter(
    private val items: List<Reel>,
    private val onAction: (Reel, String) -> Unit
) : RecyclerView.Adapter<ReelsAdapter.ReelViewHolder>() {

    inner class ReelViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val webView: WebView = view.findViewById(R.id.webview_reel)
        val videoView: VideoView = view.findViewById(R.id.videoview_reel)
        val progress: ProgressBar = view.findViewById(R.id.progress_reel)
        val tvTitle: TextView = view.findViewById(R.id.tv_reel_title)
        val tvMovieName: TextView = view.findViewById(R.id.tv_movie_name)
        val btnSearch: View = view.findViewById(R.id.btn_search_movie)
        val btnDownload: View = view.findViewById(R.id.btn_download_reel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reel, parent, false)
        return ReelViewHolder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ReelViewHolder, position: Int) {
        val reel = items[position]
        holder.tvTitle.text = reel.title
        holder.tvMovieName.text = if (reel.movieTitle.isNotEmpty()) "মুভি: ${reel.movieTitle}" else ""
        
        holder.btnSearch.setOnClickListener { onAction(reel, "search") }
        holder.btnDownload.setOnClickListener { onAction(reel, "download") }

        val youtubeId = extractYouTubeId(reel.videoUrl)
        if (youtubeId != null) {
            holder.videoView.visibility = View.GONE
            holder.webView.visibility = View.VISIBLE
            setupYouTubePlayer(holder.webView, youtubeId, holder.progress)
        } else {
            holder.webView.visibility = View.GONE
            holder.videoView.visibility = View.VISIBLE
            setupDirectPlayer(holder.videoView, reel.videoUrl, holder.progress)
        }
    }

    private fun setupDirectPlayer(videoView: VideoView, url: String, progress: ProgressBar) {
        if (url.isEmpty()) return
        progress.visibility = View.VISIBLE
        videoView.setVideoPath(url)
        videoView.setOnPreparedListener { mp ->
            progress.visibility = View.GONE
            mp.isLooping = true
            videoView.start()
            
            // Adjust video size to fill screen (TikTok style)
            val videoRatio = mp.videoWidth / mp.videoHeight.toFloat()
            val screenRatio = videoView.width / videoView.height.toFloat()
            val scaleX = videoRatio / screenRatio
            if (scaleX >= 1f) {
                videoView.scaleX = scaleX
            } else {
                videoView.scaleY = 1f / scaleX
            }
        }
        videoView.setOnErrorListener { _, _, _ ->
            progress.visibility = View.GONE
            false
        }
    }

    override fun onViewRecycled(holder: ReelViewHolder) {
        super.onViewRecycled(holder)
        try { 
            holder.webView.loadUrl("about:blank")
            holder.videoView.stopPlayback()
        } catch (e: Exception) { }
    }

    private fun extractYouTubeId(url: String): String? {
        if (url.isBlank()) return null
        listOf(
            Regex("youtu\\.be/([\\w-]+)"),
            Regex("youtube\\.com/watch\\?v=([\\w-]+)"),
            Regex("youtube\\.com/embed/([\\w-]+)"),
            Regex("youtube\\.com/shorts/([\\w-]+)")
        ).forEach { p -> p.find(url)?.groupValues?.getOrNull(1)?.let { return it } }
        return null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupYouTubePlayer(webView: WebView, videoId: String, progress: ProgressBar) {
        progress.visibility = View.VISIBLE
        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) progress.visibility = View.GONE
            }
        }
        webView.webViewClient = WebViewClient()
        val html = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>*{margin:0;padding:0;background:#000;}body{width:100vw;height:100vh;display:flex;align-items:center;justify-content:center;}iframe{width:100%;height:100%;border:none;}</style>
</head><body>
<iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&mute=0&loop=1&playlist=$videoId&controls=0&showinfo=0&rel=0&modestbranding=1" allow="autoplay;fullscreen" allowfullscreen></iframe>
</body></html>""".trimIndent()
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
    }
}
