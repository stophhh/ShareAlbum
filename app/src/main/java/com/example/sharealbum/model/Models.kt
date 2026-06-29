package com.example.sharealbum.model

// Firestore의 albums/{albumId} 문서와 1:1로 대응되는 앱 내부 모델입니다.
// ownerId와 memberIds를 함께 들고 있어 화면에서도 권한을 빠르게 판단할 수 있습니다.
data class Album(
    val id: String,
    val title: String,
    val ownerId: String,
    val ownerEmail: String,
    val ownerNickname: String,
    val memberIds: List<String>,
    val inviteCode: String,
    val createdAt: Long
)

// albums/{albumId}/photos/{photoId} 문서와 대응됩니다.
// reactions는 "heart" -> [uid1, uid2]처럼 반응 종류별 사용자 목록을 저장합니다.
data class Photo(
    val id: String,
    val imageUrl: String,
    val storagePath: String,
    val uploaderId: String,
    val uploaderEmail: String,
    val uploaderNickname: String,
    val reactions: Map<String, List<String>>,
    val commentCount: Long,
    val uploadedAt: Long
)

// users/{uid} 문서입니다. Auth에는 이메일만 있고 닉네임은 별도 프로필 문서로 관리합니다.
data class UserProfile(
    val uid: String,
    val nickname: String,
    val email: String
)

// photos/{photoId}/comments/{commentId} 문서입니다.
// 댓글 작성자 닉네임은 표시 속도를 위해 댓글 작성 시점의 값을 같이 저장합니다.
data class PhotoComment(
    val id: String,
    val authorId: String,
    val authorNickname: String,
    val text: String,
    val createdAt: Long
)

data class ReactionOption(
    val key: String,
    val label: String
)

data class SavedPhoto(
    val id: String,
    val albumId: String,
    val albumTitle: String,
    val photoId: String,
    val imageUrl: String,
    val uploaderName: String,
    val savedAt: Long
)

// 로그인/회원가입, 참여/생성 탭처럼 화면 상태를 안전하게 표현하기 위한 enum입니다.
enum class AuthMode { Login, SignUp }

enum class AlbumMode { Join, Create }

val reactionOptions = listOf(
    ReactionOption("heart", "좋아요"),
    ReactionOption("sparkle", "멋져요"),
    ReactionOption("smile", "추억")
)

// 업로드 당시 닉네임이 없는 예전 데이터도 깨지지 않도록 이메일, uid 순서로 fallback합니다.
fun Photo.displayUploader(): String {
    return uploaderNickname.ifBlank { uploaderEmail.ifBlank { uploaderId } }
}

fun Photo.totalReactionCount(): Int {
    return reactions.values.sumOf { it.size }
}

fun defaultNickname(email: String?): String {
    return email?.substringBefore("@")?.takeIf { it.isNotBlank() } ?: "나"
}
