FROM python:3.12-slim

WORKDIR /app

# Use USTC mirror for Debian apt to speed up build in China
RUN if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list.d/debian.sources; \
    fi && \
    if [ -f /etc/apt/sources.list ]; then \
        sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list; \
    fi

# Install system dependencies required by PaddlePaddle and OpenCV
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies using Tsinghua PyPI mirror
COPY requirements.txt .
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple --upgrade pip && \
    pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

# Copy application code
COPY main.py .

# NOTE: OCR models are downloaded at container STARTUP (in the FastAPI lifespan
# pre-warm step), NOT during image build. Pre-downloading during build performs
# a full PaddleOCR initialisation which is unreliable across CPU architectures;
# deferring it to startup keeps the image build portable. The HEALTHCHECK
# start-period below is sized to absorb this startup download.

EXPOSE 8866

# Health check — start-period sized to absorb the blocking PaddleOCR model
# initialisation that runs in the FastAPI lifespan at startup.
HEALTHCHECK --interval=30s --timeout=10s --start-period=300s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8866/health')" || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8866"]
