import * as admin from "firebase-admin";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import {defineSecret} from "firebase-functions/params";
import OpenAI from "openai";
import {z} from "zod";
import {randomUUID} from "crypto";

admin.initializeApp();

const db = admin.firestore();
const openAiApiKey = defineSecret("OPENAI_API_KEY");

const REGION = "asia-northeast3";
const CHAT_MODEL = "gpt-4o-mini";
const EMBEDDING_MODEL = "text-embedding-3-small";

const AnalyzePhotoSchema = z.object({
  albumId: z.string().min(1),
  photoId: z.string().min(1),
  imageUrl: z.string().url()
});

const SearchPhotosSchema = z.object({
  query: z.string().min(1),
  albumId: z.string().optional()
});

const CreateAlbumFromPromptSchema = z.object({
  prompt: z.string().min(1),
  title: z.string().optional()
});

type PhotoCandidate = {
  albumId: string;
  albumTitle: string;
  photoId: string;
  imageUrl: string;
  aiCaption: string;
  aiTags: string[];
  aiCategory: string;
  aiLocation: string;
  aiEmbedding: number[];
  uploadedAt: number;
  score?: number;
};

/**
 * 사진 1장을 AI가 분석해서 Firestore photo 문서에 검색용 메타데이터를 저장합니다.
 *
 * 앱에서 사진 업로드가 끝난 뒤 이 함수를 호출하면 됩니다.
 * 저장되는 값:
 * - aiCaption: 사진 설명
 * - aiTags: 검색용 태그
 * - aiCategory: 여행/음식/반려동물 같은 대표 카테고리
 * - aiSearchText: embedding을 만들 텍스트
 * - aiEmbedding: 자연어 검색용 벡터
 */
export const analyzePhoto = onCall(
  {region: REGION, secrets: [openAiApiKey]},
  async (request) => {
    assertSignedIn(request.auth?.uid);
    const input = AnalyzePhotoSchema.parse(request.data);
    await assertAlbumMember(input.albumId, request.auth!.uid);

    const openai = createOpenAI();
    const analysis = await analyzeImage(openai, input.imageUrl);
    const aiSearchText = [
      analysis.caption,
      analysis.category,
      analysis.location,
      analysis.tags.join(" ")
    ].filter(Boolean).join(" ");
    const embedding = await createEmbedding(openai, aiSearchText);

    await db.collection("albums")
      .doc(input.albumId)
      .collection("photos")
      .doc(input.photoId)
      .set({
        aiCaption: analysis.caption,
        aiTags: analysis.tags,
        aiCategory: analysis.category,
        aiLocation: analysis.location,
        aiSearchText,
        aiEmbedding: embedding,
        aiAnalyzedAt: Date.now()
      }, {merge: true});

    return {ok: true, analysis};
  }
);

/**
 * "강아지 사진만 보여줘", "작년 여름 제주도 여행 사진" 같은 자연어 검색입니다.
 *
 * 처리 순서:
 * 1. LLM이 사용자 문장을 location/date/tags 같은 검색 조건으로 구조화합니다.
 * 2. 사용자의 원문을 embedding으로 바꿉니다.
 * 3. 사용자가 속한 앨범의 사진 후보를 가져옵니다.
 * 4. 태그/장소/날짜 필터와 embedding 유사도 점수를 섞어서 정렬합니다.
 */
export const searchPhotos = onCall(
  {region: REGION, secrets: [openAiApiKey]},
  async (request) => {
    assertSignedIn(request.auth?.uid);
    const input = SearchPhotosSchema.parse(request.data);
    const openai = createOpenAI();

    const intent = await parseSearchIntent(openai, input.query);
    const queryEmbedding = await createEmbedding(openai, input.query);
    const candidates = await loadPhotoCandidates(request.auth!.uid, input.albumId);
    const ranked = rankPhotos(candidates, queryEmbedding, intent).slice(0, 40);

    return {
      ok: true,
      intent,
      photos: ranked
    };
  }
);

/**
 * "작년 파리 갔을 때 사진만 따로 모아서 앨범 만들어줘" 같은 명령입니다.
 *
 * 실제 서비스에서는 바로 생성하지 않고 앱에서 검색 결과를 먼저 보여준 뒤
 * 사용자가 확인 버튼을 누르게 하는 편이 안전합니다. 여기서는 포트폴리오 MVP용으로
 * 새 앨범 생성까지 한 번에 수행하는 예시를 제공합니다.
 */
