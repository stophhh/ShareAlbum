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
import androidx.compose.material3.Surface
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
import com.example.sharealbum.model.saveCount
import com.example.sharealbum.model.totalReactionCount
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
    var showUploaderGroups by remember { mutableStateOf(false) }
    var creatingUploaderGroup by remember { mutableStateOf<String?>(null) }

    fun Photo.toAgentPhotoItem(): AgentPhotoItem {
        return AgentPhotoItem(
            photo_id = id,
            image_url = thumbnailUrl.ifBlank { imageUrl },
            tags = buildPhotoTags(this),
            uploader_id = uploaderId,
            uploader_name = displayUploader(),
            uploaded_at = uploadedAt.toString(),
            reaction_count = totalReactionCount(),
            save_count = saveCount()
        )
    }

    fun runAgentCommand(command: String) {
        val cleanCommand = command.trim()
        if (cleanCommand.isBlank()) return

        scope.launch {
            isFastApiWorking = true
            commandResult = null
            matchedPhotos = emptyList()
            suggestedAlbumTitle = ""
            lastCommand = cleanCommand
            try {
                val requestPhotos = photos.map { photo ->
                    photo.toAgentPhotoItem()
                }
                val result = fastApiRepository.sendAgentCommand(
                    userId = userId,
                    message = cleanCommand,
                    photos = requestPhotos
                )
                matchedPhotos = result.photos
                suggestedAlbumTitle = result.suggested_album_title
                commandResult = buildAgentResultMessage(result.matched_photo_count, result.photo_count)
            } catch (exception: Exception) {
                commandResult = "연결 실패: ${exception.readableMessage()}"
            } finally {
                isFastApiWorking = false
            }
        }
    }

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
                    "예: 올해 사진 보여줘, 반응 많은 사진으로 베스트 앨범 만들어줘",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LuxeTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = "무엇을 할까요?"
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "정리 제안",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AgentSuggestionButton(
                            text = "베스트",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val command = "반응 많은 사진으로 베스트 앨범 만들어줘"
                                prompt = command
                                runAgentCommand(command)
                            }
                        )
                        AgentSuggestionButton(
                            text = "저장 많은",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val command = "저장 많이 한 사진으로 앨범 만들어줘"
                                prompt = command
                                runAgentCommand(command)
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AgentSuggestionButton(
                            text = "최근",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val command = "이번 달 사진 보여줘"
                                prompt = command
                                runAgentCommand(command)
                            }
                        )
                        AgentSuggestionButton(
                            text = "비슷한 후보",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val command = "비슷한 사진 후보 보여줘"
                                prompt = command
                                runAgentCommand(command)
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AgentSuggestionButton(
                            text = "내 사진",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val command = "내가 올린 사진만 보여줘"
                                prompt = command
                                showUploaderGroups = false
                                runAgentCommand(command)
                            }
                        )
                        AgentSuggestionButton(
                            text = "멤버별",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showUploaderGroups = !showUploaderGroups
                                commandResult = if (!showUploaderGroups) {
                                    "업로더별로 사진을 묶어 보여줄게요."
                                } else {
                                    null
                                }
                            }
                        )
                    }
                }

                PrimaryActionButton(
                    text = if (isFastApiWorking) "분석 중" else "명령 분석",
                    loading = isFastApiWorking,
                    enabled = !isFastApiWorking,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        runAgentCommand(prompt)
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

        if (showUploaderGroups) {
            val uploaderGroups = photos
                .groupBy { it.displayUploader().ifBlank { "이름 없는 멤버" } }
                .toList()
                .sortedByDescending { it.second.size }

            Text("멤버별 사진", style = MaterialTheme.typography.titleMedium)
            if (uploaderGroups.isEmpty()) {
                Text(
                    "아직 나눌 사진이 없어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uploaderGroups.forEach { (uploaderName, groupPhotos) ->
                    UploaderGroupCard(
                        uploaderName = uploaderName,
                        photoCount = groupPhotos.size,
                        samplePhotos = groupPhotos.take(3).map { it.toAgentPhotoItem() },
                        isCreating = creatingUploaderGroup == uploaderName,
                        onCreateAlbum = {
                            val title = "${uploaderName} 사진 모음"
                            creatingUploaderGroup = uploaderName
                            createAlbumFromAgentPhotos(
                                title = title,
                                sourcePrompt = "멤버별 사진 정리",
                                ownerId = userId,
                                ownerEmail = userEmail,
                                ownerNickname = userNickname,
                                photos = groupPhotos.map { it.toAgentPhotoItem() },
                                onDone = {
                                    creatingUploaderGroup = null
                                    Toast.makeText(context, "'$title' 앨범을 만들었어요.", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    creatingUploaderGroup = null
                                    Toast.makeText(context, "앨범 생성 실패: ${it.readableMessage()}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
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
private fun UploaderGroupCard(
    uploaderName: String,
    photoCount: Int,
    samplePhotos: List<AgentPhotoItem>,
    isCreating: Boolean,
    onCreateAlbum: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(uploaderName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "사진 ${photoCount}장",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SecondaryActionButton(
                    onClick = onCreateAlbum,
                    enabled = !isCreating
                ) {
                    Text(if (isCreating) "생성 중" else "앨범 만들기")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                samplePhotos.forEach { photo ->
                    LoadingImage(
                        model = photo.image_url,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }
                repeat(3 - samplePhotos.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AgentSuggestionButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
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
                buildString {
                    append(photo.tags.joinToString(" · ").ifBlank { "검색 결과" })
                    if (photo.reaction_count > 0) append(" · 반응 ${photo.reaction_count}개")
                    if (photo.save_count > 0) append(" · 저장 ${photo.save_count}회")
                },
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

private fun buildAgentResultMessage(matchedCount: Int, totalCount: Int): String {
    return if (matchedCount == 0) {
        "조건에 맞는 사진을 찾지 못했어요. 다른 표현으로 다시 검색해보세요."
    } else {
        "전체 ${totalCount}장 중 ${matchedCount}장을 찾았어요."
    }
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
