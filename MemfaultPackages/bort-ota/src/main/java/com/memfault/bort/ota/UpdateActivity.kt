package com.memfault.bort.ota

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.memfault.bort.ota.lib.Event
import com.memfault.bort.ota.lib.State
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class UpdateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_container)
        setTitle(R.string.app_name)

        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.ic_baseline_arrow_back_24)
            setDisplayHomeAsUpEnabled(true)
        }

        val model: UpdateViewModel by viewModels { UpdateViewModelFactory(application.components) }
        lifecycleScope.launchWhenStarted {
            model.state.collect {
                handle(it)
            }
        }
        model.events
            .onEach { handleEvent(it) }
            .launchIn(lifecycleScope)
    }

    private fun handleEvent(event: Event) {
        when (event) {
            is Event.DownloadFailed ->
                Snackbar.make(
                    findViewById(R.id.coordinator),
                    R.string.download_failed,
                    Snackbar.LENGTH_SHORT
                ).show()
            is Event.VerificationFailed ->
                Snackbar.make(
                    findViewById(R.id.coordinator),
                    R.string.verification_failed,
                    Snackbar.LENGTH_LONG
                ).show()
            is Event.NoUpdatesAvailable ->
                Snackbar.make(
                    findViewById(R.id.coordinator),
                    R.string.latest_version_already_installed,
                    Snackbar.LENGTH_SHORT
                ).show()
        }
    }

    private fun handle(state: State) {
        when (state) {
            is State.Idle ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, MainPreferenceFragment())
                    .commit()
            is State.CheckingForUpdates ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, CheckingForUpdatesFragment())
                    .commit()
            is State.UpdateAvailable ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, UpdateAvailableFragment())
                    .commit()
            is State.UpdateDownloading -> {
                getByTagOrReplace("checking_for_updates") { CheckingForUpdatesFragment() }
                    .setProgress(state.progress)
            }
            is State.ReadyToInstall ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, UpdateReadyFragment())
                    .commit()
            else -> throw IllegalStateException("$state not yet implemented")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> finish().let { true }
        else -> super.onOptionsItemSelected(item)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Fragment> getByTagOrReplace(tag: String, fragmentFactory: () -> T): T {
        val fragment: T? = supportFragmentManager.findFragmentByTag(tag) as T?
        return if (fragment == null) {
            val newFragment = fragmentFactory()
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, newFragment, tag)
                .commitNow()
            newFragment
        } else fragment
    }
}

class MainPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference_screen, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean =
        when (preference?.key) {
            "check_for_updates" -> true.also { checkForUpdatedClicked() }
            else -> super.onPreferenceTreeClick(preference)
        }

    private fun checkForUpdatedClicked() {
        ViewModelProvider(requireActivity())
            .get(UpdateViewModel::class.java)
            .checkForUpdates()
    }
}

class CheckingForUpdatesFragment : Fragment() {
    lateinit var progressBar: ProgressBar

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.checking_for_updates_layout, container, false)
        progressBar = layout.findViewById(R.id.progress_bar)
        return layout
    }

    fun setProgress(progress: Int) {
        if (this::progressBar.isInitialized) {
            progressBar.isIndeterminate = progress == -1
            progressBar.min = 0
            progressBar.max = 100
            progressBar.progress = progress
        }
    }
}

class UpdateAvailableFragment : Fragment() {
    private val updateViewModel by activityViewModels<UpdateViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val state = updateViewModel.state.value as State.UpdateAvailable

        val layout = inflater.inflate(R.layout.update_available_layout, container, false)
        layout.findViewById<TextView>(R.id.release_notes).text = state.ota.releaseNotes
        layout.findViewById<TextView>(R.id.version).text = getString(R.string.software_version, state.ota.version)
        layout.findViewById<Button>(R.id.download_update).setOnClickListener {
            updateViewModel.downloadUpdate()
        }
        layout.findViewById<Button>(R.id.maybe_later).setOnClickListener {
            activity?.finish()
        }
        return layout
    }
}

class UpdateReadyFragment : Fragment() {
    private val updateViewModel by activityViewModels<UpdateViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val state = updateViewModel.state.value as State.ReadyToInstall

        val layout = inflater.inflate(R.layout.update_available_layout, container, false)
        layout.findViewById<TextView>(R.id.release_notes).text = state.ota.releaseNotes
        layout.findViewById<TextView>(R.id.version).text = getString(R.string.software_version, state.ota.version)
        layout.findViewById<Button>(R.id.download_update).apply {
            setText(R.string.install_update)
            setOnClickListener { updateViewModel.installUpdate() }
        }
        layout.findViewById<Button>(R.id.maybe_later).setOnClickListener {
            activity?.finish()
        }
        return layout
    }
}
