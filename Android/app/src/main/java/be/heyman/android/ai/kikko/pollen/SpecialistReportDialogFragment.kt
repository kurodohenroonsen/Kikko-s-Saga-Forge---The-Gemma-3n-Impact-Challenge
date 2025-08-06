package be.heyman.android.ai.kikko.pollen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import be.heyman.android.ai.kikko.R
import com.google.android.material.switchmaterial.SwitchMaterial

class SpecialistReportDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.dialog_specialist_report, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleTextView: TextView = view.findViewById(R.id.dialog_title)
        val reportTextView: TextView = view.findViewById(R.id.report_content_textview)
        val formatSwitch: SwitchMaterial = view.findViewById(R.id.format_switch)

        val specialistName = requireArguments().getString(ARG_SPECIALIST_NAME)
        val textReport = requireArguments().getString(ARG_TEXT_REPORT)
        val jsonReport = requireArguments().getString(ARG_JSON_REPORT)

        titleTextView.text = "Rapport de l'Abeille : $specialistName"

        // Initial display
        reportTextView.text = textReport
        formatSwitch.text = "Voir en JSON"

        formatSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                reportTextView.text = jsonReport
                formatSwitch.text = "Voir en Texte"
            } else {
                reportTextView.text = textReport
                formatSwitch.text = "Voir en JSON"
            }
        }
    }

    companion object {
        private const val ARG_SPECIALIST_NAME = "specialist_name"
        private const val ARG_TEXT_REPORT = "text_report"
        private const val ARG_JSON_REPORT = "json_report"

        fun newInstance(specialistName: String, textReport: String, jsonReport: String): SpecialistReportDialogFragment {
            val args = Bundle().apply {
                putString(ARG_SPECIALIST_NAME, specialistName)
                putString(ARG_TEXT_REPORT, textReport)
                putString(ARG_JSON_REPORT, jsonReport)
            }
            val fragment = SpecialistReportDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}