package be.heyman.android.ai.kikko.forge

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.AnalysisStatus
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class JudgmentDialogFragment : DialogFragment() {

    private val viewModel: ForgeWorkshopViewModel by activityViewModels()

    private lateinit var streamingResponseTextView: TextView
    private lateinit var promptContentTextView: TextView
    private lateinit var evidenceContentTextView: TextView
    private lateinit var confirmButton: Button
    private lateinit var overrideButton: Button
    private lateinit var judgmentWarningCard: MaterialCardView
    private lateinit var judgmentWarningText: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_Kikko_FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_judgment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle(R.string.judgment_dialog_title)

        bindViews(view)
        setupListeners()
        observeViewModel()
    }

    private fun bindViews(view: View) {
        streamingResponseTextView = view.findViewById(R.id.judgment_streaming_response)
        promptContentTextView = view.findViewById(R.id.judgment_prompt_content)
        evidenceContentTextView = view.findViewById(R.id.judgment_evidence_content)
        confirmButton = view.findViewById(R.id.judgment_button_confirm)
        overrideButton = view.findViewById(R.id.judgment_button_override)
        judgmentWarningCard = view.findViewById(R.id.judgment_warning_card)
        judgmentWarningText = view.findViewById(R.id.judgment_warning_text)
    }

    private fun setupListeners() {
        confirmButton.setOnClickListener {
            viewModel.confirmJudgment()
        }
        overrideButton.setOnClickListener {
            viewModel.dismissJudgment()
        }
    }

    // BOURDON'S CRITICAL FIX: S'assurer que le ViewModel est notifié lorsque le dialogue est fermé par l'utilisateur.
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.dismissJudgment()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (val judgment = state.judgmentState) {
                    is JudgmentState.InProgress -> {
                        promptContentTextView.text = judgment.prompt
                        streamingResponseTextView.text = judgment.streamingResponse
                        evidenceContentTextView.text = "L'Arbitre délibère..."
                        confirmButton.isEnabled = false
                        overrideButton.text = getString(R.string.dialog_cancel)
                        checkCompetitionStatus(state, judgment.propertyName)
                    }
                    is JudgmentState.Complete -> {
                        // BOURDON'S DEFINITIVE FIX: La logique est adaptée à la nouvelle structure de données.
                        val verdict = judgment.verdict
                        val rankedListText = verdict.rankedProposals.joinToString("\n\n") { proposal ->
                            "Proposition (Votes: ${proposal.voteCount}):\n'${proposal.value}'\nSynthèse du Raisonnement: ${proposal.reasoningSummary}"
                        }
                        val fullText = "Verdict de l'Arbitre:\n${verdict.arbiterReasoning}\n\n--- Propositions Classées ---\n$rankedListText"
                        streamingResponseTextView.text = fullText
                        confirmButton.isEnabled = true
                        overrideButton.text = getString(R.string.judgment_button_override)
                        checkCompetitionStatus(state, judgment.propertyName)
                    }
                    is JudgmentState.Failed -> {
                        streamingResponseTextView.text = "Erreur du Jugement : ${judgment.error}"
                        confirmButton.isEnabled = false
                        overrideButton.text = getString(R.string.dialog_cancel)
                        judgmentWarningCard.isVisible = false
                    }
                    JudgmentState.None -> {
                        // L'activité gère la fermeture, et onDismiss s'assure de la propreté de l'état.
                    }
                }
            }
        }
    }

    private fun checkCompetitionStatus(state: ForgeWorkshopUiState, propertyName: String) {
        val allTasks = state.analysisResults[propertyName] ?: emptyList()
        if (allTasks.isEmpty()) {
            judgmentWarningCard.isVisible = false
            return
        }

        val totalTasks = allTasks.size
        val finishedTasks = allTasks.count { it.status in listOf(AnalysisStatus.COMPLETED, AnalysisStatus.FAILED, AnalysisStatus.CANCELLED) }

        if (finishedTasks < totalTasks) {
            judgmentWarningText.text = getString(R.string.judgment_warning_in_progress, finishedTasks, totalTasks)
            judgmentWarningCard.isVisible = true
        } else {
            judgmentWarningCard.isVisible = false
        }
    }

    companion object {
        const val TAG = "JudgmentDialogFragment"
        fun newInstance(): JudgmentDialogFragment {
            return JudgmentDialogFragment()
        }
    }
}