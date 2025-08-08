package be.heyman.android.ai.kikko.prompt

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import be.heyman.android.ai.kikko.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class PromptEditorActivity : AppCompatActivity() {

    private val TAG = "PromptEditorActivity"

    private lateinit var toolbar: MaterialToolbar
    private lateinit var promptSpinner: Spinner
    private lateinit var promptEditText: EditText
    private lateinit var saveButton: Button

    // BOURDON'S REFACTOR: La map locale contient maintenant les prompts complets (simples chaînes).
    private var currentPrompts = mutableMapOf<String, String>()
    private var promptKeys = listOf<String>()
    private var lastSelectedSpinnerPosition = -1

    private val importPromptsLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val success = PromptManager.importPrompts(this@PromptEditorActivity, uri)
                if (success) {
                    Toast.makeText(this@PromptEditorActivity, R.string.toast_import_success, Toast.LENGTH_SHORT).show()
                    loadInitialPrompts()
                } else {
                    Toast.makeText(this@PromptEditorActivity, R.string.toast_import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prompt_editor)

        bindViews()
        setupToolbar()
        setupListeners()
        loadInitialPrompts()
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.prompt_editor_toolbar)
        promptSpinner = findViewById(R.id.prompt_selector_spinner)
        promptEditText = findViewById(R.id.prompt_editor_edittext)
        saveButton = findViewById(R.id.prompt_editor_save_button)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        saveButton.setOnClickListener { saveChanges() }
    }

    private fun loadInitialPrompts() {
        // BOURDON'S REFACTOR: On charge la map simple de prompts.
        currentPrompts = PromptManager.getAllPrompts().toMutableMap()
        promptKeys = currentPrompts.keys.sorted()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, promptKeys)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        promptSpinner.adapter = adapter

        promptSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Sauvegarde les changements du prompt précédent avant de charger le nouveau.
                if (lastSelectedSpinnerPosition != -1 && lastSelectedSpinnerPosition < promptKeys.size) {
                    val previousKey = promptKeys[lastSelectedSpinnerPosition]
                    currentPrompts[previousKey] = promptEditText.text.toString()
                }

                val selectedKey = promptKeys[position]
                promptEditText.setText(currentPrompts[selectedKey])
                lastSelectedSpinnerPosition = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (promptKeys.isNotEmpty()) {
            promptEditText.setText(currentPrompts[promptKeys.first()])
            lastSelectedSpinnerPosition = 0
        }
    }

    private fun saveChanges() {
        // Mettre à jour la valeur de l'éditeur dans la map avant de sauvegarder.
        val selectedKey = promptSpinner.selectedItem as? String
        if (selectedKey != null) {
            currentPrompts[selectedKey] = promptEditText.text.toString()
        }

        lifecycleScope.launch {
            PromptManager.savePrompts(this@PromptEditorActivity, currentPrompts)
            Toast.makeText(this@PromptEditorActivity, R.string.toast_prompts_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import_prompts -> {
                importPromptsLauncher.launch("application/json")
                true
            }
            R.id.action_export_prompts -> {
                exportPrompts()
                true
            }
            R.id.action_reset_prompts -> {
                showResetConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportPrompts() {
        lifecycleScope.launch {
            val uri = PromptManager.exportPrompts(this@PromptEditorActivity)
            if (uri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "application/json"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_prompts_title)))
            } else {
                Toast.makeText(this@PromptEditorActivity, R.string.toast_export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showResetConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_reset_prompts_title)
            .setMessage(R.string.dialog_reset_prompts_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_reset_confirm) { _, _ ->
                lifecycleScope.launch {
                    PromptManager.restoreDefaults(this@PromptEditorActivity)
                    loadInitialPrompts()
                    Toast.makeText(this@PromptEditorActivity, R.string.toast_prompts_restored, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}