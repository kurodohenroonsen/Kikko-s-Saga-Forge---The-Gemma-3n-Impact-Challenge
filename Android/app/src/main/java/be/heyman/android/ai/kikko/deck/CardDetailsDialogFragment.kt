package be.heyman.android.ai.kikko.deck

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.royal_audience.RoyalAudienceActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import java.io.File
import java.util.Locale

class CardDetailsDialogFragment : DialogFragment() {

    interface CardDetailsListener {
        fun onDeleteCard(card: KnowledgeCard)
        fun onLaunchQuiz(card: KnowledgeCard)
        fun onTranslateCard(card: KnowledgeCard)
    }

    private var listener: CardDetailsListener? = null
    private lateinit var card: KnowledgeCard

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? CardDetailsListener ?: context as? CardDetailsListener
        if (listener == null) {
            android.util.Log.w("CardDetailsDialog", "$context must implement CardDetailsListener to handle actions.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_Kikko_FullScreenDialog)
        arguments?.let {
            @Suppress("DEPRECATION")
            card = it.getParcelable(ARG_CARD)!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_card_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageView: ImageView = view.findViewById(R.id.card_details_image)
        val specificNameTextView: TextView = view.findViewById(R.id.card_details_specific_name)
        val deckNameChip: Chip = view.findViewById(R.id.card_details_deck_name)
        val confidenceTextView: TextView = view.findViewById(R.id.card_details_confidence)
        val descriptionTextView: TextView = view.findViewById(R.id.card_details_description)
        val reasoningTextView: TextView = view.findViewById(R.id.card_details_reasoning)
        val statsTextView: TextView = view.findViewById(R.id.card_details_stats)
        val deleteButton: Button = view.findViewById(R.id.card_details_button_delete)
        val quizButton: Button = view.findViewById(R.id.card_details_button_quiz)
        val chatButton: Button = view.findViewById(R.id.card_details_button_chat)
        val translateButton: Button = view.findViewById(R.id.card_details_button_translate)

        val deviceLang = Locale.getDefault().language
        val translatedContent = card.translations?.get(deviceLang)

        val displayedName = card.specificName
        val displayedDescription = translatedContent?.description ?: card.description
        val displayedReasoning = translatedContent?.reasoning ?: card.reasoning
        val displayedQuiz = translatedContent?.quiz ?: card.quiz


        card.imagePath?.let { path ->
            val imgFile = File(path)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                imageView.setImageBitmap(bitmap)
            }
        }

        specificNameTextView.text = displayedName
        deckNameChip.text = card.deckName
        confidenceTextView.text = getString(R.string.card_details_confidence_format, card.confidence)
        descriptionTextView.text = displayedDescription

        reasoningTextView.text = getString(
            R.string.card_details_reasoning_format,
            displayedReasoning.visualAnalysis,
            displayedReasoning.evidenceCorrelation
        )

        val statsText = card.stats?.items?.map { (key, value) ->
            "${key.replaceFirstChar { it.titlecase() }}: $value"
        }?.joinToString("\n") ?: getString(R.string.card_details_no_stats_available)
        statsTextView.text = statsText

        deleteButton.setOnClickListener {
            listener?.onDeleteCard(card)
            dismiss()
        }

        quizButton.setOnClickListener {
            listener?.onLaunchQuiz(card)
            dismiss()
        }
        // Le bouton de quiz est désactivé via le layout et ne sera pas réactivé ici
        // quizButton.isEnabled = !displayedQuiz.isNullOrEmpty()

        chatButton.setOnClickListener {
            val intent = RoyalAudienceActivity.newIntent(requireContext(), card.id)
            startActivity(intent)
            dismiss()
        }

        translateButton.setOnClickListener {
            listener?.onTranslateCard(card)
            dismiss()
        }
        // Le bouton de traduction est désactivé via le layout et ne sera pas réactivé ici.
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        private const val ARG_CARD = "card"

        fun newInstance(card: KnowledgeCard): CardDetailsDialogFragment {
            val fragment = CardDetailsDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_CARD, card)
            fragment.arguments = args
            return fragment
        }
    }
}