export const createAlbumFromPrompt = onCall(
  {region: REGION, secrets: [openAiApiKey]},
  async (request) => {
    assertSignedIn(request.auth?.uid);
    const input = CreateAlbumFromPromptSchema.parse(request.data);
    const openai = createOpenAI();

    const intent = await parseSearchIntent(openai, input.prompt);
    const queryEmbedding = await createEmbedding(openai, input.prompt);
    const candidates = await loadPhotoCandidates(request.auth!.uid);
    const selectedPhotos = rankPhotos(candidates, queryEmbedding, intent).slice(0, 60);

    if (selectedPhotos.length === 0) {
      return {ok: false, message: "조건에 맞는 사진을 찾지 못했습니다."};
    }

    const title = input.title?.trim() || intent.suggestedAlbumTitle || "AI가 모은 앨범";
    const albumRef = db.collection("albums").doc();
    const inviteCode = createInviteCode();
    const batch = db.batch();

    batch.set(albumRef, {
      title,
      ownerId: request.auth!.uid,
      ownerEmail: request.auth?.token.email || "",
      ownerNickname: request.auth?.token.name || "",
      memberIds: [request.auth!.uid],
      inviteCode,
      createdAt: Date.now(),
      createdByAgent: true,
      sourcePrompt: input.prompt
    });

    batch.set(db.collection("inviteCodes").doc(inviteCode), {
      albumId: albumRef.id,
      createdBy: request.auth!.uid,
      createdAt: Date.now()
    });

    selectedPhotos.forEach((photo) => {
      batch.set(albumRef.collection("photos").doc(photo.photoId), {
        imageUrl: photo.imageUrl,
        sourceAlbumId: photo.albumId,
        sourcePhotoId: photo.photoId,
        uploaderNickname: photo.albumTitle,
        aiCaption: photo.aiCaption,
        aiTags: photo.aiTags,
        aiCategory: photo.aiCategory,
        aiLocation: photo.aiLocation,
        reactions: {},
        commentCount: 0,
        uploadedAt: photo.uploadedAt,
        addedByAgent: true
      });
    });

    await batch.commit();

    return {
      ok: true,
      albumId: albumRef.id,
      title,
      count: selectedPhotos.length,
      photos: selectedPhotos
    };
  }
);

function createOpenAI() {
  return new OpenAI({apiKey: openAiApiKey.value()});
}

function assertSignedIn(uid?: string) {
  if (!uid) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다.");
  }
}

async function assertAlbumMember(albumId: string, uid: string) {
  const album = await db.collection("albums").doc(albumId).get();
  const memberIds = album.get("memberIds") as string[] | undefined;
  if (!album.exists || !memberIds?.includes(uid)) {
    throw new HttpsError("permission-denied", "앨범 멤버만 사용할 수 있습니다.");
  }
}

async function analyzeImage(openai: OpenAI, imageUrl: string) {
  const response = await openai.chat.completions.create({
    model: CHAT_MODEL,
    response_format: {type: "json_object"},
    messages: [
      {
        role: "system",
        content:
          "사진 검색용 메타데이터를 한국어 JSON으로 생성하세요. " +
          "반드시 caption, tags, category, location 필드를 포함하세요. " +
          "tags는 5~12개의 짧은 단어 배열입니다."
      },
      {
        role: "user",
        content: [
          {type: "text", text: "이 사진을 공유 앨범 검색용으로 분석해줘."},
          {type: "image_url", image_url: {url: imageUrl}}
        ]
      }
    ]
  });

  const raw = response.choices[0]?.message?.content || "{}";
  const parsed = JSON.parse(raw) as Partial<{
    caption: string;
    tags: string[];
    category: string;
    location: string;
  }>;

  return {
    caption: parsed.caption || "",
    tags: Array.isArray(parsed.tags) ? parsed.tags.map(String).slice(0, 12) : [],
    category: parsed.category || "기타",
    location: parsed.location || ""
  };
}

