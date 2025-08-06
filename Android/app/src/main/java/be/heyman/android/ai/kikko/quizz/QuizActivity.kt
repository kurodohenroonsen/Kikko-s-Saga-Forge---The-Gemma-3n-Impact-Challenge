package be.heyman.android.ai.kikko.quiz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.QuizQuestion
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

class QuizActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressTextView: TextView
    private lateinit var questionTextView: TextView
    private lateinit var answersRadioGroup: RadioGroup
    private lateinit var answerRadioButtons: List<RadioButton>
    private lateinit var submitButton: Button
    private lateinit var nextButton: Button
    private lateinit var feedbackCard: MaterialCardView
    private lateinit var feedbackTextView: TextView

    private var quizQuestions: List<QuizQuestion> = emptyList()
    private var currentQuestionIndex = 0
    private var score = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        // BOURDON'S REFACTOR: Use of a dedicated 'card' variable for clarity.
        val card: KnowledgeCard? = intent.getParcelableExtra(EXTRA_CARD)
        quizQuestions = card?.quiz ?: emptyList()

        if (quizQuestions.isEmpty()) {
            Toast.makeText(this, "Erreur: Aucune question de quiz trouvée.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupToolbar(card?.specificName ?: "Quiz")
        setupListeners()
        displayQuestion()
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.quiz_toolbar)
        progressTextView = findViewById(R.id.quiz_progress_textview)
        questionTextView = findViewById(R.id.quiz_question_textview)
        answersRadioGroup = findViewById(R.id.quiz_answers_radiogroup)
        answerRadioButtons = listOf(
            findViewById(R.id.quiz_answer_1),
            findViewById(R.id.quiz_answer_2),
            findViewById(R.id.quiz_answer_3),
            findViewById(R.id.quiz_answer_4)
        )
        submitButton = findViewById(R.id.quiz_submit_button)
        nextButton = findViewById(R.id.quiz_next_button)
        feedbackCard = findViewById(R.id.quiz_feedback_card)
        feedbackTextView = findViewById(R.id.quiz_feedback_textview)
    }

    private fun setupToolbar(cardName: String) {
        toolbar.title = "Quiz : $cardName"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        submitButton.setOnClickListener {
            val selectedAnswerId = answersRadioGroup.checkedRadioButtonId
            if (selectedAnswerId != -1) {
                val selectedRadioButton = findViewById<RadioButton>(selectedAnswerId)
                val selectedIndex = answerRadioButtons.indexOf(selectedRadioButton)
                checkAnswer(selectedIndex)
            } else {
                Toast.makeText(this, "Veuillez sélectionner une réponse.", Toast.LENGTH_SHORT).show()
            }
        }

        nextButton.setOnClickListener {
            currentQuestionIndex++
            if (currentQuestionIndex < quizQuestions.size) {
                displayQuestion()
            } else {
                showFinalScore()
            }
        }
    }

    private fun displayQuestion() {
        resetQuestionState()
        val question = quizQuestions[currentQuestionIndex]
        progressTextView.text = "Question ${currentQuestionIndex + 1} / ${quizQuestions.size}"
        questionTextView.text = question.question
        question.options.forEachIndexed { index, optionText ->
            if(index < answerRadioButtons.size) {
                answerRadioButtons[index].text = optionText
                answerRadioButtons[index].visibility = View.VISIBLE
            }
        }
    }

    private fun checkAnswer(selectedIndex: Int) {
        val question = quizQuestions[currentQuestionIndex]
        val isCorrect = selectedIndex == question.correctAnswerIndex

        if (isCorrect) {
            score++
            feedbackTextView.text = "Bonne réponse !"
            feedbackCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.kikko_success_green))
        } else {
            val correctAnswer = question.options[question.correctAnswerIndex]
            feedbackTextView.text = "Incorrect. La bonne réponse était : \n\"$correctAnswer\""
            feedbackCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.kikko_error_red))
        }

        feedbackTextView.setTextColor(ContextCompat.getColor(this, R.color.kikko_bark_brown))
        feedbackCard.visibility = View.VISIBLE

        toggleAnswerInteractivity(false)
        submitButton.visibility = View.GONE
        nextButton.visibility = View.VISIBLE
    }

    private fun showFinalScore() {
        val title = "Quiz Terminé !"
        val message = "Votre score : $score / ${quizQuestions.size}"
        // For simplicity, we use an AlertDialog for the final score.
        // A dedicated results screen could be created for a more polished UX.
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Terminer") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun resetQuestionState() {
        answersRadioGroup.clearCheck()
        feedbackCard.visibility = View.GONE
        submitButton.visibility = View.VISIBLE
        nextButton.visibility = View.GONE
        toggleAnswerInteractivity(true)
    }

    private fun toggleAnswerInteractivity(isEnabled: Boolean) {
        for (radioButton in answerRadioButtons) {
            radioButton.isEnabled = isEnabled
        }
    }

    companion object {
        private const val EXTRA_CARD = "EXTRA_CARD"

        fun newIntent(context: Context, card: KnowledgeCard): Intent {
            return Intent(context, QuizActivity::class.java).apply {
                putExtra(EXTRA_CARD, card)
            }
        }
    }
}