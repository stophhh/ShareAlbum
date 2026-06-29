# AI Agent 구현 시작 가이드

## 추천 구조

이 프로젝트는 아래 구조로 AI Agent를 붙입니다.

```text
Android 앱
  -> Firebase Cloud Functions
  -> OpenAI API
  -> Firestore / Storage
```

Android 앱은 화면과 사용자 입력만 담당합니다. OpenAI API 키와 Firestore를 강하게 수정하는 작업은 Cloud Functions에서 처리합니다.

## 이번에 추가한 서버 파일

- `firebase.json`
  - Firebase CLI가 Firestore Rules와 Functions 위치를 알 수 있게 하는 설정입니다.

- `.firebaserc`
  - 기본 Firebase 프로젝트를 `sharealbum-3106a`로 지정합니다.

- `functions/package.json`
  - Cloud Functions 서버 의존성입니다.
  - `firebase-admin`, `firebase-functions`, `openai`, `zod`, `typescript`를 사용합니다.

- `functions/src/index.ts`
  - AI Agent 함수가 들어 있습니다.

## 만들어둔 Cloud Functions

### 1. `analyzePhoto`

사진 1장을 AI가 분석합니다.

입력:

```json
{
  "albumId": "앨범 ID",
  "photoId": "사진 ID",
  "imageUrl": "사진 다운로드 URL"
}
```

하는 일:

```text
사진 이미지 분석
-> aiCaption 생성
-> aiTags 생성
-> aiCategory 생성
-> aiLocation 생성
-> aiEmbedding 생성
-> Firestore photo 문서에 저장
```

### 2. `searchPhotos`

자연어로 사진을 검색합니다.

예시:

```text
강아지 사진만 보여줘
작년 여름 제주도 여행 사진
```

하는 일:

```text
사용자 문장을 검색 조건으로 변환
-> 검색어 embedding 생성
-> 사용자가 속한 앨범의 사진 후보 조회
-> embedding 유사도 + 태그/장소/날짜 조건으로 정렬
-> 결과 반환
```

### 3. `createAlbumFromPrompt`

자연어 요청으로 새 앨범을 만듭니다.

예시:

```text
작년 파리 갔을 때 사진만 따로 모아서 앨범 만들어줘
```

하는 일:

```text
사진 검색
-> 새 앨범 생성
-> 검색된 사진들을 새 앨범에 복사
-> 새 앨범 ID 반환
```

실제 서비스에서는 바로 만들기보다 앱에서 먼저 확인 화면을 보여주는 것이 좋습니다.

## 네가 직접 해야 하는 것

### 1. Firebase 요금제 확인

Cloud Functions와 외부 API 호출을 쓰려면 Firebase 프로젝트가 Blaze 요금제여야 할 수 있습니다.

Firebase Console에서 확인:

```text
Firebase Console
-> 프로젝트 sharealbum-3106a
-> Usage and billing
-> Blaze plan 확인
```

### 2. Firebase CLI 로그인

터미널에서 한 번만 하면 됩니다.

```bash
firebase login
```

### 3. Functions 의존성 설치

```bash
cd functions
npm install
```

### 4. OpenAI API 키 준비

OpenAI Platform에서 API 키를 만듭니다.

그 다음 Firebase Functions secret으로 저장합니다.

```bash
firebase functions:secrets:set OPENAI_API_KEY
```

명령어를 치면 API 키를 입력하라고 나옵니다.

### 5. Functions 배포

```bash
firebase deploy --only functions
```

Firestore Rules까지 같이 배포하려면:

```bash
firebase deploy --only firestore,functions
```

## 앱에서 다음에 연결할 부분

아직 Android 앱에서는 Functions 호출 코드를 연결하지 않았습니다. 다음 단계에서 앱에 버튼/검색창을 추가하면 됩니다.

필요한 앱 기능:

```text
1. 사진 업로드 완료 후 analyzePhoto 호출
2. AI 검색창 추가
3. 검색어 입력 시 searchPhotos 호출
4. "새 앨범 만들기" 확인 화면에서 createAlbumFromPrompt 호출
```

## 면접에서 설명할 말

> Android 앱에는 OpenAI API 키를 넣지 않고 Firebase Cloud Functions를 AI Agent 서버로 사용했습니다. 사진 업로드 후 Cloud Functions가 OpenAI Vision과 Embedding을 사용해 검색용 메타데이터를 생성하고, 자연어 검색이나 앨범 자동 생성 요청은 서버에서 Firestore를 안전하게 조회/수정하도록 설계했습니다.

## 주의할 점

- OpenAI API 키는 절대 Android 앱에 넣으면 안 됩니다.
- AI가 바로 앨범을 만들기 전에 사용자가 확인할 수 있는 UX를 넣는 것이 안전합니다.
- 기존 사진은 `analyzePhoto`가 한 번 실행되어야 AI 검색 대상이 됩니다.
