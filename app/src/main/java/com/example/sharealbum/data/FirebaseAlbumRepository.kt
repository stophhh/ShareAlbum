package com.example.sharealbum.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.sharealbum.model.Album
import com.example.sharealbum.model.AgentPhotoItem
import com.example.sharealbum.model.Photo
import com.example.sharealbum.model.PhotoComment
import com.example.sharealbum.model.SavedPhoto
import com.example.sharealbum.model.UserProfile
import com.example.sharealbum.model.defaultNickname
import com.example.sharealbum.model.displayUploader
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Firestore 문서는 타입이 느슨하므로 화면에서 쓰기 전에 Kotlin 모델로 변환합니다.
// 필수값이 없는 문서는 null을 반환해서 깨진 데이터가 UI에 나오지 않게 합니다.
fun DocumentSnapshot.toAlbum(): Album? {
    val title = getString("title") ?: return null
    val ownerId = getString("ownerId").orEmpty()
    val memberIds = get("memberIds") as? List<*> ?: emptyList<Any>()
    return Album(
        id = id,
        title = title,
        ownerId = ownerId,
        ownerEmail = getString("ownerEmail").orEmpty(),
        ownerNickname = getString("ownerNickname").orEmpty(),
        memberIds = memberIds.filterIsInstance<String>(),
        inviteCode = getString("inviteCode").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L
    )
}

