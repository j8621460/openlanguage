package com.palabrasfuncionales.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.palabrasfuncionales.app.R
import com.palabrasfuncionales.app.data.DictionaryRepository
import com.palabrasfuncionales.app.data.Entry
import com.palabrasfuncionales.app.data.LanguagePack
import com.palabrasfuncionales.app.databinding.ActivityStudyBinding
import com.palabrasfuncionales.app.learn.CardState
import com.palabrasfuncionales.app.learn.Lesson
import com.palabrasfuncionales.app.learn.Lessons
import com.palabrasfuncionales.app.learn.Question
import com.palabrasfuncionales.app.learn.Quiz
import com.palabrasfuncionales.app.learn.Srs
import com.palabrasfuncionales.app.learn.SrsStore
import com.palabrasfuncionales.app.learn.Streak
import com.palabrasfuncionales.app.util.Speaker
import com.palabrasfuncionales.app.util.toast
import com.palabrasfuncionales.app.vocab.VocabRepository
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * One study session.
 * Lesson: 1) Learn: flashcards, say each word aloud, then flip.
 *         2) Practice: multiple choice both ways; mistakes come back at the end.
 *         3) Result: spaced-repetition schedule updated, tip to remember better.
 * Review: only the practice step, with the cards that are due today.
 */
class StudyActivity : AppCompatActivity() {
    private lateinit var b: ActivityStudyBinding
    private var pack: LanguagePack? = null
    private var store: SrsStore? = null
    private var speaker: Speaker? = null
    private var lessons: List<Lesson> = emptyList()
    private var lesson: Lesson? = null
    private var cards: List<Entry> = emptyList()

    private var learnIndex = 0
    private var revealed = false
    private val queue = ArrayDeque<Question>()
    private val firstTry = LinkedHashMap<String, Boolean>()
    private val retried = HashSet<String>()
    private var current: Question? = null
    private var answered = false
    private var quizTotal = 0
    private var quizDone = 0
    private val rnd = Random(System.nanoTime())
    private lateinit var optionButtons: List<MaterialButton>
    private var source = SOURCE_FUNC
    private var timed = false
    private var timer: CountDownTimer? = null
    private var timedScore = 0
    private var timedOver = false
    private var baseTint: ColorStateList? = null
    private var baseText: ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityStudyBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        b.toolbar.setNavigationOnClickListener { finish() }
        speaker = Speaker(this)
        optionButtons = listOf(b.option1, b.option2, b.option3, b.option4)
        optionButtons.forEachIndexed { i, btn -> btn.setOnClickListener { onAnswer(i) } }
        baseTint = b.option1.backgroundTintList
        baseText = b.option1.textColors

        b.cardAction.setOnClickListener { if (!revealed) reveal() else nextCard() }
        b.cardListen.setOnClickListener { cards.getOrNull(learnIndex)?.let { speak(it) } }
        b.quizListen.setOnClickListener { current?.let { speak(it.entry) } }
        b.quizContinue.setOnClickListener { nextQuestion() }
        b.finish.setOnClickListener { finish() }
        b.repeat.setOnClickListener { if (timed) startTimed() else start(lesson?.key) }
        b.nextLesson.setOnClickListener { nextLessonAfter(lesson)?.let { start(it.key) } }

