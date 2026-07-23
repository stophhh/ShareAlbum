from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
from fastapi.responses import JSONResponse
from datetime import datetime

app = FastAPI() # 서버 생성

# 커스텀 예외 처리 클래스 정의

class AppException(Exception):
    def __init__(self, status_code: int, error_code: str, error_message: str):
        self.status_code = status_code
        self.error_code = error_code
        self.error_message = error_message


@app.exception_handler(AppException)
async def app_exception_handler(request: Request, exc: AppException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "success": False,
            "error_code": exc.error_code,
            "error_message": exc.error_message
        }
    )


# 에러 처리 API

class ErrorResponse(BaseModel):
    success: bool
    error_code: str
    error_message: str

@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "success": False,
            "error_code": f"HTTP_{exc.status_code}",
            "error_message": exc.detail
        }
    )

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={
            "success": False,
            "error_code": "HTTP_500",
            "error_message": "서버 내부 오류가 발생했습니다."
        }
    )


class AnalyzePhotoRequest(BaseModel):
    album_id: str
    photo_id: str
    image_url: str

@app.post("/ai/analyze-photo")
def analyze_photo(request: AnalyzePhotoRequest):
    if not request.image_url:
        raise AppException(
            status_code=400,
            error_code="INVALID_IMAGE_URL",
            error_message="이미지 URL이 유효하지 않습니다."
        )
    return {
        "success": True,
        "album_id": request.album_id,
        "photo_id": request.photo_id,
        "analysis_result": f"사진 '{request.photo_id}'이(가) 성공적으로 분석되었습니다."
    }

@app.get("/albums/{album_id}")
def get_album(album_id: str):
    if album_id == "none":
        raise AppException(
            status_code=404,
            error_code="ALBUM_NOT_FOUND",
            error_message="앨범을 찾을 수 없습니다."
        )
    return {"success": True, "album_id": album_id}


# 자연어 명령 API 

class PhotoItem(BaseModel):
    photo_id: str
    image_url: str
    tags: list[str] = []
    uploader_id: str = ""
    uploader_name: str = ""
    location: str | None = None
    uploaded_at: str | None = None
    reaction_count: int = 0
    save_count: int = 0


class AgentRequest(BaseModel):
    user_id: str
    message: str
    photos: list[PhotoItem] = []

@app.post("/agent/command")
def agent_command(request: AgentRequest):
    intent = classify_intent(request.message)
    keywords = extract_keywords(request.message)
    matched_photos = search_photos(request.message, request.photos, intent, request.user_id)
    suggested_album_title = suggest_album_title(request.message, keywords, intent)

    return {
        "success": True,
        "intent": intent,
        "user_id": request.user_id,
        "message": request.message,
        "photo_count": len(request.photos),
        "matched_photo_count": len(matched_photos),
        "suggested_album_title": suggested_album_title,
        "photos": matched_photos,
        "response": f"'{request.message}' 요청을 {intent}로 분류했고, 사진 {len(matched_photos)}장을 찾았습니다."
    }

def search_photos(message: str, photos: list[PhotoItem], intent: str, user_id: str):
    if is_my_photo_command(message):
        return [photo for photo in photos if photo.uploader_id == user_id]

    if intent == "SHOW_BEST_PHOTOS" or is_best_photo_command(message):
        return search_best_photos(photos)

    if intent == "SHOW_MOST_SAVED_PHOTOS" or is_most_saved_command(message):
        return search_most_saved_photos(photos)

    if intent == "SHOW_SIMILAR_CANDIDATES" or is_similar_candidate_command(message):
        return search_similar_candidates(photos)

    keywords = extract_keywords(message)
    if not keywords:
        return photos if "사진" in message else []

    matched = []
    for photo in photos:
        searchable_text = " ".join([
            " ".join(photo.tags),
            photo.uploader_name,
            photo.uploader_id,
            photo.location or "",
            photo.uploaded_at or ""
        ])
        if any(keyword in searchable_text for keyword in keywords):
            matched.append(photo)

    return matched


def search_best_photos(photos: list[PhotoItem]):
    reacted_photos = [photo for photo in photos if photo.reaction_count > 0]
    target_photos = reacted_photos if reacted_photos else photos
    return sorted(target_photos, key=lambda photo: photo.reaction_count, reverse=True)[:20]


