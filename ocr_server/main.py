"""
PaddleOCR FastAPI Sidecar Service
Exposes two OCR endpoints compatible with the LC Checker application:
  - POST /predict/ocr_system  (PaddleHub Serving compatible)
  - POST /api/v1/ocr          (custom REST endpoint)

Both endpoints accept JSON: {"images": ["<base64_encoded_image>"]}
and return: {"results": [[{"text": "...", "confidence": 0.99, "text_region": [...]}]]}
"""

import base64
import io
import logging
import threading
from contextlib import asynccontextmanager
from typing import List, Optional

import numpy as np
from fastapi import FastAPI, HTTPException
from PIL import Image
from paddleocr import PaddleOCR
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("paddleocr-server")

# Resource limits — defend against abusive or oversized payloads.
MAX_IMAGES_PER_REQUEST = 50
MAX_IMAGE_BYTES = 20 * 1024 * 1024  # 20 MB decoded image

ocr_engine = None
engine_lock = threading.Lock()


def init_engine() -> None:
    """Initialise the PaddleOCR engine under a lock (idempotent)."""
    global ocr_engine
    with engine_lock:
        if ocr_engine is None:
            logger.info("Initialising PaddleOCR engine (lang=ch, use_angle_cls=True)...")
            ocr_engine = PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
            logger.info("PaddleOCR engine initialised successfully.")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # Pre-warm the model at startup so the first business request does not pay
    # the multi-second init/download cost (which would exceed the Java client's
    # 30s timeout). The HEALTHCHECK start-period in the Dockerfile is sized to
    # absorb this blocking initialisation; until it finishes uvicorn is not yet
    # listening, so /health fails fast (connection refused) rather than hanging
    # — within start-period that is treated as "starting", not "unhealthy".
    init_engine()
    yield


app = FastAPI(title="PaddleOCR Sidecar Service", version="1.0.0", lifespan=lifespan)


def get_ocr_engine() -> PaddleOCR:
    """Return the shared engine. Lazily (re)initialises as a defensive fallback
    if startup pre-warm did not run (e.g. when imported directly for tests)."""
    if ocr_engine is None:
        init_engine()
    return ocr_engine


class OcrRequest(BaseModel):
    images: List[str]  # List of base64-encoded image strings


class OcrTextResult(BaseModel):
    text: str
    confidence: float
    text_region: Optional[List[List[int]]] = None


class OcrResponse(BaseModel):
    results: List[List[OcrTextResult]]
    status: str = "000"


def _validate_images(images: List[str]) -> None:
    if not images:
        raise HTTPException(status_code=400, detail="No images provided")
    if len(images) > MAX_IMAGES_PER_REQUEST:
        raise HTTPException(
            status_code=400,
            detail=f"Too many images: {len(images)} (max {MAX_IMAGES_PER_REQUEST})",
        )


def decode_base64_image(b64_string: str) -> np.ndarray:
    """Decode a base64-encoded image string to a numpy array for PaddleOCR."""
    try:
        image_bytes = base64.b64decode(b64_string, validate=True)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid base64 image encoding")
    if len(image_bytes) > MAX_IMAGE_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Image too large: {len(image_bytes)} bytes (max {MAX_IMAGE_BYTES})",
        )
    try:
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        return np.array(image)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid or unreadable image")


def process_ocr(images: List[str]) -> OcrResponse:
    """Core OCR processing logic shared by both endpoints."""
    all_results = []

    for idx, b64_img in enumerate(images):
        logger.info(f"Processing image {idx + 1}/{len(images)} ({len(b64_img)} base64 chars)")
        img_array = decode_base64_image(b64_img)
        ocr_result = get_ocr_engine().ocr(img_array, cls=True)

        image_results = []
        if ocr_result and ocr_result[0]:
            for line in ocr_result[0]:
                box = line[0]  # [[x1,y1],[x2,y2],[x3,y3],[x4,y4]]
                text = line[1][0]
                confidence = float(line[1][1])
                # Convert box coordinates to integer list
                region = [[int(p[0]), int(p[1])] for p in box]
                image_results.append(OcrTextResult(
                    text=text,
                    confidence=confidence,
                    text_region=region
                ))

        all_results.append(image_results)
        logger.info(f"Image {idx + 1}: extracted {len(image_results)} text regions")

    return OcrResponse(results=all_results, status="000")


@app.post("/predict/ocr_system", response_model=OcrResponse)
def predict_ocr_system(request: OcrRequest):
    """PaddleHub Serving compatible endpoint."""
    _validate_images(request.images)
    return process_ocr(request.images)


@app.post("/api/v1/ocr", response_model=OcrResponse)
def api_v1_ocr(request: OcrRequest):
    """Custom REST API endpoint for OCR."""
    _validate_images(request.images)
    return process_ocr(request.images)


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "engine": "PaddleOCR", "lang": "ch"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8866)