        val code = intent.getStringExtra(EXTRA_LANG) ?: return finish()
        source = intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE_FUNC
        timed = intent.getBooleanExtra(EXTRA_TIMED, false)
        lifecycleScope.launch {
            val p = if (source == SOURCE_VOCAB) VocabRepository.pack(this@StudyActivity, code)
            else DictionaryRepository.load(this@StudyActivity, code)
            pack = p
            store = SrsStore(this@StudyActivity, if (source == SOURCE_VOCAB) vocabStoreCode(code) else code)
            lessons = Lessons.build(p)
            if (p.entries.size < 4) {
                toast(R.string.vocab_not_ready)
                finish()
                return@launch
            }
            if (timed) startTimed() else start(intent.getStringExtra(EXTRA_LESSON))
        }
    }

    private fun start(lessonKey: String?) {
        val p = pack ?: return
        firstTry.clear(); retried.clear(); queue.clear()
        learnIndex = 0; quizDone = 0; quizTotal = 0; current = null
        if (lessonKey == null) {
            lesson = null
            val states = store?.all().orEmpty()
            val today = Srs.today()
            cards = p.entries
                .filter { Srs.isDue(states[it.cardKey], today) }
                .sortedBy { states[it.cardKey]?.dueDay ?: 0L }
                .take(REVIEW_MAX)
            b.toolbar.title = getString(R.string.review_title, p.lang.english)
            if (cards.isEmpty()) {
                toast(R.string.nothing_due)
                finish()
                return
            }
            startQuiz()
        } else {
            val l = lessons.firstOrNull { it.key == lessonKey } ?: return finish()
            lesson = l
            cards = l.entries
            b.toolbar.title = getString(R.string.lesson_title, l.section, l.part, l.parts)
            showPane(b.cardPane)
            showCard()
        }
    }

    // ---- 1. Learn (flashcards) -------------------------------------------

    private fun showCard() {
        val e = cards[learnIndex]
        revealed = false
        b.cardStep.text = getString(R.string.step_learn, learnIndex + 1, cards.size)
        b.cardWord.text = e.word
        b.cardReading.text = e.readingAid
        b.cardReading.isVisible = !e.readingAid.isNullOrBlank()
        b.cardBack.isVisible = false
        b.cardHint.isVisible = true
        b.cardAction.setText(R.string.show_meaning)
        b.cardMeaning.text = e.spanish
        val example = e.example ?: e.exampleTranslit
        b.cardExample.text = example
        b.cardExample.isVisible = !example.isNullOrBlank()
        b.cardExampleEs.text = e.exampleSpanish
        b.cardExampleEs.isVisible = !e.exampleSpanish.isNullOrBlank()
        b.cardNote.text = e.note
        b.cardNoteBox.isVisible = !e.note.isNullOrBlank()
        updateProgress()
    }

    private fun reveal() {
        revealed = true
        b.cardBack.isVisible = true
        b.cardHint.isVisible = false
        b.cardAction.setText(if (learnIndex == cards.lastIndex) R.string.start_practice else R.string.next)
    }

    private fun nextCard() {
        if (learnIndex < cards.lastIndex) {
            learnIndex++
            showCard()
        } else {
            learnIndex = cards.size
            startQuiz()
        }
    }

    // ---- 2. Practice (quiz) ----------------------------------------------

    private fun startQuiz() {
        val p = pack ?: return
        showPane(b.quizPane)
        cards.shuffled(rnd).forEachIndexed { i, e -> queue.addLast(Quiz.make(e, p.entries, reverse = i % 2 == 1, rnd = rnd)) }
        quizTotal = queue.size
        nextQuestion()
    }

    private fun nextQuestion() {
        val q = queue.removeFirstOrNull() ?: return finishSession()
        render(q)
    }

    private fun render(q: Question) {
        val p = pack ?: return
        current = q
        answered = false
        b.quizStep.text = if (timed) getString(R.string.timed_score_live, timedScore)
        else getString(R.string.step_practice, quizDone + 1, quizTotal)
        b.quizAsk.text = if (q.reverse) getString(R.string.ask_reverse, p.lang.english) else getString(R.string.ask_forward)
        b.quizPrompt.text = if (q.reverse) q.entry.spanish else q.entry.word
        b.quizReading.text = q.entry.readingAid
        b.quizReading.isVisible = !q.reverse && !q.entry.readingAid.isNullOrBlank()
        b.quizListen.isVisible = !q.reverse
        b.quizFeedback.isVisible = false
        b.quizContinue.isVisible = false
        optionButtons.forEachIndexed { i, btn ->
            val text = q.options.getOrNull(i)
            btn.isVisible = text != null
            btn.text = text
            btn.isEnabled = true
            btn.backgroundTintList = baseTint
            baseText?.let { btn.setTextColor(it) }
        }
        if (!timed) updateProgress()
    }

    // ---- 60-second game ----------------------------------------------------

    private fun startTimed() {
        val p = pack ?: return
        lesson = null
        cards = p.entries.filter { it.spanish.isNotBlank() }
        timedScore = 0
        quizDone = 0
        timedOver = false
        b.toolbar.title = getString(R.string.timed_title)
        showPane(b.quizPane)
        b.progress.max = TIMED_MS.toInt()
        timer?.cancel()
        timer = object : CountDownTimer(TIMED_MS, 250) {
            override fun onTick(left: Long) {
                b.toolbar.subtitle = getString(R.string.seconds_left, (left / 1000 + 1).toInt())
                b.progress.setProgressCompat((TIMED_MS - left).toInt(), false)
            }

            override fun onFinish() {
                timedOver = true
                finishTimed()
            }
        }.start()
        nextTimed()
    }

    private fun nextTimed() {
        val p = pack ?: return
        if (timedOver || isFinishing) return
        val e = cards[rnd.nextInt(cards.size)]
        render(Quiz.make(e, p.entries, rnd.nextBoolean(), rnd))
    }

    private fun finishTimed() {
        timer?.cancel()
        b.toolbar.subtitle = null
        val code = pack?.lang?.code ?: return
        val prefs = getSharedPreferences("stats", MODE_PRIVATE)
        val key = bestKey(source, code)
        val best = prefs.getInt(key, 0)
        val record = timedScore > best
        if (record) prefs.edit().putInt(key, timedScore).apply()
        Streak.record(this)
        showPane(b.resultPane)
        b.progress.setProgressCompat(b.progress.max, true)
        b.resultTitle.setText(R.string.time_up)
        b.resultScore.text = resources.getQuantityString(R.plurals.timed_score, timedScore, timedScore)
        b.resultNext.text = if (record) getString(R.string.new_record) else getString(R.string.timed_best, best)
        b.resultStreak.text = getString(R.string.streak_days, Streak.current(this))
        val tips = resources.getStringArray(R.array.learning_tips)
        b.resultTip.text = tips[rnd.nextInt(tips.size)]
        b.repeat.isVisible = true
        b.repeat.setText(R.string.play_again)
        b.nextLesson.isVisible = false
    }

    private fun onAnswer(index: Int) {
        val q = current ?: return
        val p = pack ?: return
        if (answered) return
        answered = true
        val correct = index == q.answer
        if (timed) {
            if (correct) timedScore++
            paintAnswer(q, index)
            b.quizStep.text = getString(R.string.timed_score_live, timedScore)
            b.root.postDelayed({ nextTimed() }, if (correct) 450L else 1300L)
            return
        }
        val key = q.entry.cardKey
        if (key !in firstTry) firstTry[key] = correct
        if (!correct && retried.add(key)) {
            // Seeing it again later in the session, the other way round.
            queue.addLast(Quiz.make(q.entry, p.entries, !q.reverse, rnd))
            quizTotal++
        }
        quizDone++
        paintAnswer(q, index)
        val praise = resources.getStringArray(R.array.praise)
        val example = listOfNotNull(q.entry.example ?: q.entry.exampleTranslit, q.entry.exampleSpanish?.let { "($it)" })
            .joinToString(" ")
        b.quizFeedback.text = buildString {
            append(if (correct) praise[rnd.nextInt(praise.size)] else getString(R.string.was_answer, q.options[q.answer]))
            if (example.isNotBlank()) append("\n").append(example)
            if (!correct && !q.entry.note.isNullOrBlank()) append("\n").append(getString(R.string.watch_out)).append(": ").append(q.entry.note)
        }
        b.quizFeedback.isVisible = true
        b.quizContinue.isVisible = true
        b.quizContinue.setText(if (queue.isEmpty()) R.string.see_result else R.string.next)
        speak(q.entry, quiet = true)
        updateProgress()
    }

    private fun paintAnswer(q: Question, index: Int) {
        val good = MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorPrimaryContainer)
        val onGood = MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorOnPrimaryContainer)
        val bad = MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorErrorContainer)
        val onBad = MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorOnErrorContainer)
        optionButtons.forEachIndexed { i, btn ->
            btn.isEnabled = false
            when (i) {
                q.answer -> { btn.backgroundTintList = ColorStateList.valueOf(good); btn.setTextColor(onGood) }
                index -> { btn.backgroundTintList = ColorStateList.valueOf(bad); btn.setTextColor(onBad) }
            }
        }
    }

    // ---- 3. Result ---------------------------------------------------------

    private fun finishSession() {
        val st = store ?: return
        val today = Srs.today()
        val states = st.all()
        val updates = HashMap<String, CardState>()
        firstTry.forEach { (key, ok) -> updates[key] = Srs.review(states[key], ok, today) }
        st.putAll(updates)
        Streak.record(this)

        val right = firstTry.values.count { it }
        val total = firstTry.size.coerceAtLeast(1)
        val pct = right * 100 / total
        showPane(b.resultPane)
        b.progress.setProgressCompat(b.progress.max, true)
        b.resultTitle.setText(
            when {
                pct >= 90 -> R.string.result_great
                pct >= 60 -> R.string.result_good
                else -> R.string.result_keep_going
            }
        )
        b.resultScore.text = getString(R.string.result_score, right, firstTry.size, pct)
        val nextDays = updates.values.minOfOrNull { it.dueDay - today } ?: 1L
        b.resultNext.text = resources.getQuantityString(R.plurals.next_review_in, nextDays.toInt(), nextDays.toInt())
        b.resultStreak.text = getString(R.string.streak_days, Streak.current(this))
        val tips = resources.getStringArray(R.array.learning_tips)
        b.resultTip.text = tips[rnd.nextInt(tips.size)]
        b.repeat.isVisible = lesson != null
        b.repeat.setText(R.string.repeat_lesson)
        b.nextLesson.isVisible = nextLessonAfter(lesson) != null
    }

    private fun nextLessonAfter(l: Lesson?): Lesson? {
        if (l == null) return null
        val i = lessons.indexOfFirst { it.key == l.key }
        return lessons.getOrNull(i + 1)
    }

    // ---- helpers ----------------------------------------------------------

    private fun showPane(pane: View) {
        listOf(b.cardPane, b.quizPane, b.resultPane).forEach { it.isVisible = it === pane }
        b.scroll.scrollTo(0, 0)
    }

    private fun updateProgress() {
        val learnUnits = if (lesson != null) cards.size else 0
        val total = (learnUnits + quizTotal).coerceAtLeast(1)
        val done = learnIndex.coerceAtMost(learnUnits) + quizDone
        b.progress.max = total
        b.progress.setProgressCompat(done, true)
    }

    private fun speak(e: Entry, quiet: Boolean = false) {
        val p = pack ?: return
        speaker?.speak(e.speakable, p.lang.ttsTag) { ok ->
            if (!ok && !quiet) toast(getString(R.string.no_voice, p.lang.english))
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_LANG = "lang"
        private const val EXTRA_LESSON = "lesson"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_TIMED = "timed"
        private const val REVIEW_MAX = 20
        private const val TIMED_MS = 60_000L
        const val SOURCE_FUNC = "func"
        const val SOURCE_VOCAB = "vocab"

        fun vocabStoreCode(code: String) = "v_$code"
        fun bestKey(source: String, code: String) = "best_timed_${source}_$code"

        /** [lessonKey] null = review the cards due today. [timed] = 60-second game. */
        fun intent(
            context: Context,
            code: String,
            lessonKey: String?,
            source: String = SOURCE_FUNC,
            timed: Boolean = false,
        ): Intent =
            Intent(context, StudyActivity::class.java)
                .putExtra(EXTRA_LANG, code)
                .putExtra(EXTRA_LESSON, lessonKey)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_TIMED, timed)
    }
}
