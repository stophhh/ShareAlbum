package com.example.sharealbum

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.sharealbum.data.addComment
import com.example.sharealbum.data.createInviteCode
import com.example.sharealbum.data.createAlbumWithAi
import com.example.sharealbum.data.deleteAlbum
import com.example.sharealbum.data.deleteComment
import com.example.sharealbum.data.deletePhoto
import com.example.sharealbum.data.downloadPhoto
import com.example.sharealbum.data.formatDate
import com.example.sharealbum.data.recordSavedPhoto
import com.example.sharealbum.data.requestPhotoAnalysis
import com.example.sharealbum.data.saveUserProfile
import com.example.sharealbum.data.searchPhotosWithAi
import com.example.sharealbum.data.toAlbum
import com.example.sharealbum.data.toPhoto
import com.example.sharealbum.data.toPhotoComment
import com.example.sharealbum.data.toSavedPhoto
import com.example.sharealbum.data.toUserProfile
import com.example.sharealbum.data.toggleReaction
import com.example.sharealbum.model.Album
import com.example.sharealbum.model.AlbumMode
import com.example.sharealbum.model.AiPhotoResult
import com.example.sharealbum.model.AuthMode
import com.example.sharealbum.model.Photo
import com.example.sharealbum.model.PhotoComment
import com.example.sharealbum.model.SavedPhoto
import com.example.sharealbum.model.UserProfile
import com.example.sharealbum.model.defaultNickname
import com.example.sharealbum.model.displayUploader
import com.example.sharealbum.model.reactionOptions
import com.example.sharealbum.model.totalReactionCount
import com.example.sharealbum.ui.BrandHeader
import com.example.sharealbum.ui.EmptyMessage
import com.example.sharealbum.ui.LuxePanel
import com.example.sharealbum.ui.LuxeTextField
import com.example.sharealbum.ui.MyPageScreen
import com.example.sharealbum.ui.PrimaryActionButton
import com.example.sharealbum.ui.ProfileDialog
import com.example.sharealbum.ui.SecondaryActionButton
import com.example.sharealbum.ui.SegmentedTabs
import com.example.sharealbum.ui.WelcomePanel
import com.example.sharealbum.ui.theme.ShareAlbumTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.Locale
import java.util.UUID
import java.io.ByteArrayOutputStream
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShareAlbumTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val auth = FirebaseAuth.getInstance()
                    var currentUserId by remember { mutableStateOf(auth.currentUser?.uid) }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            currentUserId = firebaseAuth.currentUser?.uid
                        }
                        auth.addAuthStateListener(listener)
                        onDispose { auth.removeAuthStateListener(listener) }
                    }

                    if (currentUserId != null) {
                        AlbumListScreen(onLogout = { auth.signOut() })
                    } else {
                        LoginScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(AuthMode.Login) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandHeader(subtitle = "친구들과 함께 채우는 사진 앨범")
            Spacer(modifier = Modifier.height(20.dp))
            WelcomePanel(
                title = "우리만 보는 순간들",
                subtitle = "초대코드로 앨범을 공유하고, 사진마다 반응과 코멘트를 남겨보세요."
            )
            Spacer(modifier = Modifier.height(20.dp))

            SegmentedTabs(
                selected = mode.name,
                firstKey = AuthMode.Login.name,
                firstLabel = "로그인",
                secondKey = AuthMode.SignUp.name,
                secondLabel = "회원가입",
                onSelected = { mode = AuthMode.valueOf(it) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            LuxeTextField(
                value = email,
                onValueChange = { email = it },
                label = "이메일"
            )

            Spacer(modifier = Modifier.height(12.dp))

            LuxeTextField(
                value = password,
                onValueChange = { password = it },
                label = "비밀번호",
                isPassword = true
            )

            Spacer(modifier = Modifier.height(18.dp))

            PrimaryActionButton(
                text = if (isLoading) "처리 중..." else if (mode == AuthMode.Login) "로그인" else "가입하기",
                enabled = !isLoading,
                loading = isLoading,
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "이메일과 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                        return@PrimaryActionButton
                    }
                    isLoading = true
                    val request = if (mode == AuthMode.Login) {
                        auth.signInWithEmailAndPassword(email.trim(), password)
                    } else {
                        auth.createUserWithEmailAndPassword(email.trim(), password)
                    }
                    request.addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful && mode == AuthMode.SignUp) {
                            val createdUser = auth.currentUser
                            if (createdUser != null) {
                                saveUserProfile(
                                    uid = createdUser.uid,
                                    email = createdUser.email.orEmpty(),
                                    nickname = createdUser.email?.substringBefore("@").orEmpty()
                                )
                            }
                        }
                        val successText = if (mode == AuthMode.Login) "로그인 성공" else "회원가입 성공"
                        val failText = if (mode == AuthMode.Login) "로그인 실패" else "회원가입 실패"
                        val message = if (task.isSuccessful) successText else "$failText: ${task.exception?.localizedMessage}"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
fun AlbumListScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser ?: return

    var albumTitle by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var albums by remember { mutableStateOf(listOf<Album>()) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(AlbumMode.Join) }
    var profile by remember { mutableStateOf(UserProfile(user.uid, defaultNickname(user.email), user.email.orEmpty())) }
    var showProfile by remember { mutableStateOf(false) }
    var showMyPage by remember { mutableStateOf(false) }
    var savedPhotos by remember { mutableStateOf(listOf<SavedPhoto>()) }

    if (showMyPage) {
        MyPageScreen(
            profile = profile,
            albums = albums,
            savedPhotos = savedPhotos,
            onEditProfile = {
                showMyPage = false
                showProfile = true
            },
            onBack = { showMyPage = false }
        )
        return
    }

    if (selectedAlbum != null) {
        AlbumDetailScreen(
            album = selectedAlbum!!,
            profile = profile,
            onAlbumChanged = { selectedAlbum = it },
            onAlbumDeleted = { selectedAlbum = null },
            onBack = { selectedAlbum = null }
        )
        return
    }

    if (showProfile) {
        ProfileDialog(
            profile = profile,
            onDismiss = { showProfile = false },
            onSave = { nickname ->
                saveUserProfile(user.uid, user.email.orEmpty(), nickname)
                showProfile = false
                Toast.makeText(context, "닉네임을 저장했습니다.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    DisposableEffect(user.uid) {
        saveUserProfile(user.uid, user.email.orEmpty(), profile.nickname)

        // 프로필, 저장 사진, 앨범 목록은 Firestore listener로 실시간 구독합니다.
        // 다른 기기에서 닉네임/앨범이 바뀌어도 화면이 자동으로 갱신됩니다.
        val userRegistration = db.collection("users")
            .document(user.uid)
            .addSnapshotListener { snapshot, _ ->
                val nextProfile = snapshot?.toUserProfile(user.uid, user.email.orEmpty())
                if (nextProfile != null) profile = nextProfile
            }

        val savedRegistration = db.collection("users")
            .document(user.uid)
            .collection("savedPhotos")
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, _ ->
                savedPhotos = snapshot?.documents.orEmpty().mapNotNull { it.toSavedPhoto() }
            }

        val registration = db.collection("albums")
            .whereArrayContains("memberIds", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "앨범을 불러오지 못했습니다: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                albums = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.toAlbum() }
                    .sortedByDescending { it.createdAt }
            }
        onDispose {
            userRegistration.remove()
            savedRegistration.remove()
            registration.remove()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("m", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            "momento",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            onClick = { showMyPage = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                "마이",
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Surface(
                            onClick = onLogout,
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "로그아웃",
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                WelcomePanel(
                    title = "${profile.nickname}님의 앨범",
                    subtitle = "함께 찍은 순간을 모으고, 반응과 코멘트로 다시 꺼내보세요."
                )
            }
        }

        item {
            SegmentedTabs(
                selected = mode.name,
                firstKey = AlbumMode.Join.name,
                firstLabel = "코드로 참여",
                secondKey = AlbumMode.Create.name,
                secondLabel = "앨범 만들기",
                onSelected = { mode = AlbumMode.valueOf(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            )
        }

        if (mode == AlbumMode.Create) {
            item {
                LuxePanel {
                    LuxeTextField(
                        value = albumTitle,
                        onValueChange = { albumTitle = it },
                        label = "앨범 이름"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryActionButton(
                        text = if (isCreating) "만드는 중..." else "새 앨범 만들기",
                        enabled = !isCreating,
                        loading = isCreating,
                        onClick = {
                            val title = albumTitle.trim()
                            if (title.isBlank()) return@PrimaryActionButton

                            isCreating = true
                            val albumRef = db.collection("albums").document()
                            val code = createInviteCode()
                            val album = hashMapOf(
                                "title" to title,
                                "ownerId" to user.uid,
                                "ownerEmail" to user.email.orEmpty(),
                                "ownerNickname" to profile.nickname,
                                "memberIds" to listOf(user.uid),
                                "inviteCode" to code,
                                "createdAt" to System.currentTimeMillis()
                            )
                            val invite = hashMapOf(
                                "albumId" to albumRef.id,
                                "createdBy" to user.uid,
                                "createdAt" to System.currentTimeMillis()
                            )

                            db.batch()
                                .apply {
                                    set(albumRef, album)
                                    set(db.collection("inviteCodes").document(code), invite)
                                }
                                .commit()
                                .addOnSuccessListener {
                                    albumTitle = ""
                                    Toast.makeText(context, "앨범을 만들었습니다.", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "앨범 생성 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                                .addOnCompleteListener {
                                    isCreating = false
                                }
                        }
                    )
                }
            }
        } else {
            item {
                LuxePanel {
                    LuxeTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it.uppercase(Locale.getDefault()) },
                        label = "초대 코드 입력",
                        centerText = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryActionButton(
                        text = if (isJoining) "참여 중..." else "참여하기",
                        enabled = !isJoining,
                        loading = isJoining,
                        onClick = {
                            val code = inviteCode.trim()
                            if (code.isBlank()) return@PrimaryActionButton

                            isJoining = true
                            db.collection("inviteCodes")
                                .document(code)
                                .get()
                                .addOnSuccessListener { inviteDoc ->
                                    val albumId = inviteDoc.getString("albumId")
                                    if (albumId == null) {
                                        Toast.makeText(context, "초대코드를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val albumRef = db.collection("albums").document(albumId)
                                        albumRef.get()
                                            .addOnSuccessListener { albumDoc ->
                                                val memberIds = albumDoc.get("memberIds") as? List<*> ?: emptyList<Any>()
                                                if (user.uid in memberIds.filterIsInstance<String>()) {
                                                    inviteCode = ""
                                                    Toast.makeText(context, "이미 참여 중인 앨범입니다.", Toast.LENGTH_SHORT).show()
                                                    return@addOnSuccessListener
                                                }

                                                albumRef
                                                    .update("memberIds", FieldValue.arrayUnion(user.uid))
                                                    .addOnSuccessListener {
                                                        inviteCode = ""
                                                        Toast.makeText(context, "앨범에 참여했습니다.", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .addOnFailureListener {
                                                        Toast.makeText(context, "참여 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(context, "참여 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "참여 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                                .addOnCompleteListener {
                                    isJoining = false
                                }
                        }
                    )
                }
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (albums.isEmpty()) {
            item {
                EmptyMessage("아직 앨범이 없습니다.\n새 앨범을 만들거나 초대코드로 참여해보세요.")
            }
        } else {
            items(albums, key = { it.id }) { album ->
                Card(
                    onClick = { selectedAlbum = album },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("M", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(album.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${album.memberIds.size}명 · ${if (album.ownerId == user.uid) "주인" else "멤버"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumBottomBar(
    selectedTab: String,
    isUploading: Boolean,
    onPhotos: () -> Unit,
    onSearch: () -> Unit,
    onMembers: () -> Unit,
    onInvite: () -> Unit,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 430.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BottomBarItem(label = "사진", icon = "▦", selected = selectedTab == "photos", onClick = onPhotos)
            BottomBarItem(label = "AI", icon = "✦", selected = selectedTab == "ai", onClick = onSearch)

            Surface(
                onClick = onUpload,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            "+",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }

            BottomBarItem(label = "멤버", icon = "◌", selected = selectedTab == "members", onClick = onMembers)
            BottomBarItem(label = "초대", icon = "↗", selected = false, onClick = onInvite)
        }
    }
}

@Composable
fun MembersTabContent(
    album: Album,
    currentUserId: String,
    onKick: (String) -> Unit
) {
    LuxePanel(maxWidth = 640.dp) {
        Text("멤버", style = MaterialTheme.typography.titleMedium)
        Text(
            "${album.memberIds.size}명이 함께 보고 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        album.memberIds.forEach { memberId ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (memberId == album.ownerId) "★" else "·",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                if (memberId == album.ownerId) "앨범 주인" else "멤버",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                memberId.take(12),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (currentUserId == album.ownerId && memberId != album.ownerId) {
                        TextButton(onClick = { onKick(memberId) }) {
                            Text("내보내기")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                icon,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun AlbumDetailScreen(
    album: Album,
    profile: UserProfile,
    onAlbumChanged: (Album) -> Unit,
    onAlbumDeleted: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val user = auth.currentUser ?: return
    val isOwner = album.ownerId == user.uid

    var currentAlbum by remember(album.id) { mutableStateOf(album) }
    var editTitle by remember(album.id) { mutableStateOf(album.title) }
    var photos by remember(album.id) { mutableStateOf(listOf<Photo>()) }
    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadTotal by remember { mutableStateOf(0) }
    var uploadDone by remember { mutableStateOf(0) }
    var showMembers by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    var aiResults by remember { mutableStateOf(listOf<AiPhotoResult>()) }
    var isAiWorking by remember { mutableStateOf(false) }
    var showEditTitle by remember { mutableStateOf(false) }
    var selectedTab by remember(album.id) { mutableStateOf("photos") }
    var selectionMode by remember(album.id) { mutableStateOf(false) }
    var selectedPhotoIds by remember(album.id) { mutableStateOf(setOf<String>()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isUploading = true
        uploadTotal = uris.size
        uploadDone = 0

        uris.forEach { uri ->
            uploadPhoto(
                context = context,
                albumId = album.id,
                uri = uri,
                storage = storage,
                db = db,
                userId = user.uid,
                userEmail = user.email.orEmpty(),
                nickname = profile.nickname,
                onUploaded = { photoId, imageUrl ->
                    requestPhotoAnalysis(
                        albumId = album.id,
                        photoId = photoId,
                        imageUrl = imageUrl,
                        onError = {
                            Toast.makeText(context, "AI 분석 실패: ${it.readableMessage()}", Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onError = {
                    Toast.makeText(context, "사진 업로드 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                },
                onComplete = {
                    uploadDone += 1
                    if (uploadDone >= uploadTotal) {
                        isUploading = false
                        Toast.makeText(context, "${uploadDone}장 업로드 완료", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    DisposableEffect(album.id) {
        // 앨범 제목/멤버 정보와 사진 목록을 따로 구독합니다.
        // 사진 반응이나 댓글 수가 바뀌면 photos listener가 다시 호출되어 그리드도 갱신됩니다.
        val albumRegistration = db.collection("albums")
            .document(album.id)
            .addSnapshotListener { snapshot, _ ->
                val updatedAlbum = snapshot?.toAlbum()
                if (updatedAlbum != null) {
                    currentAlbum = updatedAlbum
                    editTitle = updatedAlbum.title
                    onAlbumChanged(updatedAlbum)
                }
            }

        val photoRegistration = db.collection("albums")
            .document(album.id)
            .collection("photos")
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "사진을 불러오지 못했습니다: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                val loadedPhotos = snapshot?.documents.orEmpty().mapNotNull { it.toPhoto() }
                photos = loadedPhotos
                selectedPhotoIds = selectedPhotoIds.filter { selectedId ->
                    loadedPhotos.any { it.id == selectedId }
                }.toSet()
                if (selectedPhotoIds.isEmpty()) selectionMode = false

                // 상세창이 열린 상태에서도 최신 Photo 객체로 교체합니다.
                // 이 처리가 있어야 반응을 눌렀을 때 상세창의 숫자도 바로 따라옵니다.
                selectedPhoto = selectedPhoto?.let { selected ->
                    loadedPhotos.firstOrNull { it.id == selected.id }
                }
            }

        onDispose {
            albumRegistration.remove()
            photoRegistration.remove()
        }
    }

    if (showMembers) {
        MembersDialog(
            album = currentAlbum,
            currentUserId = user.uid,
            onDismiss = { showMembers = false },
            onKick = { memberId ->
                db.collection("albums")
                    .document(album.id)
                    .update("memberIds", FieldValue.arrayRemove(memberId))
                    .addOnSuccessListener {
                        Toast.makeText(context, "멤버를 내보냈습니다.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "멤버 내보내기 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
        )
    }

    selectedPhoto?.let { photo ->
        PhotoDialog(
            albumId = album.id,
            photo = photo,
            profile = profile,
            currentUserId = user.uid,
            canDelete = isOwner || photo.uploaderId == user.uid,
            onDismiss = { selectedPhoto = null },
            onDownload = {
                downloadPhoto(context, photo)
                // 실제 파일 저장과 별개로 마이페이지에서 볼 수 있는 저장 기록을 남깁니다.
                recordSavedPhoto(user.uid, currentAlbum, photo)
                Toast.makeText(context, "사진 저장을 시작했습니다.", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                deletePhoto(
                    albumId = album.id,
                    photo = photo,
                    onDone = {
                        selectedPhoto = null
                        Toast.makeText(context, "사진을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onError = {
                        Toast.makeText(context, "사진 삭제 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            LuxePanel(maxWidth = 640.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text("← 목록")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentAlbum.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            "${currentAlbum.memberIds.size}명 · 사진 ${photos.size}장 · ${if (isOwner) "앨범 주인" else "앨범 멤버"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOwner) {
                        Surface(
                            onClick = { showEditTitle = !showEditTitle },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                "✎",
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        if (isOwner && showEditTitle) {
            item {
                LuxePanel(maxWidth = 640.dp) {
                    LuxeTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = "앨범 이름 수정"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryActionButton(
                            text = "수정",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val title = editTitle.trim()
                                if (title.isBlank()) return@PrimaryActionButton

                                db.collection("albums")
                                    .document(album.id)
                                    .update("title", title)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "앨범 이름을 수정했습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, "수정 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        )
                        SecondaryActionButton(
                            onClick = {
                                deleteAlbum(
                                    album = currentAlbum,
                                    photos = photos,
                                    onDone = {
                                        Toast.makeText(context, "앨범을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                                        onAlbumDeleted()
                                    },
                                    onError = {
                                        Toast.makeText(context, "앨범 삭제 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("삭제")
                        }
                    }
                }
            }
        }

        if (selectedTab == "ai") {
        item {
            LuxePanel(maxWidth = 640.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI 사진 검색", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "예: 강아지 사진, 작년 제주 여행",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "AI",
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                LuxeTextField(
                    value = aiPrompt,
                    onValueChange = { aiPrompt = it },
                    label = "무엇을 찾을까요?"
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton(
                        text = if (isAiWorking) "검색 중" else "검색",
                        loading = isAiWorking,
                        enabled = !isAiWorking,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val prompt = aiPrompt.trim()
                            if (prompt.isBlank()) return@PrimaryActionButton
                            isAiWorking = true
                            searchPhotosWithAi(
                                query = prompt,
                                albumId = album.id,
                                onSuccess = {
                                    aiResults = it
                                    isAiWorking = false
                                    Toast.makeText(context, "${it.size}장 찾았어요.", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isAiWorking = false
                                    Toast.makeText(context, "AI 검색 실패: ${it.readableMessage()}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                    SecondaryActionButton(
                        onClick = {
                            val prompt = aiPrompt.trim()
                            if (prompt.isBlank()) return@SecondaryActionButton
                            isAiWorking = true
                            createAlbumWithAi(
                                prompt = prompt,
                                onSuccess = { _, count ->
                                    isAiWorking = false
                                    Toast.makeText(context, "AI 앨범을 만들었어요. 사진 ${count}장", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isAiWorking = false
                                    Toast.makeText(context, "AI 앨범 생성 실패: ${it.readableMessage()}", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("앨범 생성")
                    }
                }
            }
        }

        if (aiResults.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                ) {
                    Text("AI 검색 결과", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    aiResults.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { result ->
                                AiResultCard(result = result, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
        }

        if (selectedTab == "members") {
            item {
                MembersTabContent(
                    album = currentAlbum,
                    currentUserId = user.uid,
                    onKick = { memberId ->
                        db.collection("albums")
                            .document(album.id)
                            .update("memberIds", FieldValue.arrayRemove(memberId))
                            .addOnSuccessListener {
                                Toast.makeText(context, "멤버를 내보냈습니다.", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "멤버 내보내기 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                    }
                )
            }
        }

        if (selectedTab == "photos") {
        if (photos.isNotEmpty()) {
            item {
                BulkPhotoActionBar(
                    selectionMode = selectionMode,
                    selectedCount = selectedPhotoIds.size,
                    onStartSelection = { selectionMode = true },
                    onCancel = {
                        selectionMode = false
                        selectedPhotoIds = emptySet()
                    },
                    onSave = {
                        val selectedPhotos = photos.filter { it.id in selectedPhotoIds }
                        selectedPhotos.forEach { photo ->
                            downloadPhoto(context, photo)
                            recordSavedPhoto(user.uid, currentAlbum, photo)
                        }
                        Toast.makeText(context, "${selectedPhotos.size}장 저장을 시작했습니다.", Toast.LENGTH_SHORT).show()
                        selectionMode = false
                        selectedPhotoIds = emptySet()
                    },
                    onDelete = {
                        val selectedPhotos = photos.filter { it.id in selectedPhotoIds }
                        val deletablePhotos = selectedPhotos.filter { isOwner || it.uploaderId == user.uid }
                        if (deletablePhotos.isEmpty()) {
                            Toast.makeText(context, "삭제할 수 있는 사진이 없습니다.", Toast.LENGTH_SHORT).show()
                            return@BulkPhotoActionBar
                        }

                        var doneCount = 0
                        var failed = false
                        deletablePhotos.forEach { photo ->
                            deletePhoto(
                                albumId = album.id,
                                photo = photo,
                                onDone = {
                                    doneCount += 1
                                    if (doneCount == deletablePhotos.size && !failed) {
                                        val skipped = selectedPhotos.size - deletablePhotos.size
                                        val message = if (skipped > 0) {
                                            "${doneCount}장 삭제했습니다. 권한 없는 ${skipped}장은 제외했어요."
                                        } else {
                                            "${doneCount}장 삭제했습니다."
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onError = {
                                    failed = true
                                    Toast.makeText(context, "일부 사진 삭제 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        selectionMode = false
                        selectedPhotoIds = emptySet()
                    }
                )
            }
        }

        item {
            Surface(
                onClick = { if (!isUploading) launcher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 18.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("${uploadDone}/${uploadTotal}장 업로드 중...", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("+ 사진 여러 장 추가", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "한 번에 선택하고 바로 앨범에 올릴 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (photos.isEmpty()) {
            item {
                EmptyMessage("아직 사진이 없습니다.\n첫 사진을 올려 앨범을 채워보세요.")
            }
        } else {
            // LazyColumn 안에서 세 장씩 묶어 3열 그리드를 만듭니다.
            // 버전 차이가 있는 LazyVerticalGrid span API 대신 안정적인 Row 방식을 사용했습니다.
            items(photos.chunked(3)) { rowPhotos ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    rowPhotos.forEach { photo ->
                        PhotoGridCard(
                            photo = photo,
                            selected = photo.id in selectedPhotoIds,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    selectedPhotoIds = if (photo.id in selectedPhotoIds) {
                                        selectedPhotoIds - photo.id
                                    } else {
                                        selectedPhotoIds + photo.id
                                    }
                                } else {
                                    selectedPhoto = photo
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowPhotos.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        }
        }

        AlbumBottomBar(
            selectedTab = selectedTab,
            isUploading = isUploading,
            onPhotos = { selectedTab = "photos" },
            onSearch = { selectedTab = "ai" },
            onMembers = { selectedTab = "members" },
            onInvite = {
                clipboard.setText(AnnotatedString(currentAlbum.inviteCode))
                Toast.makeText(context, "초대코드를 복사했습니다.", Toast.LENGTH_SHORT).show()
            },
            onUpload = { if (!isUploading) launcher.launch("image/*") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun MembersDialog(
    album: Album,
    currentUserId: String,
    onDismiss: () -> Unit,
    onKick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("멤버 목록") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                album.memberIds.forEach { memberId ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (memberId == album.ownerId) "앨범 주인" else "멤버",
                                fontWeight = FontWeight.Bold
                            )
                            Text(memberId, style = MaterialTheme.typography.bodySmall)
                        }
                        if (currentUserId == album.ownerId && memberId != album.ownerId) {
                            TextButton(onClick = { onKick(memberId) }) {
                                Text("내보내기")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
fun BulkPhotoActionBar(
    selectionMode: Boolean,
    selectedCount: Int,
    onStartSelection: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    LuxePanel(maxWidth = 640.dp) {
        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${selectedCount}장 선택",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onCancel) {
                    Text("취소")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton(
                    onClick = onSave,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("저장")
                }
                SecondaryActionButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("삭제")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("사진 관리", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "여러 장을 선택해서 저장하거나 삭제할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onStartSelection) {
                    Text("선택")
                }
            }
        }
    }
}

@Composable
fun LoadingImage(
    model: Any?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "이미지",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
fun PhotoCard(photo: Photo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            LoadingImage(
                model = photo.thumbnailUrl.ifBlank { photo.imageUrl },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        photo.displayUploader(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${formatDate(photo.uploadedAt)} · ${photo.totalReactionCount()}개 반응 · 댓글 ${photo.commentCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "보기",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun PhotoGridCard(
    photo: Photo,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            LoadingImage(
                model = photo.thumbnailUrl.ifBlank { photo.imageUrl },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Text(
                    "♡ ${photo.totalReactionCount()} · ${photo.commentCount}",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (selectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp),
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        if (selected) "✓" else "",
                        modifier = Modifier.size(24.dp).padding(top = 2.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun AiResultCard(result: AiPhotoResult, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            LoadingImage(
                model = result.imageUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                result.aiCaption.ifBlank { result.albumTitle.ifBlank { "AI 검색 결과" } },
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                listOf(result.aiCategory, result.aiLocation).filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PhotoDialog(
    albumId: String,
    photo: Photo,
    profile: UserProfile,
    currentUserId: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    var displayPhoto by remember(photo.id) { mutableStateOf(photo) }
    var comments by remember(photo.id) { mutableStateOf(listOf<PhotoComment>()) }
    var commentText by remember(photo.id) { mutableStateOf("") }

    LaunchedEffect(photo) {
        displayPhoto = photo
    }

    DisposableEffect(albumId, photo.id) {
        val registration = db.collection("albums")
            .document(albumId)
            .collection("photos")
            .document(photo.id)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "댓글을 불러오지 못했습니다: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                comments = snapshot?.documents.orEmpty().mapNotNull { it.toPhotoComment() }
            }
        onDispose { registration.remove() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text("사진 이야기", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${displayPhoto.displayUploader()} · ${formatDate(displayPhoto.uploadedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LoadingImage(
                        model = displayPhoto.imageUrl,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                item {
                    ReactionBar(
                        photo = displayPhoto,
                        currentUserId = currentUserId,
                        onToggle = { reactionKey, selected ->
                            // Firestore 응답을 기다리지 않고 먼저 로컬 상태를 바꿉니다.
                            // 사용자는 즉시 반응이 바뀐 것처럼 느끼고, 이후 listener가 서버 상태와 동기화합니다.
                            val currentUsers = displayPhoto.reactions[reactionKey].orEmpty()
                            val nextUsers = if (selected) {
                                currentUsers.filterNot { it == currentUserId }
                            } else {
                                (currentUsers + currentUserId).distinct()
                            }
                            displayPhoto = displayPhoto.copy(
                                reactions = displayPhoto.reactions + (reactionKey to nextUsers)
                            )
                            toggleReaction(
                                albumId = albumId,
                                photo = displayPhoto,
                                reactionKey = reactionKey,
                                userId = currentUserId,
                                selected = selected,
                                onError = {
                                    Toast.makeText(context, "반응 저장 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }

                if (comments.isEmpty()) {
                    item {
                        Text(
                            "아직 댓글이 없습니다. 첫 코멘트를 남겨보세요.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            canDelete = canDelete || comment.authorId == currentUserId,
                            onDelete = {
                                deleteComment(
                                    albumId = albumId,
                                    photoId = photo.id,
                                    commentId = comment.id,
                                    onError = {
                                        Toast.makeText(context, "댓글 삭제 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            label = { Text("간단한 코멘트") },
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                                focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val text = commentText.trim()
                                if (text.isBlank()) return@Button
                                addComment(
                                    albumId = albumId,
                                    photoId = photo.id,
                                    profile = profile,
                                    text = text,
                                    onDone = { commentText = "" },
                                    onError = {
                                        Toast.makeText(context, "댓글 저장 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text("등록")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("저장")
            }
        },
        dismissButton = {
            Row {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("삭제")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("닫기")
                }
            }
        }
    )
}

private fun uploadPhoto(
    context: Context,
    albumId: String,
    uri: Uri,
    storage: FirebaseStorage,
    db: FirebaseFirestore,
    userId: String,
    userEmail: String,
    nickname: String,
    onUploaded: (photoId: String, imageUrl: String) -> Unit,
    onError: (Exception) -> Unit,
    onComplete: () -> Unit
) {
    val storagePath = "albums/$albumId/${UUID.randomUUID()}.jpg"
    val thumbnailPath = storagePath.replace("albums/$albumId/", "albums/$albumId/thumbs/")
    val ref = storage.reference.child(storagePath)
    val thumbRef = storage.reference.child(thumbnailPath)

    ref.putFile(uri)
        .continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("업로드 실패")
            ref.downloadUrl
        }
        .addOnSuccessListener { downloadUrl ->
            val originalUrl = downloadUrl.toString()
            val thumbnailBytes = createThumbnailBytes(context, uri)

            fun savePhoto(thumbnailUrl: String) {
                val photoData = hashMapOf(
                    "imageUrl" to originalUrl,
                    "thumbnailUrl" to thumbnailUrl,
                    "storagePath" to storagePath,
                    "thumbnailPath" to thumbnailPath,
                    "uploaderId" to userId,
                    "uploaderEmail" to userEmail,
                    "uploaderNickname" to nickname,
                    "reactions" to emptyMap<String, List<String>>(),
                    "commentCount" to 0L,
                    "uploadedAt" to System.currentTimeMillis(),
                    "aiStatus" to "pending"
                )

                db.collection("albums")
                    .document(albumId)
                    .collection("photos")
                    .add(photoData)
                    .addOnSuccessListener { photoDoc ->
                        onUploaded(photoDoc.id, originalUrl)
                    }
                    .addOnFailureListener { onError(it) }
                    .addOnCompleteListener { onComplete() }
            }

            if (thumbnailBytes == null) {
                savePhoto(originalUrl)
            } else {
                thumbRef.putBytes(thumbnailBytes)
                    .continueWithTask { task ->
                        if (!task.isSuccessful) throw task.exception ?: IllegalStateException("썸네일 업로드 실패")
                        thumbRef.downloadUrl
                    }
                    .addOnSuccessListener { thumbUrl ->
                        savePhoto(thumbUrl.toString())
                    }
                    .addOnFailureListener {
                        savePhoto(originalUrl)
                    }
                }
        }
        .addOnFailureListener {
            onError(it)
            onComplete()
        }
}

private fun createThumbnailBytes(context: Context, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }

    val maxSize = 520
    val longestSide = max(bounds.outWidth, bounds.outHeight)
    if (longestSide <= 0) return null

    val sampleSize = max(1, longestSide / maxSize)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: return null

    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 78, output)
    bitmap.recycle()
    return output.toByteArray()
}

private fun Exception.readableMessage(): String {
    return localizedMessage ?: message ?: this::class.java.simpleName
}

@Composable
private fun ReactionBar(
    photo: Photo,
    currentUserId: String,
    onToggle: (String, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        reactionOptions.forEach { option ->
            val users = photo.reactions[option.key].orEmpty()
            val selected = currentUserId in users
            Surface(
                onClick = { onToggle(option.key, selected) },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "${option.label} ${users.size}",
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun CommentRow(comment: PhotoComment, canDelete: Boolean, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.48f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(comment.authorNickname, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDate(comment.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (canDelete) {
                        TextButton(onClick = onDelete) {
                            Text("삭제")
                        }
                    }
                }
            }
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
