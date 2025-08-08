// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/forge/ForgeWorkshopActivity.kt ---

package be.heyman.android.ai.kikko.forge

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.GameConstants
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.AnalysisStatus
import be.heyman.android.ai.kikko.model.PollenStatus
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

class ForgeWorkshopActivity : AppCompatActivity() {

    private val viewModel: ForgeWorkshopViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    private val TAG = "ForgeWorkshopActivity"

    // Vues statiques
    private lateinit var statusMessage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var pollenGrainsRecyclerView: RecyclerView
    private lateinit var workshopContent: View
    private lateinit var grainIdLabel: TextView
    private lateinit var workshopSelectedImage: ImageView
    private lateinit var workshopSelectedName: TextView
    private lateinit var workshopSelectedDeck: TextView
    private lateinit var propertiesContainer: LinearLayout
    private lateinit var workshopDeleteGrainButton: Button
    private lateinit var filterRawButton: LinearLayout
    private lateinit var filterFoodButton: LinearLayout
    private lateinit var filterPlantButton: LinearLayout
    private lateinit var filterInsectButton: LinearLayout
    private lateinit var filterBirdButton: LinearLayout
    private lateinit var filterButtons: Map<String, View>

    // Adaptateurs et état de l'UI
    private lateinit var grainAdapter: PollenGrainAdapter
    private val propertyAdapters = mutableMapOf<String, AnalysisResultAdapter>()
    private val expandedPropertySections = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forge_workshop)
        Log.d(TAG, "[CYCLE DE VIE] onCreate: Initialisation de l'Atelier.")

        bindViews()
        setupStaticAdapters()
        setupStaticListeners()
        observeUiState()
    }

    private fun bindViews() {
        Log.d(TAG, "Liaison des vues de l'Atelier...")
        statusMessage = findViewById(R.id.workshop_status_message)
        progressBar = findViewById(R.id.workshop_progress_bar)
        pollenGrainsRecyclerView = findViewById(R.id.workshop_grains_recyclerview)
        workshopContent = findViewById(R.id.workshop_detail_container)
        grainIdLabel = findViewById(R.id.grain_id_label)
        workshopSelectedImage = findViewById(R.id.workshop_selected_image)
        workshopSelectedName = findViewById(R.id.workshop_selected_name)
        workshopSelectedDeck = findViewById(R.id.workshop_selected_deck)
        propertiesContainer = findViewById(R.id.workshop_properties_container)
        workshopDeleteGrainButton = findViewById(R.id.workshop_delete_grain_button)
        filterRawButton = findViewById(R.id.filter_button_raw)
        filterFoodButton = findViewById(R.id.filter_button_food)
        filterPlantButton = findViewById(R.id.filter_button_plant)
        filterInsectButton = findViewById(R.id.filter_button_insect)
        filterBirdButton = findViewById(R.id.filter_button_bird)
        filterButtons = mapOf(
            ForgeWorkshopViewModel.FILTER_RAW to filterRawButton,
            GameConstants.MASTER_DECK_LIST[0] to filterFoodButton,
            GameConstants.MASTER_DECK_LIST[1] to filterPlantButton,
            GameConstants.MASTER_DECK_LIST[2] to filterInsectButton,
            GameConstants.MASTER_DECK_LIST[3] to filterBirdButton
        )
        Log.d(TAG, "Toutes les vues ont été liées avec succès.")
    }

    private fun setupStaticAdapters() {
        grainAdapter = PollenGrainAdapter(emptyList(), null) { grain ->
            viewModel.selectGrain(grain)
        }
        pollenGrainsRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        pollenGrainsRecyclerView.adapter = grainAdapter
    }

    private fun setupStaticListeners() {
        Log.d(TAG, "Configuration des listeners statiques.")
        workshopDeleteGrainButton.setOnClickListener {
            Log.d(TAG, "Bouton de suppression du grain cliqué.")
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.workshop_delete_grain_dialog_title)
                .setMessage(R.string.workshop_delete_grain_dialog_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.workshop_button_delete) { _, _ ->
                    Log.i(TAG, "Confirmation de la suppression. Appel au ViewModel.")
                    viewModel.deleteSelectedGrain()
                }
                .show()
        }
        filterButtons.forEach { (filterType, button) ->
            button.setOnClickListener {
                Log.d(TAG, "Bouton de filtre '$filterType' cliqué.")
                viewModel.setFilter(filterType)
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                Log.v(TAG, "[UI UPDATE] Nouvel état UI. Filtre: ${state.activeFilter}, Jugement: ${state.judgmentState::class.simpleName}")
                progressBar.isVisible = state.isLoading
                statusMessage.text = state.statusMessage

                grainAdapter.updateGrainsAndSelection(state.workshopGrains, state.selectedGrain?.id)

                if (state.selectedGrain != null) {
                    workshopContent.isVisible = true
                    grainIdLabel.text = getString(R.string.workshop_grain_id_format, state.selectedGrain.id.substring(0, 8))

                    if (state.selectedCard != null) {
                        workshopSelectedName.text = state.selectedCard.specificName
                        workshopSelectedDeck.text = getString(R.string.workshop_deck_format, state.selectedCard.deckName)
                        state.selectedCard.imagePath?.let { path ->
                            File(path).takeIf { it.exists() }?.let { workshopSelectedImage.setImageBitmap(BitmapFactory.decodeFile(it.absolutePath)) }
                                ?: workshopSelectedImage.setImageResource(R.drawable.ic_placeholder_card)
                        } ?: workshopSelectedImage.setImageResource(R.drawable.ic_placeholder_card)
                    } else {
                        workshopSelectedName.text = getString(R.string.workshop_raw_pollen_title)
                        workshopSelectedDeck.text = getString(R.string.workshop_deck_unknown)
                        state.selectedGrain.pollenImagePaths.firstOrNull()?.let { path ->
                            File(path).takeIf { it.exists() }?.let { workshopSelectedImage.setImageBitmap(BitmapFactory.decodeFile(it.absolutePath)) }
                                ?: workshopSelectedImage.setImageResource(R.drawable.ic_placeholder_card)
                        } ?: workshopSelectedImage.setImageResource(R.drawable.ic_placeholder_card)
                    }

                    populatePropertiesContainer(state)

                } else {
                    workshopContent.isVisible = false
                }

                state.analysisResults.forEach { (propertyName, results) ->
                    propertyAdapters[propertyName]?.submitList(results)
                }

                updateFilterButtonsVisualState(state.activeFilter)
                handleJudgmentState(state.judgmentState)
            }
        }
    }

    private fun handleJudgmentState(judgmentState: JudgmentState) {
        val existingDialog = supportFragmentManager.findFragmentByTag(JudgmentDialogFragment.TAG) as? JudgmentDialogFragment
        if (judgmentState is JudgmentState.None) {
            existingDialog?.dismiss()
            return
        }
        if (existingDialog == null) {
            JudgmentDialogFragment.newInstance().show(supportFragmentManager, JudgmentDialogFragment.TAG)
        }
    }

    private fun updateFilterButtonsVisualState(activeFilter: String) {
        val activeColor = ContextCompat.getColor(this, R.color.kikko_gold_light)
        val inactiveColor = ContextCompat.getColor(this, android.R.color.transparent)
        filterButtons.forEach { (filterType, buttonView) ->
            if (filterType == activeFilter) {
                buttonView.setBackgroundColor(activeColor)
                buttonView.alpha = 1.0f
            } else {
                buttonView.setBackgroundColor(inactiveColor)
                buttonView.alpha = 0.7f
            }
        }
    }

    private fun populatePropertiesContainer(state: ForgeWorkshopUiState) {
        propertiesContainer.removeAllViews()
        propertyAdapters.clear()
        val grain = state.selectedGrain ?: return
        val card = state.selectedCard
        val isAwaitingValidation = grain.status == PollenStatus.AWAITING_VALIDATION || grain.status == PollenStatus.IDENTIFYING || grain.status == PollenStatus.RAW

        addPropertySection("identification", state, isAwaitingValidation)

        if(card != null && card.deckName != "Unknown") {
            val properties = viewModel.getPropertiesForDeck(card.deckName)
            properties.forEach { propertyName ->
                addPropertySection(propertyName, state, isAwaitingValidation)
            }
        }
    }

    private fun addPropertySection(propertyName: String, state: ForgeWorkshopUiState, isAwaitingValidation: Boolean) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_property_refinement, propertiesContainer, false)

        val headerView = view.findViewById<ConstraintLayout>(R.id.property_refinement_header)
        val titleView = view.findViewById<TextView>(R.id.property_refinement_title)
        val resultCountView = view.findViewById<TextView>(R.id.property_refinement_result_count)
        val expandIcon = view.findViewById<ImageView>(R.id.property_refinement_expand_icon)
        val contentContainer = view.findViewById<LinearLayout>(R.id.property_refinement_content)
        val launchButton = view.findViewById<Button>(R.id.property_refinement_launch_button)
        val judgmentButton = view.findViewById<Button>(R.id.property_refinement_launch_judgment_button)
        val recyclerView = view.findViewById<RecyclerView>(R.id.property_refinement_results_recyclerview)
        val summaryCard = view.findViewById<MaterialCardView>(R.id.property_refinement_summary_card)
        val summaryContainer = view.findViewById<LinearLayout>(R.id.property_refinement_summary_container)

        titleView.text = getTitleForProperty(propertyName)
        val isExpanded = expandedPropertySections.contains(propertyName)
        contentContainer.isVisible = isExpanded
        expandIcon.rotation = if (isExpanded) 180f else 0f
        val allResults = state.analysisResults[propertyName] ?: emptyList()
        val completedResultsCount = allResults.count { it.status == AnalysisStatus.COMPLETED }
        resultCountView.text = if (completedResultsCount > 0) getString(R.string.workshop_results_count, completedResultsCount) else ""
        resultCountView.isVisible = completedResultsCount > 0

        headerView.setOnClickListener {
            if (expandedPropertySections.contains(propertyName)) {
                expandedPropertySections.remove(propertyName)
                contentContainer.isVisible = false
                expandIcon.animate().rotation(0f).start()
            } else {
                expandedPropertySections.add(propertyName)
                contentContainer.isVisible = true
                expandIcon.animate().rotation(180f).start()
            }
        }

        var isLaunchButtonEnabled = true
        var disabledReasonResId: Int? = null
        if (isAwaitingValidation && propertyName != "identification") {
            isLaunchButtonEnabled = false; disabledReasonResId = R.string.workshop_validate_identification_first
        } else if (propertyName == "allergens" && state.selectedCard?.ingredients == null) {
            isLaunchButtonEnabled = false; disabledReasonResId = R.string.workshop_forge_ingredients_first
        }

        val isCompetitionRunning = allResults.any { it.status == AnalysisStatus.PENDING || it.status == AnalysisStatus.RUNNING }
        val hasCompletedResults = allResults.any { it.status == AnalysisStatus.COMPLETED }

        if (isCompetitionRunning) {
            launchButton.setText(R.string.workshop_competition_in_progress)
            isLaunchButtonEnabled = false
        } else if (allResults.isEmpty()) {
            launchButton.setText(R.string.workshop_launch_competition)
            launchButton.setOnClickListener { viewModel.createAnalysisTournament(propertyName) }
        } else {
            launchButton.setText(R.string.workshop_relaunch_competition)
            launchButton.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_relaunch_title).setMessage(R.string.dialog_relaunch_message)
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .setPositiveButton(R.string.dialog_relaunch_confirm) { _, _ -> viewModel.relaunchAnalysisTournament(propertyName) }
                    .show()
            }
        }

        if (disabledReasonResId != null) launchButton.text = getString(disabledReasonResId)
        launchButton.isEnabled = isLaunchButtonEnabled

        judgmentButton.isVisible = hasCompletedResults && isLaunchButtonEnabled
        judgmentButton.setOnClickListener {
            viewModel.launchFinalJudgment(propertyName)
        }

        val summary = state.competitionSummaries[propertyName]
        if (summary != null && summary.items.isNotEmpty()) {
            summaryCard.isVisible = true
            summaryContainer.removeAllViews()
            summary.items.forEach { summaryItem ->
                val summaryItemView = LayoutInflater.from(this).inflate(R.layout.item_competition_summary, summaryContainer, false)
                val summaryTextView = summaryItemView.findViewById<TextView>(R.id.summary_response_text)
                val summaryValidateButton = summaryItemView.findViewById<Button>(R.id.summary_validate_button)
                summaryTextView.text = getString(R.string.workshop_summary_proposal_format, summaryItem.voteCount, summaryItem.response)
                summaryValidateButton.setOnClickListener { viewModel.validateFromSummary(summaryItem) }
                summaryContainer.addView(summaryItemView)
            }
        } else {
            summaryCard.isVisible = false
        }

        // BOURDON'S FIX (Erreurs #1 & #2): Les lambdas appellent maintenant les méthodes
        // `runAnalysisTask` et `cancelAnalysisTask` qui existent dans le ViewModel corrigé.
        val adapter = AnalysisResultAdapter(
            onRun = { viewModel.runAnalysisTask(it) },
            onCancel = { viewModel.cancelAnalysisTask(it) },
            onRetry = { viewModel.retryAnalysisTask(it) },
            onViewError = { task -> showErrorDialog(task.errorMessage ?: getString(R.string.error_unknown)) },
            onValidate = { task ->
                if (task.propertyName == "identification") viewModel.validateAndCreateCardFromIdentification(task)
                else viewModel.validateProperty(task)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        propertyAdapters[propertyName] = adapter
        propertiesContainer.addView(view)
    }

    private fun getTitleForProperty(propertyName: String): String {
        val propertyTitleResId = when (propertyName) {
            "identification" -> R.string.property_title_identification
            "description" -> R.string.property_title_description
            "ingredients" -> R.string.property_title_ingredients
            "allergens" -> R.string.property_title_allergens
            "stats.energy" -> R.string.property_title_energy
            "biological.scientificName" -> R.string.property_title_scientific_name
            "biological.vernacularName" -> R.string.property_title_vernacular_name
            "stats.floweringPeriod" -> R.string.property_title_flowering
            "stats.diet" -> R.string.property_title_diet
            "stats.wingspan" -> R.string.property_title_wingspan
            else -> 0
        }
        return if (propertyTitleResId != 0) {
            getString(R.string.workshop_refinement_title_format, getString(propertyTitleResId))
        } else {
            getString(R.string.workshop_refinement_title_format, propertyName.replaceFirstChar { it.titlecase() })
        }
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_error_details_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }
}