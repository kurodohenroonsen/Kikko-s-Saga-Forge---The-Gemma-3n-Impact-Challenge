package be.heyman.android.ai.kikko

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.data.ModelCatalogue
import be.heyman.android.ai.kikko.prompt.PromptEditorActivity
import be.heyman.android.ai.kikko.ui.adapters.LocalModelAdapter
import be.heyman.android.ai.kikko.worker.DownloadManagerKikko
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File

class ToolsDialogFragment : DialogFragment() {

    interface ToolsDialogListener {
        fun onExportSagaRequested()
        fun onImportSagaRequested()
        fun onAddModelRequested()
        fun onDeleteModelRequested(modelFile: File)
        fun onNukeDatabaseRequested()
        // BOURDON'S ADDITION: Nouvelle méthode pour lancer l'éditeur de prompts.
        fun onManagePromptsRequested()
    }

    private var listener: ToolsDialogListener? = null
    private lateinit var queenManagementAdapter: LocalModelAdapter
    private lateinit var queenSelectorAdapter: QueenModelAdapter
    private val TAG = "ToolsDialog"

    private lateinit var prefs: SharedPreferences
    private lateinit var requireChargingSwitch: SwitchMaterial
    private lateinit var requireIdleSwitch: SwitchMaterial
    private lateinit var nukeDbButton: Button
    private lateinit var queenAcceleratorRadioGroup: RadioGroup
    private lateinit var downloadModelsButton: Button
    private lateinit var downloadDecksButton: Button
    // BOURDON'S ADDITION: Référence pour le nouveau bouton.
    private lateinit var managePromptsButton: Button


