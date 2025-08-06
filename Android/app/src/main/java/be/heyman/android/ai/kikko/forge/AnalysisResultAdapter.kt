package be.heyman.android.ai.kikko.forge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.AnalysisResult
import be.heyman.android.ai.kikko.model.AnalysisStatus
import be.heyman.android.ai.kikko.model.ModelConfiguration
import com.google.android.material.chip.Chip
import com.google.gson.Gson

// BOURDON'S FIX: La classe DiffCallback est maintenant définie AVANT l'adaptateur qui l'utilise.
class AnalysisResultDiffCallback : DiffUtil.ItemCallback<AnalysisResult>() {
    override fun areItemsTheSame(oldItem: AnalysisResult, newItem: AnalysisResult): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AnalysisResult, newItem: AnalysisResult): Boolean {
        return oldItem.status == newItem.status &&
                oldItem.rawResponse == newItem.rawResponse &&
                oldItem.streamingResponse == newItem.streamingResponse &&
                oldItem.errorMessage == newItem.errorMessage
    }
}

class AnalysisResultAdapter(
    private val onRun: (AnalysisResult) -> Unit,
    private val onCancel: (AnalysisResult) -> Unit,
    private val onRetry: (AnalysisResult) -> Unit,
    private val onViewError: (AnalysisResult) -> Unit,
    private val onValidate: (AnalysisResult) -> Unit
) : ListAdapter<AnalysisResult, AnalysisResultAdapter.ViewHolder>(AnalysisResultDiffCallback()) {

    private val gson = Gson()
    private val expandedItemIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = getItem(position)
        val isExpanded = expandedItemIds.contains(task.id)
        holder.bind(task, isExpanded) {
            if (expandedItemIds.contains(task.id)) {
                expandedItemIds.remove(task.id)
            } else {
                expandedItemIds.add(task.id)
            }
            notifyItemChanged(position)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val analysisModelConfig: TextView = view.findViewById(R.id.analysis_model_config)
        val analysisStatusChip: Chip = view.findViewById(R.id.analysis_status_chip)
        val analysisRawResponse: TextView = view.findViewById(R.id.analysis_raw_response)
        val analysisErrorMessage: TextView = view.findViewById(R.id.analysis_error_message)
        val analysisRunButton: Button = view.findViewById(R.id.analysis_run_button)
        val analysisPauseButton: Button = view.findViewById(R.id.analysis_pause_button)
        val analysisCancelButton: Button = view.findViewById(R.id.analysis_cancel_button)
        val analysisRetryButton: Button = view.findViewById(R.id.analysis_retry_button)
        val analysisViewErrorButton: Button = view.findViewById(R.id.analysis_view_error_button)
        val analysisValidateButton: Button = view.findViewById(R.id.analysis_validate_button)

        val headerContainer: View = view.findViewById(R.id.analysis_header_container)
        val detailsContainer: View = view.findViewById(R.id.analysis_details_container)


        fun bind(task: AnalysisResult, isManuallyExpanded: Boolean, onHeaderClick: () -> Unit) {
            analysisStatusChip.text = task.status.name

            val title = when {
                task.status == AnalysisStatus.COMPLETED -> generateTitleFromResponse(task)
                else -> {
                    val config = gson.fromJson(task.modelConfigJson, ModelConfiguration::class.java)
                    "${config.modelName.replace(".task", "")} (${config.accelerator}, T:${config.temperature})"
                }
            }
            analysisModelConfig.text = title

            if (task.status == AnalysisStatus.RUNNING || isManuallyExpanded) {
                detailsContainer.visibility = View.VISIBLE
            } else {
                detailsContainer.visibility = View.GONE
            }
            headerContainer.setOnClickListener { onHeaderClick() }

            val showResponseText = when (task.status) {
                AnalysisStatus.RUNNING -> task.streamingResponse
                AnalysisStatus.COMPLETED -> task.rawResponse
                else -> null
            }

            if (!showResponseText.isNullOrEmpty()) {
                analysisRawResponse.visibility = View.VISIBLE
                analysisRawResponse.text = showResponseText
            } else {
                analysisRawResponse.visibility = View.GONE
            }

            analysisErrorMessage.visibility = if (task.status == AnalysisStatus.FAILED && task.errorMessage != null) {
                analysisErrorMessage.text = task.errorMessage
                View.VISIBLE
            } else {
                View.GONE
            }

            when (task.status) {
                AnalysisStatus.PENDING, AnalysisStatus.PAUSED, AnalysisStatus.CANCELLED -> showActions(run = true)
                AnalysisStatus.RUNNING -> showActions(pause = true, cancel = true)
                AnalysisStatus.FAILED -> showActions(viewError = true, retry = true)
                AnalysisStatus.COMPLETED -> {
                    showActions(validate = true)
                }
            }

            analysisRunButton.setOnClickListener { onRun(task) }
            analysisCancelButton.setOnClickListener { onCancel(task) }
            analysisRetryButton.setOnClickListener { onRetry(task) }
            analysisViewErrorButton.setOnClickListener { onViewError(task) }
            analysisValidateButton.setOnClickListener { onValidate(task) }
        }

        private fun generateTitleFromResponse(task: AnalysisResult): String {
            val rawResponse = task.rawResponse ?: return "Analyse terminée"
            val config = gson.fromJson(task.modelConfigJson, ModelConfiguration::class.java)
            val modelNickname = config.modelName.take(15)

            return when (task.propertyName) {
                "identification" -> {
                    try {
                        data class IdentificationTitle(val specificName: String?, val deckName: String?)
                        val cleanJson = rawResponse.substringAfter("{").substringBeforeLast("}")
                        val result = gson.fromJson("{$cleanJson}", IdentificationTitle::class.java)
                        if (!result.specificName.isNullOrBlank() && !result.deckName.isNullOrBlank()) {
                            "✅ ${result.deckName}: ${result.specificName}"
                        } else { "⚠️ Résultat partiel" }
                    } catch (e: Exception) {
                        val name = """"specificName"\s*:\s*"(.*?)"""".toRegex().find(rawResponse)?.groups?.get(1)?.value
                        val deck = """"(deckName|DeckName)"\s*:\s*"(.*?)"""".toRegex(RegexOption.IGNORE_CASE).find(rawResponse)?.groups?.get(2)?.value
                        if (name != null && deck != null) "✅ $deck: $name (via Regex)" else "⚠️ Résultat non-structuré"
                    }
                }
                "description" -> "✅ Description générée par $modelNickname"
                else -> "✅ Terminé - $modelNickname"
            }
        }

        private fun showActions(run: Boolean = false, pause: Boolean = false, cancel: Boolean = false, retry: Boolean = false, viewError: Boolean = false, validate: Boolean = false) {
            analysisRunButton.visibility = if (run) View.VISIBLE else View.GONE
            analysisPauseButton.visibility = if (pause) View.VISIBLE else View.GONE
            analysisCancelButton.visibility = if (cancel) View.VISIBLE else View.GONE
            analysisRetryButton.visibility = if (retry) View.VISIBLE else View.GONE
            analysisViewErrorButton.visibility = if (viewError) View.VISIBLE else View.GONE
            analysisValidateButton.visibility = if (validate) View.VISIBLE else View.GONE
        }
    }
}