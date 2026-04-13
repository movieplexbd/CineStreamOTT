package com.ottapp.moviestream.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ottapp.moviestream.data.model.Actor
import com.ottapp.moviestream.databinding.ItemActorCircleBinding
import com.ottapp.moviestream.util.loadImage

class MovieActorAdapter(
    private val onActorClick: (Actor) -> Unit
) : RecyclerView.Adapter<MovieActorAdapter.ActorViewHolder>() {

    private var actors = listOf<Actor>()

    fun submitList(list: List<Actor>) {
        actors = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActorViewHolder {
        val binding = ItemActorCircleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ActorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActorViewHolder, position: Int) {
        holder.bind(actors[position])
    }

    override fun getItemCount(): Int = actors.size

    inner class ActorViewHolder(private val binding: ItemActorCircleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(actor: Actor) {
            binding.tvName.text = actor.name
            binding.ivActor.loadImage(actor.imageUrl)
            binding.root.setOnClickListener { onActorClick(actor) }
        }
    }
}
