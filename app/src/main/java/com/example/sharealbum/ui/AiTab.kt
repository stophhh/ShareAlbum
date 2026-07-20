package com.example.sharealbum.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.sharealbum.data.FastApiRepository
import com.example.sharealbum.data.createAlbumFromAgentPhotos
import com.example.sharealbum.model.AgentPhotoItem
import com.example.sharealbum.model.Photo
import com.example.sharealbum.model.displayUploader
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun AiTabContent(
    albumId: String,
    userId: String,
    userEmail: String,
    userNickname: String,
    photos: List<Photo>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fastApiRepository = remember { FastApiRepository() }

    var prompt by remember { mutableStateOf("") }
    var commandResult by remember { mutableStateOf<String?>(null) }
    var matchedPhotos by remember { mutableStateOf(listOf<AgentPhotoItem>()) }
    var suggestedAlbumTitle by remember { mutableStateOf("") }
    var lastCommand by remember { mutableStateOf("") }
    var isFastApiWorking by remember { mutableStateOf(false) }
    var isCreatingAlbum by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LuxePanel(maxWidth = 640.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("스마트 사진 검색", style = MaterialTheme.typography.titleMedium)
                Text(
                    "예: 올해 사진 보여줘, 여름 사진으로 앨범 만들어줘",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LuxeTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = "무엇을 할까요?"
                )

                PrimaryActionButton(
                    text = if (isFastApiWorking) "분석 중" else "명령 분석",
                    loading = isFastApiWorking,
                    enabled = !isFastApiWorking,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val command = prompt.trim()
                        if (command.isBlank()) return@PrimaryActionButton

                        scope.launch {
                            isFastApiWorking = true
                            commandResult = null
                            matchedPhotos = emptyList()
                            suggestedAlbumTitle = ""
                            lastCommand = command
                            try {
                                val requestPhotos = photos.map { photo ->
                                    AgentPhotoItem(
                                        photo_id = photo.id,
                                        image_url = photo.imageUrl,
                                        tags = buildPhotoTags(photo),
                                        uploaded_at = photo.uploadedAt.toString()
                                    )
                                }
                                val result = fastApiRepository.sendAgentCommand(
                                    userId = userId,
                                    message = command,
                                    photos = requestPhotos
                                )
                                matchedPhotos = result.photos
                                suggestedAlbumTitle = result.suggested_album_title
                                commandResult = "${result.intent}: ${result.response} / 전체 ${result.photo_count}장 중 ${result.matched_photo_count}장"
                            } catch (exception: Exception) {
                                commandResult = "연결 실패: ${exception.readableMessage()}"
                            } finally {
                                isFastApiWorking = false
                            }
                        }
                    }
                )

                commandResult?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (matchedPhotos.isNotEmpty()) {
            Text("검색 결과", style = MaterialTheme.typography.titleMedium)
            if (suggestedAlbumTitle.isNotBlank()) {
                SecondaryActionButton(
                    onClick = {
                        isCreatingAlbum = true
                        createAlbumFromAgentPhotos(
                            title = suggestedAlbumTitle,
                            sourcePrompt = lastCommand,
                            ownerId = userId,
                            ownerEmail = userEmail,
                            ownerNickname = userNickname,
                            photos = matchedPhotos,
                            onDone = {
                                isCreatingAlbum = false
                                Toast.makeText(context, "'$suggestedAlbumTitle' 앨범을 만들었어요.", Toast.LENGTH_SHORT).show()
                            },
                            onError = {
                                isCreatingAlbum = false
                                Toast.makeText(context, "앨범 생성 실패: ${it.readableMessage()}", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isCreatingAlbum,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isCreatingAlbum) "앨범 생성 중" else "'$suggestedAlbumTitle' 앨범 만들기")
                }
            }
            matchedPhotos.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { photo ->
                        AgentPhotoCard(photo = photo, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun AgentPhotoCard(photo: AgentPhotoItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            LoadingImage(
                model = photo.image_url,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                photo.tags.joinToString(" · ").ifBlank { "검색 결과" },
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun Exception.readableMessage(): String {
    val reason = localizedMessage ?: message ?: "알 수 없는 오류"
    return "${this::class.java.simpleName}: $reason"
}

private fun buildPhotoTags(photo: Photo): List<String> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = photo.uploadedAt
    }

    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    val season = when (month) {
        3, 4, 5 -> "봄"
        6, 7, 8 -> "여름"
        9, 10, 11 -> "가을"
        else -> "겨울"
    }

    val half = if (month <= 6) "상반기" else "하반기"

    val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "월요일"
        Calendar.TUESDAY -> "화요일"
        Calendar.WEDNESDAY -> "수요일"
        Calendar.THURSDAY -> "목요일"
        Calendar.FRIDAY -> "금요일"
        Calendar.SATURDAY -> "토요일"
        else -> "일요일"
    }

    return listOf(
        "사진",
        "${year}년",
        year.toString(),
        "${month}월",
        "${day}일",
        "${year}년 ${month}월",
        "${month}월 ${day}일",
        season,
        half,
        dayOfWeek,
        photo.displayUploader(),
        photo.uploaderEmail.substringBefore("@")
    ).filter { it.isNotBlank() }.distinct()
}
