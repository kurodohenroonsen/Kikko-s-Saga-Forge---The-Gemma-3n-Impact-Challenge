package be.heyman.android.ai.kikko.clash.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.clash.data.ClashSettings
import be.heyman.android.ai.kikko.data.Model
import com.google.android.material.slider.Slider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

class ClashSetupDialogFragment : DialogFragment() {

    interface ClashSetupListener {
        fun onClashSettingsConfirmed(settings: ClashSettings)
    }

    private var listener: ClashSetupListener? = null

    private lateinit var modelSpinner: Spinner
    private lateinit var acceleratorRadioGroup: RadioGroup
    private lateinit var temperatureLabel: TextView
    private lateinit var temperatureSlider: Slider
    private lateinit var ttsSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var dontShowAgainCheckbox: CheckBox
    private lateinit var confirmButton: Button

    private var availableModels: List<Model> = emptyList()
    private var currentSettings: ClashSettings? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? ClashSetupListener ?: context as? ClashSetupListener
        if (listener == null) {
            throw ClassCastException("$context must implement ClashSetupListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val modelsJson = it.getString(ARG_MODELS)
            val settingsJson = it.getString(ARG_SETTINGS)
            val gson = Gson()
            val modelListType = object : TypeToken<List<Model>>() {}.type
            availableModels = gson.fromJson(modelsJson, modelListType)
            currentSettings = gson.fromJson(settingsJson, ClashSettings::class.java)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_clash_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle("Préparation au Clash")

        bindViews(view)
        setupViews()
        setupListeners()
    }

    private fun bindViews(view: View) {
        modelSpinner = view.findViewById(R.id.clash_setup_model_spinner)
        acceleratorRadioGroup = view.findViewById(R.id.clash_setup_accelerator_radiogroup)
        temperatureLabel = view.findViewById(R.id.clash_setup_temperature_label)
        temperatureSlider = view.findViewById(R.id.clash_setup_temperature_slider)
        ttsSwitch = view.findViewById(R.id.clash_setup_tts_switch)
        dontShowAgainCheckbox = view.findViewById(R.id.clash_setup_dont_show_again_checkbox)
        confirmButton = view.findViewById(R.id.clash_setup_confirm_button)
    }

    private fun setupViews() {
        // Model Spinner
        val modelNames = availableModels.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, modelNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter

        // Set initial values from currentSettings
        currentSettings?.let {
            val modelIndex = availableModels.indexOfFirst { model -> model.name == it.queenModelName }
            if (modelIndex != -1) modelSpinner.setSelection(modelIndex)

            if (it.brain == "CPU") {
                acceleratorRadioGroup.check(R.id.clash_setup_cpu_radio)
            } else {
                acceleratorRadioGroup.check(R.id.clash_setup_gpu_radio)
            }

            temperatureSlider.value = it.temperature
            temperatureLabel.text = "Tempérament du Juge (${String.format(Locale.US, "%.2f", it.temperature)})"

            ttsSwitch.isChecked = it.isTtsEnabled
            dontShowAgainCheckbox.isChecked = !it.showSetupOnLaunch
        }
    }

    private fun setupListeners() {
        temperatureSlider.addOnChangeListener { _, value, _ ->
            temperatureLabel.text = "Tempérament du Juge (${String.format(Locale.US, "%.2f", value)})"
        }

        confirmButton.setOnClickListener {
            val selectedModel = availableModels[modelSpinner.selectedItemPosition]
            val selectedAcceleratorId = acceleratorRadioGroup.checkedRadioButtonId
            val selectedAccelerator = if (selectedAcceleratorId == R.id.clash_setup_cpu_radio) "CPU" else "GPU"
            val selectedTemperature = temperatureSlider.value
            val isTtsEnabled = ttsSwitch.isChecked
            val shouldShowAgain = !dontShowAgainCheckbox.isChecked

            val newSettings = ClashSettings(
                queenModelName = selectedModel.name,
                brain = selectedAccelerator,
                temperature = selectedTemperature,
                isTtsEnabled = isTtsEnabled,
                showSetupOnLaunch = shouldShowAgain
            )

            listener?.onClashSettingsConfirmed(newSettings)
            dismiss()
        }
    }

    companion object {
        const val TAG = "ClashSetupDialog"
        private const val ARG_MODELS = "available_models"
        private const val ARG_SETTINGS = "current_settings"

        fun newInstance(availableModels: List<Model>, currentSettings: ClashSettings?): ClashSetupDialogFragment {
            val fragment = ClashSetupDialogFragment()
            val args = Bundle()
            val gson = Gson()
            args.putString(ARG_MODELS, gson.toJson(availableModels))
            args.putString(ARG_SETTINGS, gson.toJson(currentSettings))
            fragment.arguments = args
            return fragment
        }
    }
}