package com.hoursgram.app

import android.app.Application
import android.os.Build
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatPosition
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.Update
import dev.g000sha256.tdl.dto.UpdateAuthorizationState
import dev.g000sha256.tdl.dto.UpdateChatLastMessage
import dev.g000sha256.tdl.dto.UpdateChatPhoto
import dev.g000sha256.tdl.dto.UpdateChatPosition
import dev.g000sha256.tdl.dto.UpdateChatReadInbox
import dev.g000sha256.tdl.dto.UpdateChatTitle
import dev.g000sha256.tdl.dto.UpdateFile
import dev.g000sha256.tdl.dto.UpdateNewChat
import dev.g000sha256.tdl.dto.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class ChatRow(
    val id: Long,
    val title: String,
    val lastText: String,
    val lastDate: Int,
    val unread: Int,
    val order: Long,
    val pinned: Boolean,
    val photoFileId: Int,
    val photoPath: String?,
)

fun previewOf(message: Message?): String {
    if (message == null) return ""
    return when (val content = message.content) {
        is MessageText -> content.text.text
        is MessagePhoto -> "Фото" + content.caption.text.let { if (it.isEmpty()) "" else ": $it" }
        is MessageSticker -> content.sticker.emoji + " Стикер"
        else -> content::class.simpleName.orEmpty().removePrefix("Message").ifEmpty { "Сообщение" }
    }
}

class TdRepo(private val app: Application, apiId: Int, apiHash: String) {

    val client: TdlClient = TdlClient.create()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val authFlow = MutableStateFlow<AuthorizationState?>(null)
    private val chatsFlow = MutableStateFlow<List<ChatRow>>(emptyList())
    private val meFlow = MutableStateFlow<User?>(null)

    val auth: StateFlow<AuthorizationState?> = authFlow.asStateFlow()
    val chats: StateFlow<List<ChatRow>> = chatsFlow.asStateFlow()
    val me: StateFlow<User?> = meFlow.asStateFlow()

    private val rows = HashMap<Long, ChatRow>()
    private val userNames = HashMap<Long, String>()
    private val keys = apiId to apiHash
    private var paramsSent = false
    private var chatsExhausted = false

