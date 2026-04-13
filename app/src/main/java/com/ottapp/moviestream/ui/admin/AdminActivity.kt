package com.ottapp.moviestream.ui.admin

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ottapp.moviestream.R
import com.ottapp.moviestream.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding

    private val fragments = mutableMapOf<Int, Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityAdminBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "মাস্টার কন্ট্রোল"

            if (savedInstanceState == null) {
                showFragment(R.id.adminMoviesFragment)
            }

            binding.adminBottomNavigation.setOnItemSelectedListener { item ->
                showFragment(item.itemId)
                true
            }
        } catch (e: Exception) {
            Log.e("AdminActivity", "Crash in onCreate: ${e.message}", e)
            finish()
        }
    }

    private fun showFragment(itemId: Int) {
        val tag = itemId.toString()
        val existing = supportFragmentManager.findFragmentByTag(tag)

        val fragment: Fragment = when {
            existing != null -> {
                updateTitle(itemId)
                supportFragmentManager.beginTransaction()
                    .show(existing)
                    .also { tx ->
                        fragments.values.forEach { f ->
                            if (f.tag != tag && !f.isHidden) tx.hide(f)
                        }
                    }
                    .commit()
                return
            }
            itemId == R.id.adminMoviesFragment   -> AdminMoviesFragment()
            itemId == R.id.adminBannersFragment  -> AdminBannersFragment()
            itemId == R.id.adminReelsFragment    -> AdminReelsFragment()
            itemId == R.id.adminUsersFragment    -> AdminUsersFragment()
            itemId == R.id.adminSubsFragment     -> AdminSubsFragment()
            itemId == R.id.adminActorsFragment   -> AdminActorsFragment()
            else -> AdminMoviesFragment()
        }

        fragments[itemId] = fragment
        updateTitle(itemId)

        val tx = supportFragmentManager.beginTransaction()
        fragments.values.forEach { f -> if (!f.isHidden && f !== fragment) tx.hide(f) }
        tx.add(R.id.admin_fragment_container, fragment, tag)
        tx.commit()
    }

    private fun updateTitle(itemId: Int) {
        supportActionBar?.title = when (itemId) {
            R.id.adminMoviesFragment  -> "মুভি ম্যানেজমেন্ট"
            R.id.adminBannersFragment -> "ব্যানার ম্যানেজমেন্ট"
            R.id.adminReelsFragment   -> "রিলস ম্যানেজমেন্ট"
            R.id.adminUsersFragment   -> "ইউজার ম্যানেজমেন্ট"
            R.id.adminSubsFragment    -> "পেমেন্ট রিকোয়েস্ট"
            R.id.adminActorsFragment  -> "অভিনেতা ম্যানেজমেন্ট"
            else -> "মাস্টার কন্ট্রোল"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