async function parseSearchIntent(openai: OpenAI, query: string) {
  const currentYear = new Date().getFullYear();
  const response = await openai.chat.completions.create({
    model: CHAT_MODEL,
    response_format: {type: "json_object"},
    messages: [
      {
        role: "system",
        content:
          `현재 연도는 ${currentYear}년입니다. ` +
          "사용자의 사진 검색/앨범 생성 요청을 JSON으로 구조화하세요. " +
          "필드: keywords(string[]), location(string), category(string), " +
          "dateFrom(number|null), dateTo(number|null), suggestedAlbumTitle(string). " +
          "dateFrom/dateTo는 밀리초 timestamp입니다. 모르면 null."
      },
      {role: "user", content: query}
    ]
  });

  const raw = response.choices[0]?.message?.content || "{}";
  const parsed = JSON.parse(raw) as Partial<{
    keywords: string[];
    location: string;
    category: string;
    dateFrom: number | null;
    dateTo: number | null;
    suggestedAlbumTitle: string;
  }>;

  return {
    keywords: Array.isArray(parsed.keywords) ? parsed.keywords.map(String) : [],
    location: parsed.location || "",
    category: parsed.category || "",
    dateFrom: typeof parsed.dateFrom === "number" ? parsed.dateFrom : null,
    dateTo: typeof parsed.dateTo === "number" ? parsed.dateTo : null,
    suggestedAlbumTitle: parsed.suggestedAlbumTitle || ""
  };
}

async function createEmbedding(openai: OpenAI, input: string) {
  const response = await openai.embeddings.create({
    model: EMBEDDING_MODEL,
    input
  });
  return response.data[0].embedding;
}

async function loadPhotoCandidates(uid: string, albumId?: string): Promise<PhotoCandidate[]> {
  const albumsSnapshot = albumId ?
    await db.collection("albums").where(admin.firestore.FieldPath.documentId(), "==", albumId).get() :
    await db.collection("albums").where("memberIds", "array-contains", uid).get();

  const candidates: PhotoCandidate[] = [];

  for (const albumDoc of albumsSnapshot.docs) {
    const memberIds = albumDoc.get("memberIds") as string[] | undefined;
    if (!memberIds?.includes(uid)) continue;

    const photosSnapshot = await albumDoc.ref.collection("photos").get();
    photosSnapshot.docs.forEach((photoDoc) => {
      const imageUrl = photoDoc.get("imageUrl") as string | undefined;
      const embedding = photoDoc.get("aiEmbedding") as number[] | undefined;
      if (!imageUrl || !embedding?.length) return;

      candidates.push({
        albumId: albumDoc.id,
        albumTitle: albumDoc.get("title") || "",
        photoId: photoDoc.id,
        imageUrl,
        aiCaption: photoDoc.get("aiCaption") || "",
        aiTags: photoDoc.get("aiTags") || [],
        aiCategory: photoDoc.get("aiCategory") || "",
        aiLocation: photoDoc.get("aiLocation") || "",
        aiEmbedding: embedding,
        uploadedAt: photoDoc.get("uploadedAt") || 0,
      });
    });
  }

  return candidates;
}

function rankPhotos(
  candidates: PhotoCandidate[],
  queryEmbedding: number[],
  intent: {
    keywords: string[];
    location: string;
    category: string;
    dateFrom: number | null;
    dateTo: number | null;
  }
) {
  return candidates
    .map((photo) => {
      let score = 0;
      const text = `${photo.aiCaption} ${photo.aiTags.join(" ")} ${photo.aiCategory} ${photo.aiLocation}`;
      const embedding = photo.aiEmbedding;

      if (embedding.length) score += cosineSimilarity(queryEmbedding, embedding) * 10;
      if (intent.location && photo.aiLocation.includes(intent.location)) score += 3;
      if (intent.category && photo.aiCategory.includes(intent.category)) score += 2;
      intent.keywords.forEach((keyword) => {
        if (text.includes(keyword)) score += 1;
      });
      if (intent.dateFrom && photo.uploadedAt < intent.dateFrom) score -= 5;
      if (intent.dateTo && photo.uploadedAt > intent.dateTo) score -= 5;

      return {...photo, score};
    })
    .filter((photo) => (photo.score || 0) > 0)
    .sort((a, b) => (b.score || 0) - (a.score || 0));
}

function cosineSimilarity(a: number[], b: number[]) {
  if (a.length !== b.length || a.length === 0) return 0;
  let dot = 0;
  let normA = 0;
  let normB = 0;
  for (let i = 0; i < a.length; i++) {
    dot += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }
  return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}

function createInviteCode() {
  return randomUUID().replaceAll("-", "").slice(0, 8).toUpperCase();
}
