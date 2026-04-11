package com.ottapp.moviestream.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.DownloadedMovie
import com.ottapp.moviestream.databinding.ItemDownloadBinding
import com.ottapp.moviestream.util.loadImage
import com.ottapp.moviestream.util.toReadableSize

class DownloadAdapter(
    private val onPlay:   (DownloadedMovie) -> Unit,
    private val onDelete: (DownloadedMovie) -> Unit
) : ListAdapter<DownloadedMovie, DownloadAdapter.VH>(Diff()) {

    inner class VH(private val b: ItemDownloadBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: DownloadedMovie) {
            b.ivThumb.loadImage(item.bannerImageUrl, R.color.surface2)
            b.tvTitle.text    = item.title
            b.tvSize.text     = item.fileSize.toReadableSize()
            b.tvCategory.text = item.category
            b.tvRating.text   = "★ ${item.imdbRating}"
            b.btnPlay.setOnClickListener   { onPlay(item) }
            b.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    class Diff : DiffUtil.ItemCallback<DownloadedMovie>() {
        override fun areItemsTheSame(a: DownloadedMovie, b: DownloadedMovie) = a.movieId == b.movieId
        override fun areContentsTheSame(a: DownloadedMovie, b: DownloadedMovie) = a == b
    }
}