def search_most_saved_photos(photos: list[PhotoItem]):
    saved_photos = [photo for photo in photos if photo.save_count > 0]
    target_photos = saved_photos if saved_photos else photos
    return sorted(target_photos, key=lambda photo: photo.save_count, reverse=True)[:20]


def search_similar_candidates(photos: list[PhotoItem]):
    groups: dict[str, list[PhotoItem]] = {}
    for photo in photos:
        key_parts = [
            tag for tag in photo.tags
            if "년" in tag or "월" in tag or tag in ["봄", "여름", "가을", "겨울"]
        ][:3]
        uploader_tags = [tag for tag in photo.tags if tag and "@" not in tag][-1:]
        key = " ".join(key_parts + uploader_tags)
        if key:
            groups.setdefault(key, []).append(photo)

    candidates = []
    for group in groups.values():
        if len(group) >= 2:
            candidates.extend(group[:4])

    return candidates[:20]


def extract_keywords(message: str):
    now = datetime.now()
    relative_keywords = []
    if "올해" in message:
        relative_keywords.extend([f"{now.year}년", str(now.year)])
    if "작년" in message:
        relative_keywords.extend([f"{now.year - 1}년", str(now.year - 1)])
    if "이번달" in message or "이번 달" in message:
        relative_keywords.append(f"{now.month}월")
    if "지난달" in message or "지난 달" in message:
        previous_month = 12 if now.month == 1 else now.month - 1
        relative_keywords.append(f"{previous_month}월")

    ignored_words = [
    "사진", "보여줘", "보여", "띄워", "열어", "열기", "열람",
    "검색", "찾아줘", "찾아", "만", "에", "찍은", "올린",
    "앨범", "만들어줘", "만들어", "생성해줘", "생성", "모아서", "모아",
    "올해", "작년", "이번달", "이번 달", "지난달", "지난 달",
    "베스트", "좋아요", "반응", "많은", "인기",
    "저장", "저장한", "저장된", "많이", "비슷한", "중복", "후보",
    "내가", "내", "나의", "사용자별", "멤버별", "사람별"
]
    normalized = message
    for word in ignored_words:
        normalized = normalized.replace(word, " ")
    direct_keywords = [word.strip() for word in normalized.split() if word.strip()]
    return list(dict.fromkeys(relative_keywords + direct_keywords))

def classify_intent(message: str):
    if is_create_album_command(message):
        return "CREATE_ALBUM_FROM_PHOTOS"

    if is_best_photo_command(message):
        return "SHOW_BEST_PHOTOS"

    if is_most_saved_command(message):
        return "SHOW_MOST_SAVED_PHOTOS"

    if is_similar_candidate_command(message):
        return "SHOW_SIMILAR_CANDIDATES"

    if is_search_command(message):
        return "SHOW_PHOTOS"

    return "UNKNOWN_INTENT"


def is_create_album_command(message: str):
    return (
        ("앨범" in message or "모음" in message or "사진" in message)
        and ("만들" in message or "생성" in message or "모아" in message)
    )


def is_search_command(message: str):
    return (
        "보여" in message or "띄워" in message or "열어" in message or "열기" in message or "열람" in message or "검색" in message
    )


def is_my_photo_command(message: str):
    return (
        "내가" in message or "내 사진" in message or "나의 사진" in message or "내가 올린" in message
    )


def is_best_photo_command(message: str):
    return (
        "베스트" in message or "인기" in message or "좋아요" in message or "반응 많은" in message
    )


def is_most_saved_command(message: str):
    return "저장" in message and ("많" in message or "베스트" in message or "모음" in message)


def is_similar_candidate_command(message: str):
    return "비슷한" in message or "중복" in message


def suggest_album_title(message: str, keywords: list[str], intent: str):
    if is_my_photo_command(message):
        return "내가 올린 사진 모음"

    if intent == "SHOW_BEST_PHOTOS" or is_best_photo_command(message):
        return "베스트 사진 모음"

    if intent == "SHOW_MOST_SAVED_PHOTOS" or is_most_saved_command(message):
        return "저장 많은 사진 모음"

    if intent == "SHOW_SIMILAR_CANDIDATES" or is_similar_candidate_command(message):
        return "비슷한 사진 후보"

    if keywords:
        return f"{keywords[0]} 사진 모음"

    if "여름" in message:
        return "여름 사진 모음"

    return "AI가 모은 앨범"
