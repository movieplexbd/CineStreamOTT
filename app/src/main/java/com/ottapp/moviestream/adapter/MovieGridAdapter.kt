package com.ottapp.moviestream.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.databinding.ItemMovieCardBinding
import com.ottapp.moviestream.util.loadImage

class MovieGridAdapter(
    private val onClick: (Movie) -> Unit
) : ListAdapter<Movie, MovieGridAdapter.VH>(Diff()) {

    inner class VH(private val b: ItemMovieCardBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(movie: Movie) {
            b.ivThumb.loadImage(movie.bannerImageUrl.orEmpty(), R.color.surface2)
            b.tvTitle.text = movie.title.orEmpty()
            b.tvRating.text = "★ ${movie.imdbRating}"
            b.tvCategory.text = movie.category.orEmpty()

            b.ivLock.visibility = if (movie.testMovie) android.view.View.GONE else android.view.View.VISIBLE

            b.root.setOnClickListener { onClick(movie) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMovieCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    class Diff : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(a: Movie, b: Movie) = a.id == b.id
        override fun areContentsTheSame(a: Movie, b: Movie) = a == b
    }
}
