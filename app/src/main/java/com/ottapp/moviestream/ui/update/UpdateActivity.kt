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

        binding.tvUpdateTitle.text = title
        binding.tvUpdateSubtitle.text = message
        binding.tvVersionInfo.text = "Current Version: $currentVersion | Latest Version: $latestVersion"

        if (changelog.isNotEmpty()) {
            binding.cvChangelog.visibility = View.VISIBLE
            changelog.forEach { item ->
                val textView = TextView(this).apply {
                    text = "✔ $item"
                    setTextColor(resources.getColor(R.color.black, null))
                    textSize = 14f
                    setPadding(0, 4, 0, 4)
                }
                binding.llChangelogContainer.addView(textView)
            }
        } else {
            binding.cvChangelog.visibility = View.GONE
        }

        if (updateType == "FORCE") {
            binding.btnLater.visibility = View.GONE
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Do nothing, back button disabled for force update
                }
            })
        } else {
            binding.btnLater.visibility = View.VISIBLE
            binding.btnLater.setOnClickListener {
                finish()
            }
        }

        binding.btnUpdateNow.setOnClickListener {
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
