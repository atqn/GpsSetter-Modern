package com.android1500.gpssetter.ui


import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.android1500.gpssetter.R
import com.android1500.gpssetter.databinding.SettingsActivityBinding
import com.android1500.gpssetter.gsApp
import com.android1500.gpssetter.utils.PrefManager
import rikka.preference.SimpleMenuPreference


class SettingsActivity : AppCompatActivity() {

    private val binding by lazy {
        SettingsActivityBinding.inflate(layoutInflater)
    }

    class SettingPreferenceDataStore() : PreferenceDataStore() {
        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return when (key) {
                "isHookedSystem" -> PrefManager.isHookSystem
                "disable_update" -> PrefManager.disableUpdate
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }

        override fun putBoolean(key: String?, value: Boolean) {
            return when (key) {
                "isHookedSystem" -> PrefManager.isHookSystem = value
                "disable_update" -> PrefManager.disableUpdate = value
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }

        override fun getString(key: String?, defValue: String?): String? {
            return when (key) {
                "accuracy_settings" -> PrefManager.accuracy
                "movement_mode" -> PrefManager.movementMode
                "movement_radius" -> PrefManager.movementRadius
                "movement_speed" -> PrefManager.movementSpeed
                "map_type" -> PrefManager.mapType.toString()
                "darkTheme" -> PrefManager.darkTheme.toString()
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }

        override fun putString(key: String?, value: String?) {
            return when (key) {
                "accuracy_settings" -> PrefManager.accuracy = value
                "movement_mode" -> PrefManager.movementMode = value ?: PrefManager.MODE_SMALL
                "movement_radius" -> PrefManager.movementRadius = value
                "movement_speed" -> PrefManager.movementSpeed = value
                "map_type" -> PrefManager.mapType = value!!.toInt()
                "darkTheme" -> PrefManager.darkTheme = value!!.toInt()
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsPreferenceFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }


    class SettingsPreferenceFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager?.preferenceDataStore = SettingPreferenceDataStore()
            setPreferencesFromResource(R.xml.setting, rootKey)

            findPreference<EditTextPreference>("accuracy_settings")?.let {
                it.summary = "${PrefManager.accuracy} m."
                it.setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_NUMBER;
                    editText.keyListener = DigitsKeyListener.getInstance("0123456789.,");
                    editText.addTextChangedListener(getCommaReplacerTextWatcher(editText));
                }

                it.setOnPreferenceChangeListener { preference, newValue ->
                    try {
                        newValue as String?
                        preference.summary = "$newValue  m."
                    } catch (n: NumberFormatException) {
                        n.printStackTrace()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.enter_valid_input),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
            }

            findPreference<EditTextPreference>("movement_radius")?.let {
                it.summary = "${PrefManager.movementRadius} m"
                it.setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_NUMBER
                    editText.keyListener = DigitsKeyListener.getInstance("0123456789")
                }
                it.setOnPreferenceChangeListener { preference, newValue ->
                    val v = (newValue as? String)?.toFloatOrNull()
                    if (v == null || v <= 0f || v > 1000f) {
                        Toast.makeText(requireContext(), getString(R.string.enter_valid_input), Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        preference.summary = "$newValue m"
                        true
                    }
                }
            }

            findPreference<EditTextPreference>("movement_speed")?.let {
                it.summary = "${PrefManager.movementSpeed} m/s"
                it.setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    editText.keyListener = DigitsKeyListener.getInstance("0123456789.,")
                    editText.addTextChangedListener(getCommaReplacerTextWatcher(editText))
                }
                it.setOnPreferenceChangeListener { preference, newValue ->
                    val v = (newValue as? String)?.toFloatOrNull()
                    if (v == null || v <= 0f || v > 50f) {
                        Toast.makeText(requireContext(), getString(R.string.enter_valid_input), Toast.LENGTH_SHORT).show()
                        false
                    } else {
                        preference.summary = "$newValue m/s"
                        true
                    }
                }
            }

            findPreference<SimpleMenuPreference>("darkTheme")?.setOnPreferenceChangeListener { _, newValue ->
                val newMode = (newValue as String).toInt()
                if (PrefManager.darkTheme != newMode) {
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    activity?.recreate()
                }
                true
            }

            // Movement mode dropdown — show advanced fields only when Custom.
            findPreference<SimpleMenuPreference>("movement_mode")?.let { mode ->
                applyMovementModeVisibility(mode.value ?: PrefManager.movementMode)
                mode.setOnPreferenceChangeListener { _, newValue ->
                    applyMovementModeVisibility(newValue as String)
                    true
                }
            }
        }

        private fun applyMovementModeVisibility(mode: String) {
            val isCustom = mode == PrefManager.MODE_CUSTOM
            findPreference<EditTextPreference>("movement_radius")?.isVisible = isCustom
            findPreference<EditTextPreference>("movement_speed")?.isVisible = isCustom
            findPreference<EditTextPreference>("accuracy_settings")?.isVisible = isCustom
        }

        private fun getCommaReplacerTextWatcher(editText: EditText): TextWatcher {
            return object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun onTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun afterTextChanged(editable: Editable) {
                    val text = editable.toString()
                    if (text.contains(",")) {
                        editText.setText(text.replace(",", "."))
                        editText.setSelection(editText.text.length)
                    }
                }
            }
        }

    }


}