# ShareAlbum / Momento 면접 설명 노트

## 한 줄 소개

Momento는 친구나 가족이 초대코드로 같은 앨범에 참여하고, 사진을 업로드한 뒤 반응과 댓글을 남길 수 있는 공유 앨범 앱입니다.

## 기술 스택

- Android Kotlin
- Jetpack Compose
- Firebase Authentication
- Cloud Firestore
- Firebase Storage
- Coil 이미지 로딩

## 파일 구조

- `MainActivity.kt`
  - 앱 진입점과 주요 화면 흐름을 담당합니다.
  - 로그인 화면, 앨범 목록, 앨범 상세, 사진 상세 다이얼로그가 들어 있습니다.

- `model/Models.kt`
  - 앱에서 쓰는 데이터 모델을 모아둔 파일입니다.
  - `Album`, `Photo`, `UserProfile`, `PhotoComment`, `SavedPhoto`가 있습니다.

- `data/FirebaseAlbumRepository.kt`
  - Firestore와 Storage에 접근하는 함수를 모아둔 파일입니다.
  - 문서 변환, 반응 토글, 댓글 추가/삭제, 사진 삭제, 저장 기록 생성 등을 담당합니다.

- `ui/CommonComponents.kt`
  - 여러 화면에서 반복해서 쓰는 UI 컴포넌트입니다.
  - 버튼, 입력창, 브랜드 헤더, 빈 화면 등이 있습니다.

- `ui/ProfileDialogs.kt`
  - 프로필 수정과 마이페이지 다이얼로그 UI입니다.

## 데이터 구조

Firestore는 대략 이렇게 사용합니다.

```text
albums/{albumId}
  title
  ownerId
  ownerNickname
  memberIds
  inviteCode
  createdAt

albums/{albumId}/photos/{photoId}
  imageUrl
  storagePath
  uploaderId
  uploaderNickname
  reactions
  commentCount
  uploadedAt

albums/{albumId}/photos/{photoId}/comments/{commentId}
  authorId
  authorNickname
  text
  createdAt

users/{uid}
  nickname
  email

users/{uid}/savedPhotos/{savedPhotoId}
  albumId
  albumTitle
  photoId
  imageUrl
  uploaderName
  savedAt

inviteCodes/{code}
  albumId
  createdBy
  createdAt
```

## 핵심 구현 설명

### 1. 로그인 상태 유지

Firebase Auth의 `AuthStateListener`를 사용합니다. 앱이 켜질 때 이미 로그인된 사용자가 있으면 바로 앨범 목록 화면으로 이동합니다.

면접 답변:
> Firebase Auth가 현재 로그인된 사용자를 기억하기 때문에, 앱 시작 시 `auth.currentUser`와 `AuthStateListener`로 로그인 여부를 확인했습니다.

### 2. 초대코드 참여

앨범을 만들 때 `inviteCodes/{code}` 문서를 같이 만듭니다. 사용자가 코드를 입력하면 이 문서를 조회해서 albumId를 찾고, 해당 앨범의 `memberIds`에 본인 uid를 추가합니다.

면접 답변:
> 앨범 전체를 검색하지 않고 초대코드 전용 컬렉션을 둬서 코드 조회를 단순하게 만들었습니다.

### 3. 권한 구분

앨범에는 `ownerId`와 `memberIds`가 있습니다.

- owner는 앨범 이름 수정, 앨범 삭제, 멤버 내보내기가 가능합니다.
- member는 앨범 조회, 사진 업로드, 댓글/반응이 가능합니다.
- 사진 삭제는 앨범 주인 또는 사진 업로더만 가능합니다.

면접 답변:
> 화면에서도 권한에 따라 버튼을 숨기고, Firestore Security Rules에서도 같은 조건을 다시 검사하도록 했습니다.

### 4. 실시간 반응과 댓글

사진 목록과 댓글 목록은 Firestore `addSnapshotListener`로 구독합니다. 그래서 다른 사용자가 사진을 올리거나 댓글을 달면 화면이 자동 갱신됩니다.

반응은 사용자가 눌렀을 때 먼저 로컬 상태를 바꾸고, 이후 Firestore 업데이트 결과가 listener로 들어와 최종 동기화됩니다.

면접 답변:
> 반응 버튼은 체감 속도가 중요해서 optimistic update 방식으로 먼저 UI를 갱신하고, Firestore listener로 서버 상태와 다시 맞췄습니다.

### 5. 댓글 수 관리

댓글을 추가하거나 삭제할 때 댓글 문서와 사진의 `commentCount`를 batch로 함께 업데이트합니다.

면접 답변:
> 댓글 문서 생성과 카운트 증가가 따로 성공하면 숫자가 틀어질 수 있어서 batch write로 묶었습니다.

### 6. 사진 저장 기록

사진 상세에서 저장 버튼을 누르면 Android DownloadManager로 파일 저장을 시작하고, 동시에 `users/{uid}/savedPhotos`에 기록을 남깁니다. 마이페이지에서는 이 기록을 보여줍니다.

면접 답변:
> 실제 파일 다운로드와 앱 안의 저장 기록은 별개라서, 다운로드 요청 후 Firestore에 사용자의 저장 히스토리를 따로 남겼습니다.

## 내가 설명하면 좋은 고민 포인트

- 왜 모델 파일을 분리했는가:
  - 화면 코드와 데이터 구조가 섞이면 유지보수가 어려워서 분리했습니다.

- 왜 Repository 파일을 만들었는가:
  - Firestore 접근 로직을 화면에서 빼서, 화면은 UI 상태에 집중하게 만들었습니다.

- 왜 Security Rules가 필요한가:
  - 앱 화면에서 버튼을 숨겨도 악의적인 요청은 가능하기 때문에 서버 쪽 규칙으로 다시 막아야 합니다.

- 왜 실시간 listener를 썼는가:
  - 공유 앨범은 여러 사용자가 동시에 쓰는 앱이라 새 사진, 댓글, 반응이 바로 보이는 경험이 중요합니다.

## 아직 개선할 점

- `MainActivity.kt`에 화면 코드가 아직 많아서 `screens/LoginScreen.kt`, `screens/AlbumListScreen.kt`, `screens/AlbumDetailScreen.kt`로 더 나눌 수 있습니다.
- AI 기능을 붙인다면 앨범 요약, 베스트 사진 추천, 댓글 추천 기능을 추가할 수 있습니다.
- 사진 업로드를 여러 장 선택으로 확장할 수 있습니다.