    companion object {
        const val TAG = "ToolsDialog"
        const val PREFS_NAME = "ForgeSettings"
        const val KEY_REQUIRE_CHARGING = "KEY_FORGE_WHILE_CHARGING"
        const val KEY_REQUIRE_IDLE = "KEY_FORGE_WHEN_IDLE"
        const val KEY_SELECTED_FORGE_QUEEN = "KEY_SELECTED_FORGE_QUEEN"
        const val KEY_SELECTED_FORGE_QUEEN_ACCELERATOR = "KEY_SELECTED_FORGE_QUEEN_ACCELERATOR"


        fun newInstance(): ToolsDialogFragment {
            return ToolsDialogFragment()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? ToolsDialogListener
            ?: throw ClassCastException("$context must implement ToolsDialogListener")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle(R.string.tools_dialog_title)

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        bindViews(view)
        setupForgeQueenSelector(view)
        setupForgeQueenAccelerator()
        setupQueenModelManagement(view)
        setupSagaButtons(view)
        setupForgeSettings()
        setupNukeButton()
        // BOURDON'S ADDITION: Appel à la nouvelle méthode de configuration.
        setupPromptManagementButton()
    }

    private fun bindViews(view: View) {
        requireChargingSwitch = view.findViewById(R.id.tools_switch_require_charging)
        requireIdleSwitch = view.findViewById(R.id.tools_switch_require_idle)
        nukeDbButton = view.findViewById(R.id.tools_button_nuke_db)
        queenAcceleratorRadioGroup = view.findViewById(R.id.tools_radiogroup_queen_accelerator)
        downloadModelsButton = view.findViewById(R.id.tools_button_download_models)
        downloadDecksButton = view.findViewById(R.id.tools_button_download_decks)
        // BOURDON'S ADDITION: Liaison du nouveau bouton.
        managePromptsButton = view.findViewById(R.id.tools_button_manage_prompts)
    }

    private fun setupForgeQueenSelector(view: View) {
        val queenSelectorRecyclerView: RecyclerView = view.findViewById(R.id.tools_recyclerview_queen_selector)
        val selectedQueenName = prefs.getString(KEY_SELECTED_FORGE_QUEEN, null)

        queenSelectorAdapter = QueenModelAdapter(
            mutableListOf(),
            selectedQueenName
        ) { selectedFile ->
            prefs.edit().putString(KEY_SELECTED_FORGE_QUEEN, selectedFile.name).apply()
            Toast.makeText(context, getString(R.string.queen_selected_toast, selectedFile.name), Toast.LENGTH_SHORT).show()
            loadForgeQueenModels()
        }

        queenSelectorRecyclerView.layoutManager = LinearLayoutManager(context)
        queenSelectorRecyclerView.adapter = queenSelectorAdapter
        loadForgeQueenModels()
    }

    private fun setupForgeQueenAccelerator() {
        val savedAccelerator = prefs.getString(KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")
        if (savedAccelerator == "CPU") {
            queenAcceleratorRadioGroup.check(R.id.tools_radio_cpu)
        } else {
            queenAcceleratorRadioGroup.check(R.id.tools_radio_gpu)
        }

        queenAcceleratorRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedAccelerator = if (checkedId == R.id.tools_radio_cpu) "CPU" else "GPU"
            prefs.edit().putString(KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, selectedAccelerator).apply()
            Toast.makeText(context, getString(R.string.accelerator_set_toast, selectedAccelerator), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQueenModelManagement(view: View) {
        val queenRecyclerView: RecyclerView = view.findViewById(R.id.tools_recyclerview_models)
        val addModelButton: Button = view.findViewById(R.id.tools_button_add_model)

        queenManagementAdapter = LocalModelAdapter(mutableListOf()) { modelFile ->
            listener?.onDeleteModelRequested(modelFile)
            loadQueenManagementModels()
        }
        queenRecyclerView.layoutManager = LinearLayoutManager(context)
        queenRecyclerView.adapter = queenManagementAdapter
        loadQueenManagementModels()
        addModelButton.setOnClickListener { listener?.onAddModelRequested() }

        downloadModelsButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.kikko.be/model"))
            startActivity(intent)
            dismiss()
        }
    }

    private fun setupSagaButtons(view: View) {
        val exportSagaButton: Button = view.findViewById(R.id.tools_button_export_saga)
        val importSagaButton: Button = view.findViewById(R.id.tools_button_import_saga)
        importSagaButton.setOnClickListener { listener?.onImportSagaRequested(); dismiss() }
        exportSagaButton.setOnClickListener { listener?.onExportSagaRequested(); dismiss() }

        // Ajout du listener pour le nouveau bouton de téléchargement de decks
        downloadDecksButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.kikko.be/sagas"))
            startActivity(intent)
            dismiss()
        }
    }

    // BOURDON'S ADDITION: Nouvelle méthode pour configurer le bouton de gestion des prompts.
    private fun setupPromptManagementButton() {
        managePromptsButton.setOnClickListener {
            listener?.onManagePromptsRequested()
            dismiss()
        }
    }

    private fun setupForgeSettings() {
        requireChargingSwitch.isChecked = prefs.getBoolean(KEY_REQUIRE_CHARGING, false)
        requireIdleSwitch.isChecked = prefs.getBoolean(KEY_REQUIRE_IDLE, false)

        requireChargingSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_REQUIRE_CHARGING, isChecked).apply()
            val status = if(isChecked) getString(R.string.generic_enabled) else getString(R.string.generic_disabled)
            Toast.makeText(context, getString(R.string.forge_charging_toast, status), Toast.LENGTH_SHORT).show()
        }
        requireIdleSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_REQUIRE_IDLE, isChecked).apply()
            val status = if(isChecked) getString(R.string.generic_enabled) else getString(R.string.generic_disabled)
            Toast.makeText(context, getString(R.string.forge_idle_toast, status), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNukeButton() {
        nukeDbButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_confirmation_required_title)
                .setMessage(R.string.dialog_nuke_db_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm_nuke) { _, _ ->
                    listener?.onNukeDatabaseRequested()
                    dismiss()
                }
                .show()
        }
    }

    private fun loadForgeQueenModels() {
        val modelsDir = File(requireContext().filesDir, "imported_models")
        val modelFiles = if (modelsDir.exists() && modelsDir.isDirectory) {
            modelsDir.listFiles { _, name -> name.endsWith(".task") }?.toList()?.sortedBy { it.name } ?: emptyList()
        } else {
            emptyList()
        }
        val selectedQueenName = prefs.getString(KEY_SELECTED_FORGE_QUEEN, null)
        queenSelectorAdapter.updateSelection(modelFiles, selectedQueenName)
    }

    private fun loadQueenManagementModels() {
        val modelsDir = File(requireContext().filesDir, "imported_models")
        if (modelsDir.exists() && modelsDir.isDirectory) {
            val modelFiles = modelsDir.listFiles { _, name -> name.endsWith(".task") }?.toList() ?: emptyList()
            queenManagementAdapter.updateModels(modelFiles.sortedBy { it.name })
        } else {
            queenManagementAdapter.updateModels(emptyList())
        }
        loadForgeQueenModels()
    }
}