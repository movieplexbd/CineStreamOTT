package com.ottapp.moviestream.ui.update

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.ottapp.moviestream.R
import com.ottapp.moviestream.databinding.ActivityUpdateBinding

class UpdateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra("title") ?: "New Update Available"
        val message = intent.getStringExtra("message") ?: "Better performance, bug fixes, new features"
        val changelog = intent.getStringArrayListExtra("changelog") ?: arrayListOf()
        val downloadLink = intent.getStringExtra("downloadLink") ?: ""
        val updateType = intent.getStringExtra("updateType") ?: "SOFT"
        val currentVersion = intent.getStringExtra("currentVersion") ?: ""
        val latestVersion = intent.getStringExtra("latestVersion") ?: ""

        binding.tv_update_title.text = title
        binding.tv_update_subtitle.text = message
        binding.tv_version_info.text = "Current Version: $currentVersion | Latest Version: $latestVersion"

        if (changelog.isNotEmpty()) {
            binding.cv_changelog.visibility = View.VISIBLE
            changelog.forEach { item ->
                val textView = TextView(this).apply {
                    text = "✔ $item"
                    setTextColor(resources.getColor(R.color.black, null))
                    textSize = 14f
                    setPadding(0, 4, 0, 4)
                }
                binding.ll_changelog_container.addView(textView)
            }
        } else {
            binding.cv_changelog.visibility = View.GONE
        }

        if (updateType == "FORCE") {
            binding.btn_later.visibility = View.GONE
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Do nothing, back button disabled for force update
                }
            })
        } else {
            binding.btn_later.visibility = View.VISIBLE
            binding.btn_later.setOnClickListener {
                finish()
            }
        }

        binding.btn_update_now.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadLink))
                startActivity(intent)
                if (updateType == "FORCE") {
                    finish()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
