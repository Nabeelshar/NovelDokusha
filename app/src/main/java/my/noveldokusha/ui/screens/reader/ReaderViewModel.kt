package my.noveldokusha.ui.screens.reader

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import my.noveldokusha.AppPreferences
import my.noveldokusha.repository.Repository
import my.noveldokusha.data.database.tables.Chapter
import my.noveldokusha.ui.BaseViewModel
import my.noveldokusha.ui.screens.reader.tools.*
import my.noveldokusha.utils.StateExtra_String
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.properties.Delegates

interface ReaderStateBundle {
    var bookUrl: String
    var chapterUrl: String
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: Repository,
    state: SavedStateHandle,
    val appPreferences: AppPreferences,
    private val liveTranslation: LiveTranslation,
    @ApplicationContext private val context: Context,
) : BaseViewModel(), ReaderStateBundle {

    enum class ReaderState { IDLE, LOADING, INITIAL_LOAD }
    data class ChapterState(
        val chapterUrl: String,
        val chapterItemIndex: Int,
        val offset: Int
    )

    data class ChapterStats(val itemsCount: Int, val chapter: Chapter, val chapterIndex: Int)

    override var bookUrl by StateExtra_String(state)
    override var chapterUrl by StateExtra_String(state)

    val textFont by appPreferences.READER_FONT_FAMILY.state(viewModelScope)
    val textSize by appPreferences.READER_FONT_SIZE.state(viewModelScope)
    val isTextSelectable by appPreferences.READER_SELECTABLE_TEXT.state(viewModelScope)

    val liveTranslationSettingState get() = liveTranslation.settingsState
    val textToSpeechSettingData get() = readerSpeaker.settings
    var onTranslatorChanged
        get() = liveTranslation.onTranslatorChanged
        set(value) {
            liveTranslation.onTranslatorChanged = value
        }

    var currentChapter: ChapterState by Delegates.observable(
        ChapterState(
            chapterUrl = chapterUrl,
            chapterItemIndex = 0,
            offset = 0
        )
    ) { _, old, new ->
        chapterUrl = new.chapterUrl
        if (old.chapterUrl != new.chapterUrl) saveLastReadPositionState(
            repository,
            bookUrl,
            new,
            old
        )
    }

    var showReaderInfoView by mutableStateOf(false)
    val orderedChapters = mutableListOf<Chapter>()

    data class ReadingChapterPosStats(
        val chapterIndex: Int,
        val chapterItemIndex: Int,
        val chapterItemsCount: Int,
        val chapterTitle: String,
    )

    var readingPosStats by mutableStateOf<ReadingChapterPosStats?>(null)
    val chapterPercentageProgress by derivedStateOf {
        val data = readingPosStats ?: return@derivedStateOf 0f
        when (data.chapterItemsCount) {
            0 -> 100f
            else -> ceil((data.chapterItemIndex.toFloat() / data.chapterItemsCount.toFloat()) * 100f)
        }
    }

    private val readRoutine = ChaptersIsReadRoutine(repository)

    fun markChapterStartAsSeen(chapterUrl: String) {
        readRoutine.setReadStart(chapterUrl = chapterUrl)
    }

    fun markChapterEndAsSeen(chapterUrl: String) {
        readRoutine.setReadEnd(chapterUrl = chapterUrl)
    }

    @Volatile
    var forceUpdateListViewState: (suspend () -> Unit)? = null

    @Volatile
    var maintainLastVisiblePosition: (suspend (suspend () -> Unit) -> Unit)? = null

    @Volatile
    var maintainStartPosition: (suspend (suspend () -> Unit) -> Unit)? = null

    @Volatile
    var setInitialPosition: (suspend (ItemPosition) -> Unit)? = null

    @Volatile
    var showInvalidChapterDialog: (suspend () -> Unit)? = null

    val scrollToTheTop = MutableSharedFlow<Unit>()
    val scrollToTheBottom = MutableSharedFlow<Unit>()

    private suspend fun <T> withMainNow(fn: suspend CoroutineScope.() -> T) =
        withContext(Dispatchers.Main.immediate, fn)

    val chaptersLoader = ChaptersLoader(
        repository = repository,
        translateOrNull = { liveTranslation.translator?.translate?.invoke(it) },
        translationIsActive = { liveTranslation.translator != null },
        translationSourceLanguageOrNull = { liveTranslation.translator?.sourceLocale?.displayLanguage },
        translationTargetLanguageOrNull = { liveTranslation.translator?.targetLocale?.displayLanguage },
        bookUrl = bookUrl,
        orderedChapters = orderedChapters,
        readerState = ReaderState.INITIAL_LOAD,
        forceUpdateListViewState = { withMainNow { forceUpdateListViewState?.invoke() } },
        maintainLastVisiblePosition = { withMainNow { maintainLastVisiblePosition?.invoke(it) } },
        maintainStartPosition = { withMainNow { maintainStartPosition?.invoke(it) } },
        setInitialPosition = { withMainNow { setInitialPosition?.invoke(it) } },
        showInvalidChapterDialog = { withMainNow { showInvalidChapterDialog?.invoke() } },
    )

    val items get() = chaptersLoader.getItems()

    val readerSpeaker = ReaderSpeaker(
        coroutineScope = viewModelScope,
        context = context,
        items = items,
        chapterLoadedFlow = chaptersLoader.chapterLoadedFlow,
        isChapterIndexLoaded = chaptersLoader::isChapterIndexLoaded,
        isChapterIndexValid = chaptersLoader::isChapterIndexValid,
        tryLoadPreviousChapter = chaptersLoader::tryLoadPrevious,
        loadNextChapter = chaptersLoader::tryLoadNext,
        scrollToTheTop = scrollToTheTop,
        scrollToTheBottom = scrollToTheBottom,
        customSavedVoices = appPreferences.READER_TEXT_TO_SPEECH_SAVED_PREDEFINED_LIST.state(viewModelScope),
        setCustomSavedVoices = { appPreferences.READER_TEXT_TO_SPEECH_SAVED_PREDEFINED_LIST.value = it},
        getPreferredVoiceId = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_ID.value },
        setPreferredVoiceId = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_ID.value = it },
        getPreferredVoiceSpeed = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_SPEED.value },
        setPreferredVoiceSpeed = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_SPEED.value = it },
        getPreferredVoicePitch = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_PITCH.value },
        setPreferredVoicePitch = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_PITCH.value = it },
    )

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val chapter = async(Dispatchers.IO) { repository.bookChapters.get(chapterUrl) }
            val loadTranslator = async(Dispatchers.IO) { liveTranslation.init() }
            val chaptersList = async(Dispatchers.Default) {
                orderedChapters.also { it.addAll(repository.bookChapters.chapters(bookUrl)) }
            }
            val chapterIndex = async(Dispatchers.Default) {
                chaptersList.await().indexOfFirst { it.url == chapterUrl }
            }
            chaptersList.await()
            loadTranslator.await()
            currentChapter = ChapterState(
                chapterUrl = chapterUrl,
                chapterItemIndex = chapter.await()?.lastReadPosition ?: 0,
                offset = chapter.await()?.lastReadOffset ?: 0,
            )
            // All data prepared! Let's load the current chapter
            chaptersLoader.tryLoadInitial(chapterIndex = chapterIndex.await())
        }

        viewModelScope.launch {
            repository.libraryBooks.updateLastReadEpochTimeMilli(bookUrl, System.currentTimeMillis())
        }

        viewModelScope.launch {
            readerSpeaker.reachedChapterEndFlowChapterIndex.collect { chapterIndex ->
                withContext(Dispatchers.Main.immediate) {
                    if (chaptersLoader.isLastChapter(chapterIndex)) return@withContext
                    val nextChapterIndex = chapterIndex + 1
                    val chapterItem = chaptersLoader.orderedChapters[nextChapterIndex]
                    if (chaptersLoader.loadedChapters.contains(chapterItem.url)) {
                        readerSpeaker.readChapterStartingFromStart(
                            chapterIndex = nextChapterIndex
                        )
                    } else launch {
                        chaptersLoader.tryLoadNext()
                        chaptersLoader.chapterLoadedFlow
                            .filter { it.type == ChaptersLoader.ChapterLoaded.Type.Next }
                            .take(1)
                            .collect {
                                readerSpeaker.readChapterStartingFromStart(
                                    chapterIndex = nextChapterIndex
                                )
                            }
                    }
                }
            }
        }

        viewModelScope.launch {
            readerSpeaker
                .currentReaderItemFlow
                .debounce(timeoutMillis = 5_000)
                .filter { isReaderInSpeakMode }
                .collect {
                    saveLastReadPositionFromCurrentSpeakItem()
                }
        }

        viewModelScope.launch {
            readerSpeaker
                .currentReaderItemFlow
                .filter { isReaderInSpeakMode }
                .collect {
                    val item = it.item
                    if (item !is ReaderItem.ParagraphLocation) return@collect
                    when (item.location) {
                        ReaderItem.Location.FIRST -> markChapterStartAsSeen(chapterUrl = item.chapterUrl)
                        ReaderItem.Location.LAST -> markChapterEndAsSeen(chapterUrl = item.chapterUrl)
                        ReaderItem.Location.MIDDLE -> Unit
                    }
                }
        }
    }

    fun startSpeaker(itemIndex: Int) {
        val startingItem = items.getOrNull(itemIndex) ?: return
        readerSpeaker.start()
        viewModelScope.launch {
            readerSpeaker.readChapterStartingFromItemIndex(
                chapterIndex = startingItem.chapterIndex,
                itemIndex = itemIndex
            )
        }
    }

    fun onClose() {
        readerSpeaker.onClose()
    }

    private val isReaderInSpeakMode by derivedStateOf {
        readerSpeaker.settings.isThereActiveItem.value &&
                readerSpeaker.settings.isPlaying.value
    }

    private fun saveLastReadPositionFromCurrentSpeakItem() {
        val item = readerSpeaker.settings.currentActiveItemState.value.item
        saveLastReadPositionState(
            repository = repository,
            bookUrl = bookUrl,
            chapter = ChapterState(
                chapterUrl = item.chapterUrl,
                chapterItemIndex = item.chapterItemIndex,
                offset = 0
            )
        )
    }

    override fun onCleared() {
        chaptersLoader.coroutineContext.cancelChildren()
        if (isReaderInSpeakMode) {
            saveLastReadPositionFromCurrentSpeakItem()
        } else {
            saveLastReadPositionState(repository, bookUrl, currentChapter)
        }
        super.onCleared()
    }

    fun reloadReader() {
        chaptersLoader.reload()
        readerSpeaker.stop()
    }

    fun updateInfoViewTo(itemIndex: Int) {
        val item = items.getOrNull(itemIndex) ?: return
        if (item !is ReaderItem.Position) return
        val stats = chaptersLoader.chaptersStats[chapterUrl] ?: return
        readingPosStats = ReadingChapterPosStats(
            chapterIndex = item.chapterIndex,
            chapterItemIndex = item.chapterItemIndex,
            chapterItemsCount = stats.itemsCount,
            chapterTitle = stats.chapter.title
        )
    }
}
