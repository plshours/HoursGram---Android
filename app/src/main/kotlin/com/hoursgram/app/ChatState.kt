package com.hoursgram.app

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.Update
import dev.g000sha256.tdl.dto.UpdateDeleteMessages
import dev.g000sha256.tdl.dto.UpdateFile
import dev.g000sha256.tdl.dto.UpdateMessageContent
import dev.g000sha256.tdl.dto.UpdateMessageSendSucceeded
import dev.g000sha256.tdl.dto.UpdateNewMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MsgItem(
    val id: Long,
    val outgoing: Boolean,
    val date: Int,
    val text: String,
    val sender: String,
    val photoFileId: Int,
    val photoPath: String?,
    val pending: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatState(private val repo: TdRepo, val chatId: Long) {

    // One thread for all mutations, so `map` needs no lock.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val map = HashMap<Long, MsgItem>()
    private val itemsFlow = MutableStateFlow<List<MsgItem>>(emptyList())
    private val loadingFlow = MutableStateFlow(false)
    private var isPrivate = true
    private var endReached = false
    private var emptyTries = 0

    val items: StateFlow<List<MsgItem>> = itemsFlow.asStateFlow()
    val loading: StateFlow<Boolean> = loadingFlow.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repo.client.allUpdates.collect { onUpdate(it) }
        }
        scope.launch {
            isPrivate = repo.isPrivate(chatId)
            repo.client.openChat(chatId)
            loadMore()
        }
    }

    fun close() {
        repo.scope.launch { repo.client.closeChat(chatId) }
        scope.cancel()
    }

    fun loadMore() {
        scope.launch { loadOlder() }
    }

    private suspend fun loadOlder() {
        if (endReached || loadingFlow.value) return
        loadingFlow.value = true
        val from = map.values.filter { !it.pending }.minOfOrNull { it.id } ?: 0L
        val result = repo.client.getChatHistory(
            chatId = chatId, fromMessageId = from, offset = 0, limit = 40, onlyLocal = false,
        )
        if (result is TdlResult.Success) {
            val messages = result.result.messages.filterNotNull()
            if (messages.isEmpty()) {
                // The first answer may be empty while TDLib is still fetching.
                if (map.isNotEmpty() || ++emptyTries > 2) endReached = true
            } else {
                messages.forEach { map[it.id] = toItem(it, false) }
                publish()
                markRead(messages)
            }
        }
        loadingFlow.value = false
    }

    private suspend fun toItem(message: Message, pending: Boolean): MsgItem {
        var photoId = 0
        var photoPath: String? = null
        val content = message.content
        val text = when (content) {
            is MessageText -> content.text.text
            is MessagePhoto -> {
                val size = content.photo.sizes.filter { it.width <= 1280 }.maxByOrNull { it.width }
                    ?: content.photo.sizes.firstOrNull()
                if (size != null) {
                    photoId = size.photo.id
                    if (size.photo.local.isDownloadingCompleted) photoPath = size.photo.local.path
                    else repo.downloadFile(size.photo.id)
                }
                content.caption.text
            }
            else -> previewOf(message)
        }
        val sender = if (isPrivate || message.isOutgoing) "" else repo.senderName(message.senderId)
        return MsgItem(message.id, message.isOutgoing, message.date, text, sender, photoId, photoPath, pending)
    }

    private fun publish() {
        itemsFlow.value = map.values.sortedWith(
            compareByDescending<MsgItem> { it.date }.thenByDescending { it.id }
        )
    }

    private suspend fun markRead(messages: List<Message>) {
        val ids = messages.filter { !it.isOutgoing }.map { it.id }.toLongArray()
        if (ids.isNotEmpty()) {
            repo.client.viewMessages(chatId = chatId, messageIds = ids, source = null, forceRead = true)
        }
    }

    private suspend fun onUpdate(update: Update) {
        when (update) {
            is UpdateNewMessage -> if (update.message.chatId == chatId) {
                map[update.message.id] = toItem(update.message, update.message.sendingState != null)
                publish()
                markRead(listOf(update.message))
            }
            is UpdateMessageSendSucceeded -> if (update.message.chatId == chatId) {
                map.remove(update.oldMessageId)
                map[update.message.id] = toItem(update.message, false)
                publish()
            }
            is UpdateMessageContent -> if (update.chatId == chatId) {
                val old = map[update.messageId] ?: return
                val content = update.newContent
                if (content is MessageText) {
                    map[old.id] = MsgItem(
                        old.id, old.outgoing, old.date, content.text.text, old.sender,
                        old.photoFileId, old.photoPath, old.pending,
                    )
                    publish()
                }
            }
            is UpdateDeleteMessages -> if (update.chatId == chatId && update.isPermanent) {
                update.messageIds.forEach { map.remove(it) }
                publish()
            }
            is UpdateFile -> {
                val file = update.file
                if (!file.local.isDownloadingCompleted || file.local.path.isEmpty()) return
                var changed = false
                map.values.filter { it.photoFileId == file.id && it.photoPath == null }.forEach {
                    map[it.id] = MsgItem(
                        it.id, it.outgoing, it.date, it.text, it.sender,
                        it.photoFileId, file.local.path, it.pending,
                    )
                    changed = true
                }
                if (changed) publish()
            }
            else -> Unit
        }
    }

    suspend fun send(text: String): String? {
        val result = repo.client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = null,
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray()),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        )
        return (result as? TdlResult.Failure)?.message
    }
}