    init {
        // Updates are collected on one coroutine, so `rows` needs no lock.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.allUpdates.collect { handle(it) }
        }
        scope.launch {
            val state = client.getAuthorizationState()
            if (state is TdlResult.Success) handleAuth(state.result)
        }
    }

    private fun handle(update: Update) {
        when (update) {
            is UpdateAuthorizationState -> handleAuth(update.authorizationState)
            is UpdateNewChat -> put(fromChat(update.chat))
            is UpdateChatLastMessage -> rows[update.chatId]?.let {
                val position = mainPosition(update.positions)
                put(
                    ChatRow(
                        it.id, it.title, previewOf(update.lastMessage),
                        update.lastMessage?.date ?: 0, it.unread,
                        position?.order ?: it.order, position?.isPinned ?: it.pinned,
                        it.photoFileId, it.photoPath,
                    )
                )
            }
            is UpdateChatPosition -> rows[update.chatId]?.let {
                if (update.position.list is ChatListMain) {
                    put(
                        ChatRow(
                            it.id, it.title, it.lastText, it.lastDate, it.unread,
                            update.position.order, update.position.isPinned,
                            it.photoFileId, it.photoPath,
                        )
                    )
                }
            }
            is UpdateChatReadInbox -> rows[update.chatId]?.let {
                put(
                    ChatRow(
                        it.id, it.title, it.lastText, it.lastDate, update.unreadCount,
                        it.order, it.pinned, it.photoFileId, it.photoPath,
                    )
                )
            }
            is UpdateChatTitle -> rows[update.chatId]?.let {
                put(
                    ChatRow(
                        it.id, update.title, it.lastText, it.lastDate, it.unread,
                        it.order, it.pinned, it.photoFileId, it.photoPath,
                    )
                )
            }
            is UpdateChatPhoto -> rows[update.chatId]?.let {
                val file = update.photo?.small
                put(
                    ChatRow(
                        it.id, it.title, it.lastText, it.lastDate, it.unread,
                        it.order, it.pinned, file?.id ?: 0, readyPath(file?.local?.path, file?.local?.isDownloadingCompleted),
                    )
                )
                if (file != null && !file.local.isDownloadingCompleted) download(file.id)
            }
            is UpdateFile -> {
                val file = update.file
                val path = readyPath(file.local.path, file.local.isDownloadingCompleted) ?: return
                rows.values.filter { it.photoFileId == file.id && it.photoPath != path }.forEach {
                    put(
                        ChatRow(
                            it.id, it.title, it.lastText, it.lastDate, it.unread,
                            it.order, it.pinned, it.photoFileId, path,
                        )
                    )
                }
            }
            else -> Unit
        }
    }

    private fun readyPath(path: String?, done: Boolean?): String? =
        if (done == true && !path.isNullOrEmpty()) path else null

    private fun mainPosition(positions: Array<ChatPosition>): ChatPosition? =
        positions.firstOrNull { it.list is ChatListMain }

    private fun fromChat(chat: Chat): ChatRow {
        val position = mainPosition(chat.positions)
        val photo = chat.photo?.small
        if (photo != null && !photo.local.isDownloadingCompleted) download(photo.id)
        return ChatRow(
            chat.id, chat.title, previewOf(chat.lastMessage), chat.lastMessage?.date ?: 0,
            chat.unreadCount, position?.order ?: 0L, position?.isPinned ?: false,
            photo?.id ?: 0, readyPath(photo?.local?.path, photo?.local?.isDownloadingCompleted),
        )
    }

    private fun put(row: ChatRow) {
        rows[row.id] = row
        chatsFlow.value = rows.values.filter { it.order != 0L }.sortedByDescending { it.order }
    }

    private fun download(fileId: Int) {
        scope.launch {
            client.downloadFile(fileId = fileId, priority = 1, offset = 0, limit = 0, synchronous = false)
        }
    }

    fun downloadFile(fileId: Int) = download(fileId)

    private fun handleAuth(state: AuthorizationState) {
        authFlow.value = state
        when (state) {
            is AuthorizationStateWaitTdlibParameters -> if (!paramsSent) {
                paramsSent = true
                scope.launch { sendParameters() }
            }
            is AuthorizationStateReady -> scope.launch {
                val me = client.getMe()
                if (me is TdlResult.Success) meFlow.value = me.result
                loadMoreChats()
            }
            else -> Unit
        }
    }

    private suspend fun sendParameters() {
        val base = File(app.filesDir, "tdlib")
        val result = client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = File(base, "db").absolutePath,
            filesDirectory = File(base, "files").absolutePath,
            databaseEncryptionKey = ByteArray(0),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = keys.first,
            apiHash = keys.second,
            systemLanguageCode = Locale.getDefault().language.ifEmpty { "en" },
            deviceModel = Build.MODEL,
            systemVersion = Build.VERSION.RELEASE,
            applicationVersion = BuildConfig.VERSION_NAME,
        )
        if (result is TdlResult.Failure) paramsSent = false
    }

    suspend fun loadMoreChats() {
        if (chatsExhausted) return
        val result = client.loadChats(chatList = ChatListMain(), limit = 100)
        // TDLib answers 404 once every chat of the list has been loaded.
        if (result is TdlResult.Failure) chatsExhausted = true
    }

    private fun <T> TdlResult<T>.error(): String? = (this as? TdlResult.Failure)?.message

    suspend fun sendPhone(phone: String): String? =
        client.setAuthenticationPhoneNumber(phoneNumber = phone, settings = null).error()

    suspend fun sendCode(code: String): String? = client.checkAuthenticationCode(code).error()

    suspend fun sendPassword(password: String): String? = client.checkAuthenticationPassword(password).error()

    suspend fun logOut() {
        client.logOut()
    }

    suspend fun userName(userId: Long): String {
        userNames[userId]?.let { return it }
        val result = client.getUser(userId)
        val name = if (result is TdlResult.Success) {
            "${result.result.firstName} ${result.result.lastName}".trim()
        } else {
            ""
        }
        if (name.isNotEmpty()) userNames[userId] = name
        return name
    }

    suspend fun senderName(sender: MessageSender): String = when (sender) {
        is MessageSenderUser -> userName(sender.userId)
        is MessageSenderChat -> rows[sender.chatId]?.title.orEmpty()
        else -> ""
    }

    suspend fun isPrivate(chatId: Long): Boolean {
        val result = client.getChat(chatId)
        return result is TdlResult.Success && result.result.type is ChatTypePrivate
    }
}
