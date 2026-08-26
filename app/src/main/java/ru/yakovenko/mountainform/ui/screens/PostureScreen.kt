package ru.yakovenko.mountainform.ui.screens

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yakovenko.mountainform.ui.AppUiState
import ru.yakovenko.mountainform.ui.components.SectionTitle
import ru.yakovenko.mountainform.ui.formatEpochDay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PostureScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSave: (Int, String, List<String?>) -> Unit,
) {
    val context = LocalContext.current
    val latest = state.postureAssessments.firstOrNull()
    var photoSlot by remember { mutableIntStateOf(0) }
    var frontUri by remember(latest) { mutableStateOf(latest?.frontPhotoUri?.let(Uri::parse)) }
    var sideUri by remember(latest) { mutableStateOf(latest?.sidePhotoUri?.let(Uri::parse)) }
    var backUri by remember(latest) { mutableStateOf(latest?.backPhotoUri?.let(Uri::parse)) }
    var rating by remember(latest) { mutableIntStateOf(latest?.selfRating ?: 3) }
    var notes by remember(latest) { mutableStateOf(latest?.notes.orEmpty()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            when (photoSlot) {
                0 -> frontUri = uri
                1 -> sideUri = uri
                else -> backUri = uri
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Осанка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionTitle("Индивидуальная оценка", "Фото остаются на устройстве и не входят в общий отчёт") }
            item {
                Text(
                    "Сделайте снимки в спокойной привычной стойке, на уровне корпуса, без попытки специально выпрямиться. " +
                        "Приложение сохраняет наблюдение, но не ставит диагноз.",
                )
            }
            item {
                PhotoCard(
                    title = "Спереди",
                    uri = frontUri,
                    onPick = { photoSlot = 0; picker.launch(arrayOf("image/*")) },
                    onClear = { frontUri = null },
                )
            }
            item {
                PhotoCard(
                    title = "Сбоку",
                    uri = sideUri,
                    onPick = { photoSlot = 1; picker.launch(arrayOf("image/*")) },
                    onClear = { sideUri = null },
                )
            }
            item {
                PhotoCard(
                    title = "Сзади",
                    uri = backUri,
                    onPick = { photoSlot = 2; picker.launch(arrayOf("image/*")) },
                    onClear = { backUri = null },
                )
            }
            item { SectionTitle("Самооценка", "1 — выраженная сутулость, 5 — легко сохраняю нейтральную стойку") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { value ->
                        FilterChip(selected = rating == value, onClick = { rating = value }, label = { Text(value.toString()) })
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Наблюдения") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = {
                        onSave(rating, notes, listOf(frontUri?.toString(), sideUri?.toString(), backUri?.toString()))
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сохранить оценку") }
            }
            latest?.let { lastAssessment ->
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Последняя запись", fontWeight = FontWeight.Bold)
                            Text(formatEpochDay(lastAssessment.epochDay), style = MaterialTheme.typography.bodySmall)
                            Text("Самооценка: ${lastAssessment.selfRating}/5")
                            if (lastAssessment.notes.isNotBlank()) Text(lastAssessment.notes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(title: String, uri: Uri?, onPick: () -> Unit, onClear: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            uri?.let { imageUri ->
                rememberUriBitmap(imageUri)?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text(if (uri == null) "Выбрать фото" else "Заменить фото")
            }
            if (uri != null) {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("Убрать фото") }
            }
        }
    }
}

@Composable
private fun rememberUriBitmap(uri: Uri): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val maxWidth = 1000
                if (info.size.width > maxWidth) {
                    val ratio = maxWidth.toFloat() / info.size.width
                    decoder.setTargetSize(maxWidth, (info.size.height * ratio).toInt())
                }
            }.asImageBitmap()
        }.getOrNull()
    }
    return bitmap
}
