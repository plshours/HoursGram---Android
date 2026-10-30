package com.hoursgram.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val avatarColors = listOf(
    Color(0xFFE17076), Color(0xFFFAA774), Color(0xFFA695E7),
    Color(0xFF7BC862), Color(0xFF6EC9CB), Color(0xFF65AADD), Color(0xFFEE7AAE),
)

private fun formatTime(date: Int): String {
    if (date == 0) return ""
    val d = Date(date * 1000L)
    val now = System.currentTimeMillis()
    val pattern = if (now - d.time < 24 * 3600 * 1000L) "HH:mm" else "dd.MM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(d)
}

@Composable
fun Avatar(path: String?, title: String, id: Long, size: Int) {
    val image = rememberImage(path, size * 3)
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(avatarColors[(id.hashCode() and 0x7fffffff) % avatarColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, null, Modifier.size(size.dp), contentScale = ContentScale.Crop)
        } else {
            Text(title.trim().take(1).uppercase(), color = Color.White, fontSize = (size / 2.4f).sp)
        }
    }
}

@Composable
fun Splash(text: String) {
    Column(
        Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CrashDialog(text: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
        title = { Text("Прошлый запуск завершился ошибкой") },
        text = {
            SelectionContainer {
                Text(
                    text.take(3000), fontSize = 11.sp,
                    modifier = Modifier.height(320.dp).androidxScroll(),
                )
            }
        },
    )
}

@Composable
fun StartErrorScreen(text: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Не удалось запустить TDLib", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(text.take(4000), fontSize = 11.sp, modifier = Modifier.androidxScroll())
        }
    }
}

@Composable
fun ApiKeysScreen(onSave: (Int, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var hash by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HorusGram", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Нужны свои ключи Telegram API: my.telegram.org → API development tools.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            id, { id = it.filter(Char::isDigit) }, label = { Text("api_id") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            hash, { hash = it.trim() }, label = { Text("api_hash") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(id.toInt(), hash) },
            enabled = id.toIntOrNull() != null && hash.length >= 16,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить") }
    }
}

@Composable
fun StepScreen(
    title: String,
    hint: String,
    label: String,
    keyboard: KeyboardType,
    secret: Boolean,
    onSubmit: suspend (String) -> String?,
) {
    var value by remember(title) { mutableStateOf("") }
    var error by remember(title) { mutableStateOf<String?>(null) }
    var busy by remember(title) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HorusGram", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value, { value = it }, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color(0xFFE57373), modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    error = onSubmit(value.trim())
                    busy = false
                }
            },
            enabled = value.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Подождите…" else "Далее") }
    }
}

@Composable
fun AuthFlow(repo: TdRepo) {
    val state by repo.auth.collectAsState()
    when (state) {
        null -> Splash("Подключение…")
        is AuthorizationStateWaitPhoneNumber -> StepScreen(
            "Ваш номер", "Код придёт в Telegram или по SMS", "Номер в формате +7…",
            KeyboardType.Phone, false,
        ) { repo.sendPhone(it) }
        is AuthorizationStateWaitCode -> StepScreen(
            "Код подтверждения", "Введите код из Telegram", "Код", KeyboardType.Number, false,
        ) { repo.sendCode(it) }
        is AuthorizationStateWaitPassword -> StepScreen(
            "Облачный пароль", "Включена двухэтапная проверка", "Пароль", KeyboardType.Password, true,
        ) { repo.sendPassword(it) }
        else -> Splash("Подготовка…")
    }
}

@Composable
fun ChatListScreen(
    repo: TdRepo,
    palette: HgPalette,
    onPalette: (HgPalette) -> Unit,
    onOpen: (ChatRow) -> Unit,
) {
    val chats by repo.chats.collectAsState()
    val me by repo.me.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                .statusBarsPadding().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "HorusGram", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(onClick = { menu = true }) {
                    Text(me?.firstName ?: "Меню")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Palettes.forEach {
                        DropdownMenuItem(
                            text = { Text((if (it === palette) "✓ " else "") + it.name) },
                            onClick = { onPalette(it); menu = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Выйти") },
                        onClick = { menu = false; scope.launch { repo.logOut() } },
                    )
                }
            }
        }
        if (chats.isEmpty()) {
            Splash("Загружаю чаты…")
        } else {
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                itemsIndexed(chats, key = { _, c -> c.id }) { index, chat ->
                    if (index >= chats.size - 10) {
                        LaunchedEffect(chats.size) { repo.loadMoreChats() }
                    }
                    ChatRowItem(chat) { onOpen(chat) }
                }
            }
        }
    }
}

@Composable
private fun ChatRowItem(chat: ChatRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat.photoPath, chat.title, chat.id, 52)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(
                    formatTime(chat.lastDate), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.lastText.replace('\n', ' '), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                if (chat.unread > 0) {
                    Box(
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) { Text(chat.unread.toString(), color = Color.White, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(repo: TdRepo, chat: ChatRow, onBack: () -> Unit) {
    val state = remember(chat.id) { ChatState(repo, chat.id) }
    DisposableEffect(state) { onDispose { state.close() } }
    val items by state.items.collectAsState()
    var text by remember(chat.id) { mutableStateOf("") }
    var error by remember(chat.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                .statusBarsPadding().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
            Avatar(chat.photoPath, chat.title, chat.id, 36)
            Spacer(Modifier.size(10.dp))
            Text(chat.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(items, key = { _, m -> m.id }) { index, message ->
                if (index >= items.size - 5) {
                    LaunchedEffect(items.size) { state.loadMore() }
                }
                Bubble(message)
            }
        }
        error?.let { Text(it, color = Color(0xFFE57373), modifier = Modifier.padding(horizontal = 12.dp)) }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                .navigationBarsPadding().imePadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                text, { text = it }, placeholder = { Text("Сообщение") },
                modifier = Modifier.weight(1f), maxLines = 5,
            )
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    val outgoing = text.trim()
                    text = ""
                    scope.launch { error = state.send(outgoing) }
                },
            ) { Text("➤", fontSize = 24.sp) }
        }
    }
}

@Composable
private fun Bubble(message: MsgItem) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
                .background(if (message.outgoing) palette.bubbleOut else palette.bubbleIn)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (message.sender.isNotEmpty()) {
                Text(message.sender, color = palette.accent, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            if (message.photoFileId != 0) {
                val image = rememberImage(message.photoPath, 900)
                if (image != null) {
                    Image(
                        image, null, Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    Box(Modifier.size(160.dp, 120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            }
            if (message.text.isNotEmpty()) Text(message.text)
            Text(
                (if (message.pending) "⏳ " else "") + formatTime(message.date),
                fontSize = 11.sp, color = palette.subtext, modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
fun MainFlow(repo: TdRepo, palette: HgPalette, onPalette: (HgPalette) -> Unit) {
    val state by repo.auth.collectAsState()
    if (state !is AuthorizationStateReady) {
        AuthFlow(repo)
        return
    }
    var open by remember { mutableStateOf<ChatRow?>(null) }
    val current = open
    androidx.activity.compose.BackHandler(enabled = current != null) { open = null }
    if (current == null) {
        ChatListScreen(repo, palette, onPalette) { open = it }
    } else {
        ChatScreen(repo, current) { open = null }
    }
}
