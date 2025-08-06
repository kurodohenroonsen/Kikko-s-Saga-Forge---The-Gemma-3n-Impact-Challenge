package be.heyman.android.ai.kikko.royal_audience

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import be.heyman.android.ai.kikko.R
import com.google.android.material.slider.Slider
import kotlinx.parcelize.Parcelize
import java.util.Locale

@Parcelize
data class AudienceSettings(val temperature: Float, val topK: Int) : Parcelable

class AudienceSettingsDialogFragment : DialogFragment() {

    interface AudienceSettingsListener {
        fun onSettingsConfirmed(settings: AudienceSettings)
    }

    private var listener: AudienceSettingsListener? = null
    private lateinit var currentSettings: AudienceSettings

    private lateinit var temperatureLabel: TextView
    private lateinit var temperatureSlider: Slider
    private lateinit var topKLabel: TextView
    private lateinit var topKSlider: Slider
    private lateinit var confirmButton: Button

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // L'activité parente doit implémenter cette interface pour recevoir les résultats.
        listener = context as? AudienceSettingsListener
            ?: throw ClassCastException("$context must implement AudienceSettingsListener")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION")
            currentSettings = it.getParcelable(ARG_SETTINGS) ?: AudienceSettings(0.2f, 40)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_audience_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupViews()
        setupListeners()
    }

    private fun bindViews(view: View) {
        temperatureLabel = view.findViewById(R.id.audience_settings_temperature_label)
        temperatureSlider = view.findViewById(R.id.audience_settings_temperature_slider)
        topKLabel = view.findViewById(R.id.audience_settings_topk_label)
        topKSlider = view.findViewById(R.id.audience_settings_topk_slider)
        confirmButton = view.findViewById(R.id.audience_settings_confirm_button)
    }

    private fun setupViews() {
        dialog?.setTitle("Décrets de la Reine")

        temperatureSlider.value = currentSettings.temperature
        topKSlider.value = currentSettings.topK.toFloat()

        updateTemperatureLabel(currentSettings.temperature)
        updateTopKLabel(currentSettings.topK.toFloat())
    }

    private fun setupListeners() {
        temperatureSlider.addOnChangeListener { _, value, _ ->
            updateTemperatureLabel(value)
        }
        topKSlider.addOnChangeListener { _, value, _ ->
            updateTopKLabel(value)
        }
        confirmButton.setOnClickListener {
            val newSettings = AudienceSettings(
                temperature = temperatureSlider.value,
                topK = topKSlider.value.toInt()
            )
            listener?.onSettingsConfirmed(newSettings)
            dismiss()
        }
    }

    private fun updateTemperatureLabel(value: Float) {
        temperatureLabel.text = String.format(Locale.US, "Tempérament (Créativité : %.2f)", value)
    }

    private fun updateTopKLabel(value: Float) {
        topKLabel.text = "Focalisation (Top-K : ${value.toInt()})"
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        const val TAG = "AudienceSettingsDialog"
        private const val ARG_SETTINGS = "current_settings"

        fun newInstance(currentSettings: AudienceSettings): AudienceSettingsDialogFragment {
            val args = Bundle().apply {
                putParcelable(ARG_SETTINGS, currentSettings)
            }
            return AudienceSettingsDialogFragment().apply {
                arguments = args
            }
        }
    }
}