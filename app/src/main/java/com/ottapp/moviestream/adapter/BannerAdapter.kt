package com.ottapp.moviestream.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.databinding.ItemBannerBinding
import com.ottapp.moviestream.util.loadImage

class BannerAdapter(
    private val onClick: (Movie) -> Unit
) : ListAdapter<Movie, BannerAdapter.VH>(Diff()) {

    inner class VH(private val b: ItemBannerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(movie: Movie) {
            b.ivBanner.loadImage(movie.bannerImageUrl.orEmpty())
            b.tvBannerTitle.text = movie.title.orEmpty()
            b.tvBannerCategory.text = movie.category.orEmpty()
            b.tvBannerRating.text = "⭐ ${movie.imdbRating}"
            b.btnBannerPlay.setOnClickListener { onClick(movie) }
            b.root.setOnClickListener { onClick(movie) }
            if (movie.testMovie) b.tvFreeBadge.visibility = android.view.View.VISIBLE
            else b.tvFreeBadge.visibility = android.view.View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    class Diff : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(a: Movie, b: Movie) = a.id == b.id
        override fun areContentsTheSame(a: Movie, b: Movie) = a == b
    }
}
