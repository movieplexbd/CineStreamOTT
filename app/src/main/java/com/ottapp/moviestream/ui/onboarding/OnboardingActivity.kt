package com.ottapp.moviestream.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.ottapp.moviestream.LoginActivity
import com.ottapp.moviestream.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardAdapter

    private val slides = listOf(
        OnboardSlide(
            icon     = "🎬",
            title    = "CineStream-এ স্বাগতম!",
            subtitle = "বাংলা ডাবিং মুভির সেরা অ্যাপ। হলিউড, ইন্ডিয়ান ও কোরিয়ান মুভি এখন বাংলায়।"
        ),
        OnboardSlide(
            icon     = "🆓",
            title    = "২ ঘণ্টা ফ্রি ট্রায়াল",
            subtitle = "প্রথমবার লগইন করলে আপনি পাবেন ২ ঘণ্টার বিনামূল্যে ট্রায়াল। কোনো কার্ড লাগবে না!"
        ),
        OnboardSlide(
            icon     = "🔍",
            title    = "মুভি খুঁজুন ও দেখুন",
            subtitle = "হোম পেজ থেকে ট্রেন্ডিং মুভি দেখুন। যেকোনো মুভিতে ক্লিক করুন → বিবরণ পড়ুন → ▶ দেখুন বোতাম চাপুন।"
        ),
        OnboardSlide(
            icon     = "⬇️",
            title    = "ডাউনলোড করুন অফলাইনে",
            subtitle = "ইন্টারনেট ছাড়াও দেখতে পারবেন। মুভি পেজে ডাউনলোড বোতাম চাপুন → ডাউনলোড ট্যাবে দেখুন।"
        ),
        OnboardSlide(
            icon     = "🔥",
            title    = "মাত্র ৳১০-তে সাবস্ক্রাইব করুন",
            subtitle = "ট্রায়াল শেষে মাত্র ১০ টাকায় ১ মাসের পূর্ণ অ্যাক্সেস নিন। বিকাশ/নগদে পেমেন্ট করুন। প্রোফাইল → সাবস্ক্রিপশন নিন।"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = OnboardAdapter(slides)
        binding.viewPager.adapter = adapter

        setupDots(0)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                setupDots(position)
                if (position == slides.lastIndex) {
                    binding.btnNext.text = "শুরু করুন 🎬"
                    binding.btnSkip.visibility = View.GONE
                } else {
                    binding.btnNext.text = "পরবর্তী →"
                    binding.btnSkip.visibility = View.VISIBLE
                }
            }
        })

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < slides.lastIndex) {
                binding.viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun setupDots(selectedIndex: Int) {
        binding.layoutDots.removeAllViews()
        for (i in slides.indices) {
            val dot = TextView(this).apply {
                text = if (i == selectedIndex) "●" else "○"
                textSize = 14f
                setTextColor(
                    if (i == selectedIndex) 0xFFE8431C.toInt() else 0xFF555555.toInt()
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(6, 0, 6, 0)
                layoutParams = lp
            }
            binding.layoutDots.addView(dot)
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences("onboarding", Context.MODE_PRIVATE)
            .edit().putBoolean("shown", true).apply()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    companion object {
        fun isOnboardingShown(context: Context): Boolean {
            return context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
                .getBoolean("shown", false)
        }
    }
}
