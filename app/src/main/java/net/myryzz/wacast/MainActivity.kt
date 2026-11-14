package net.myryzz.wacast

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import net.myryzz.wacast.databinding.ActivityMainBinding

/**
 * Single-activity host. The five web-app tabs (Template, Compose, Preview, Contacts, Send)
 * become fragments swapped into this shell.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
    }
}