fun DocumentSnapshot.toPhoto(): Photo? {
    val imageUrl = getString("imageUrl") ?: return null
    return Photo(
        id = id,
        imageUrl = imageUrl,
        thumbnailUrl = getString("thumbnailUrl").orEmpty(),
        storagePath = getString("storagePath").orEmpty(),
        thumbnailPath = getString("thumbnailPath").orEmpty(),
        uploaderId = getString("uploaderId").orEmpty(),
        uploaderEmail = getString("uploaderEmail").orEmpty(),
        uploaderNickname = getString("uploaderNickname").orEmpty(),
        reactions = readReactions(get("reactions")),
        savedBy = (get("savedBy") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        commentCount = getLong("commentCount") ?: 0L,
        uploadedAt = getLong("uploadedAt") ?: 0L
    )
}

fun DocumentSnapshot.toUserProfile(uid: String, email: String): UserProfile {
    return UserProfile(
        uid = uid,
        nickname = getString("nickname").orEmpty().ifBlank { defaultNickname(email) },
        email = getString("email").orEmpty().ifBlank { email }
    )
}

fun DocumentSnapshot.toPhotoComment(): PhotoComment? {
    val text = getString("text") ?: return null
    return PhotoComment(
        id = id,
        authorId = getString("authorId").orEmpty(),
        authorNickname = getString("authorNickname").orEmpty().ifBlank { "익명" },
        text = text,
        createdAt = getLong("createdAt") ?: 0L
    )
}

fun DocumentSnapshot.toSavedPhoto(): SavedPhoto? {
    val imageUrl = getString("imageUrl") ?: return null
    return SavedPhoto(
        id = id,
        albumId = getString("albumId").orEmpty(),
        albumTitle = getString("albumTitle").orEmpty(),
        photoId = getString("photoId").orEmpty(),
        imageUrl = imageUrl,
        uploaderName = getString("uploaderName").orEmpty(),
        savedAt = getLong("savedAt") ?: 0L
    )
}

// 사람이 직접 입력하기 쉬운 8자리 초대코드를 만듭니다.
fun createInviteCode(): String {
    return UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.getDefault())
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "-"
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

// Android DownloadManager를 사용해 시스템 다운로드 알림과 저장 처리를 맡깁니다.
fun downloadPhoto(context: Context, photo: Photo) {
    val request = DownloadManager.Request(Uri.parse(photo.imageUrl))
        .setTitle("ShareAlbum 사진")
        .setDescription("사진 저장 중")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(
            context,
            Environment.DIRECTORY_PICTURES,
            "sharealbum_${photo.id}.jpg"
        )

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

// Auth에는 이메일만 있으므로 닉네임은 users/{uid} 문서에 따로 저장합니다.
// merge 옵션을 사용해 나중에 프로필 필드가 늘어나도 기존 값을 덮어쓰지 않습니다.
fun saveUserProfile(uid: String, email: String, nickname: String) {
    val cleanNickname = nickname.trim().ifBlank { defaultNickname(email) }
    val data = hashMapOf(
        "nickname" to cleanNickname,
        "email" to email,
        "updatedAt" to System.currentTimeMillis()
    )
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(uid)
        .set(data, SetOptions.merge())
}

// "내가 저장한 사진" 기록입니다. id를 고정해 같은 사진을 여러 번 저장해도 중복되지 않게 합니다.
fun recordSavedPhoto(userId: String, album: Album, photo: Photo) {
    val db = FirebaseFirestore.getInstance()
    val data = hashMapOf(
        "albumId" to album.id,
        "albumTitle" to album.title,
        "photoId" to photo.id,
        "imageUrl" to photo.imageUrl,
        "uploaderName" to photo.displayUploader(),
        "savedAt" to System.currentTimeMillis()
    )

    db.collection("users")
        .document(userId)
        .collection("savedPhotos")
        .document("${album.id}_${photo.id}")
        .set(data, SetOptions.merge())

    db.collection("albums")
        .document(album.id)
        .collection("photos")
        .document(photo.id)
        .update("savedBy", FieldValue.arrayUnion(userId))
}

// FastAPI가 골라준 사진 URL 목록으로 새 앨범을 만듭니다.
// 실제 생성은 사용자가 화면에서 확인 버튼을 누른 뒤 실행되도록 Android에서 처리합니다.
fun createAlbumFromAgentPhotos(
    title: String,
    sourcePrompt: String,
    ownerId: String,
    ownerEmail: String,
    ownerNickname: String,
    photos: List<AgentPhotoItem>,
    onDone: (String) -> Unit,
    onError: (Exception) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val albumRef = db.collection("albums").document()
    val inviteCode = createInviteCode()
    val now = System.currentTimeMillis()
    val albumBatch = db.batch()

    albumBatch.set(albumRef, hashMapOf(
        "title" to title.trim().ifBlank { "AI가 모은 앨범" },
        "ownerId" to ownerId,
        "ownerEmail" to ownerEmail,
        "ownerNickname" to ownerNickname,
        "memberIds" to listOf(ownerId),
        "inviteCode" to inviteCode,
        "createdAt" to now,
        "createdByAgent" to true,
        "sourcePrompt" to sourcePrompt
    ))

    albumBatch.set(db.collection("inviteCodes").document(inviteCode), hashMapOf(
        "albumId" to albumRef.id,
        "createdBy" to ownerId,
        "createdAt" to now
    ))

    albumBatch.commit()
        .addOnSuccessListener {
            if (photos.isEmpty()) {
                onDone(albumRef.id)
                return@addOnSuccessListener
            }

            val photoBatch = db.batch()
            photos.forEach { photo ->
                val photoRef = albumRef.collection("photos").document()
                photoBatch.set(photoRef, hashMapOf(
                    "imageUrl" to photo.image_url,
                    "thumbnailUrl" to photo.image_url,
                    "sourcePhotoId" to photo.photo_id,
                    "uploaderId" to ownerId,
                    "uploaderEmail" to ownerEmail,
                    "uploaderNickname" to ownerNickname,
                    "aiTags" to photo.tags,
                    "reactions" to emptyMap<String, List<String>>(),
                    "savedBy" to emptyList<String>(),
                    "commentCount" to 0L,
                    "uploadedAt" to now,
                    "addedByAgent" to true
                ))
            }
            photoBatch.commit()
                .addOnSuccessListener { onDone(albumRef.id) }
                .addOnFailureListener { onError(it) }
        }
        .addOnFailureListener { onError(it) }
}

fun createAlbumFromSavedPhotos(
    title: String,
    ownerId: String,
    ownerEmail: String,
    ownerNickname: String,
    savedPhotos: List<SavedPhoto>,
    onDone: (String) -> Unit,
    onError: (Exception) -> Unit
) {
    val agentPhotos = savedPhotos.map { photo ->
        AgentPhotoItem(
            photo_id = photo.photoId,
            image_url = photo.imageUrl,
            uploader_name = photo.uploaderName,
            tags = listOf(photo.albumTitle, photo.uploaderName).filter { it.isNotBlank() }
        )
    }

    createAlbumFromAgentPhotos(
        title = title,
        sourcePrompt = "마이페이지 저장 사진으로 만든 앨범",
        ownerId = ownerId,
        ownerEmail = ownerEmail,
        ownerNickname = ownerNickname,
        photos = agentPhotos,
        onDone = onDone,
        onError = onError
    )
}

// 반응은 reactions.heart 같은 배열 필드에 uid를 넣고 빼는 방식입니다.
// arrayUnion/arrayRemove를 쓰면 동시에 여러 사용자가 눌러도 중복과 덮어쓰기 문제가 줄어듭니다.
fun toggleReaction(
    albumId: String,
    photo: Photo,
    reactionKey: String,
    userId: String,
    selected: Boolean,
    onError: (Exception) -> Unit
) {
    FirebaseFirestore.getInstance()
        .collection("albums")
        .document(albumId)
        .collection("photos")
        .document(photo.id)
        .update(
            "reactions.$reactionKey",
            if (selected) FieldValue.arrayRemove(userId) else FieldValue.arrayUnion(userId)
        )
        .addOnFailureListener { onError(it) }
}

// 댓글 생성과 사진의 commentCount 증가를 batch로 묶어 두 값이 함께 바뀌도록 합니다.
fun addComment(
    albumId: String,
    photoId: String,
    profile: UserProfile,
    text: String,
    onDone: () -> Unit,
    onError: (Exception) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val photoRef = db.collection("albums")
        .document(albumId)
        .collection("photos")
        .document(photoId)
    val commentRef = photoRef.collection("comments").document()
    val comment = hashMapOf(
        "authorId" to profile.uid,
        "authorNickname" to profile.nickname,
        "text" to text,
        "createdAt" to System.currentTimeMillis()
    )

    db.batch()
        .apply {
            set(commentRef, comment)
            update(photoRef, "commentCount", FieldValue.increment(1))
        }
        .commit()
        .addOnSuccessListener { onDone() }
        .addOnFailureListener { onError(it) }
}

// 댓글 삭제와 commentCount 감소도 batch로 묶어 목록과 카운트가 어긋나지 않게 합니다.
fun deleteComment(
    albumId: String,
    photoId: String,
    commentId: String,
    onError: (Exception) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val photoRef = db.collection("albums")
        .document(albumId)
        .collection("photos")
        .document(photoId)

    db.batch()
        .apply {
            delete(photoRef.collection("comments").document(commentId))
            update(photoRef, "commentCount", FieldValue.increment(-1))
        }
        .commit()
        .addOnFailureListener { onError(it) }
}

// 사진 삭제는 Firestore 문서를 먼저 삭제하고 Storage 파일을 이어서 삭제합니다.
// 화면에 깨진 사진 문서가 남는 상황을 먼저 막기 위한 순서입니다.
fun deletePhoto(
    albumId: String,
    photo: Photo,
    onDone: () -> Unit,
    onError: (Exception) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()

    db.collection("albums")
        .document(albumId)
        .collection("photos")
        .document(photo.id)
        .delete()
        .addOnSuccessListener {
            val paths = listOf(photo.storagePath, photo.thumbnailPath).filter { it.isNotBlank() }.distinct()
            if (paths.isEmpty()) {
                onDone()
                return@addOnSuccessListener
            }

            var completed = 0
            var failed = false
            paths.forEach { path ->
                storage.reference.child(path)
                    .delete()
                    .addOnSuccessListener {
                        completed += 1
                        if (completed == paths.size && !failed) onDone()
                    }
                    .addOnFailureListener {
                        failed = true
                        onError(it)
                    }
            }
        }
        .addOnFailureListener { onError(it) }
}

// 앨범 삭제는 사진 문서, 초대코드 문서, 앨범 문서를 batch로 함께 정리합니다.
// Storage 파일 삭제는 Firestore 삭제 성공 후 별도 요청합니다.
fun deleteAlbum(
    album: Album,
    photos: List<Photo>,
    onDone: () -> Unit,
    onError: (Exception) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val batch = db.batch()
    val albumRef = db.collection("albums").document(album.id)

    photos.forEach { photo ->
        batch.delete(albumRef.collection("photos").document(photo.id))
    }
    if (album.inviteCode.isNotBlank()) {
        batch.delete(db.collection("inviteCodes").document(album.inviteCode))
    }
    batch.delete(albumRef)

    batch.commit()
        .addOnSuccessListener {
            photos
                .flatMap { listOf(it.storagePath, it.thumbnailPath) }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { storagePath ->
                    storage.reference.child(storagePath).delete()
                }
            onDone()
        }
        .addOnFailureListener { onError(it) }
}

// Firestore에서 읽은 Map/List는 타입이 Any로 들어오기 때문에 안전하게 캐스팅합니다.
private fun readReactions(raw: Any?): Map<String, List<String>> {
    val map = raw as? Map<*, *> ?: return emptyMap()
    return map.mapNotNull { (key, value) ->
        val reactionKey = key as? String ?: return@mapNotNull null
        val users = (value as? List<*>)?.filterIsInstance<String>().orEmpty()
        reactionKey to users
    }.toMap()
}
