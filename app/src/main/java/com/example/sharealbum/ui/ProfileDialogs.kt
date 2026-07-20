package com.example.sharealbum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.sharealbum.data.formatDate
import com.example.sharealbum.model.Album
import com.example.sharealbum.model.SavedPhoto
import com.example.sharealbum.model.UserProfile

@Composable
fun ProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var nickname by remember(profile.uid) { mutableStateOf(profile.nickname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("닉네임 설정") },
        text = {
            Column {
                Text(
                    "앨범과 댓글에 표시될 이름이에요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                LuxeTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = "닉네임"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val next = nickname.trim()
                    if (next.isNotBlank()) onSave(next)
                }
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
fun MyPageDialog(
    profile: UserProfile,
    albums: List<Album>,
    savedPhotos: List<SavedPhoto>,
    onEditProfile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        title = {
            Column {
                Text("마이페이지", style = MaterialTheme.typography.headlineMedium)
                Text(
                    profile.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("참여 앨범", "${albums.size}")
                        StatCard("저장 사진", "${savedPhotos.size}")
                    }
                }

                item {
                    Text("저장한 사진", style = MaterialTheme.typography.titleMedium)
                }

                if (savedPhotos.isEmpty()) {
                    item {
                        Text(
                            "아직 저장한 사진이 없습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(savedPhotos, key = { it.id }) { photo ->
                        SavedPhotoRow(photo)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEditProfile) {
                Text("닉네임 수정")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
fun MyPageScreen(
    profile: UserProfile,
    albums: List<Album>,
    savedPhotos: List<SavedPhoto>,
    onEditProfile: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text("돌아가기")
                    }
                    TextButton(onClick = onEditProfile) {
                        Text("닉네임 수정")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("마이페이지", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            profile.nickname,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            profile.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(onClick = onEditProfile) {
                            Text("프로필 수정")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard("참여 앨범", "${albums.size}")
                StatCard("저장 사진", "${savedPhotos.size}")
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Text("내 앨범", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (albums.isEmpty()) {
                    Text(
                        "아직 참여한 앨범이 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    albums.take(5).forEach { album ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    album.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${album.memberIds.size}명 · ${if (album.ownerId == profile.uid) "앨범 주인" else "멤버"} · ${formatDate(album.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (albums.size > 5) {
                        Text(
                            "외 ${albums.size - 5}개 앨범에 더 참여 중입니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Text("저장한 사진", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (savedPhotos.isEmpty()) {
                    Text(
                        "아직 저장한 사진이 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(savedPhotos, key = { it.id }) { photo ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                SavedPhotoRow(photo)
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: String) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondary
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavedPhotoRow(photo: SavedPhoto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = photo.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(photo.albumTitle.ifBlank { "앨범" }, fontWeight = FontWeight.SemiBold)
                Text(
                    "${photo.uploaderName.ifBlank { "업로더" }} · ${formatDate(photo.savedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
