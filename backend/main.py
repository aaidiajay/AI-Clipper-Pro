
import os
import re
import json
import base64
import tempfile
import subprocess
from pathlib import Path
from typing import Any

import imageio_ffmpeg
from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel, HttpUrl
from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()

app = FastAPI(title="AI Clipper Pro API", version="0.2.0")

client = (
    OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
    if os.getenv("OPENAI_API_KEY")
    else None
)

MAX_UPLOAD_SIZE = 100 * 1024 * 1024


class LinkRequest(BaseModel):
    source_url: HttpUrl


@app.get("/health")
def health():
    return {"ok": True, "service": "AI Clipper Pro"}


@app.post("/analyze-link")
def analyze_link(req: LinkRequest):
    host = (req.source_url.host or "").lower()

    allowed = (
        host == "youtube.com"
        or host.endswith(".youtube.com")
        or host == "youtu.be"
        or host == "twitch.tv"
        or host.endswith(".twitch.tv")
    )

    if not allowed:
        raise HTTPException(
            status_code=400,
            detail="Only YouTube and Twitch URLs are accepted."
        )

    return {
        "status": "link_validated",
        "message": "Link accepted. Platform streams are not downloaded.",
        "next_step": "Upload a video file you own or are authorized to process.",
        "source_url": str(req.source_url)
    }


@app.post("/analyze-video")
async def analyze_video(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("video/"):
        raise HTTPException(
            status_code=415,
            detail="Please upload a video file."
        )

    if not client:
        raise HTTPException(
            status_code=503,
            detail="Server admin must configure OPENAI_API_KEY."
        )

    suffix = Path(file.filename or "video.mp4").suffix or ".mp4"

    with tempfile.TemporaryDirectory() as td:
        video_path = Path(td) / ("source" + suffix)

        total_size = 0

        with video_path.open("wb") as output:
            while True:
                chunk = await file.read(1024 * 1024)

                if not chunk:
                    break

                total_size += len(chunk)

                if total_size > MAX_UPLOAD_SIZE:
                    raise HTTPException(
                        status_code=413,
                        detail="Maximum upload size is 100 MB."
                    )

                output.write(chunk)

        await file.close()

        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()

        probe = subprocess.run(
            [ffmpeg, "-i", str(video_path)],
            capture_output=True,
            text=True,
            timeout=60
        )

        info = probe.stderr or ""
        match = re.search(r"Duration:\s*(\d+):(\d+):([\d.]+)", info)

        if not match:
            raise HTTPException(
                status_code=400,
                detail="Could not read video duration. Please try an MP4 video."
            )

        hours, minutes, seconds = match.groups()
        duration = (
            int(hours) * 3600
            + int(minutes) * 60
            + float(seconds)
        )

        if duration < 2:
            raise HTTPException(
                status_code=400,
                detail="Video is too short."
            )

        frames = []

        for i in range(6):
            timestamp = min(
                duration - 0.1,
                duration * (i + 1) / 7
            )

            frame_path = Path(td) / f"frame{i}.jpg"

            subprocess.run(
                [
                    ffmpeg,
                    "-y",
                    "-ss", str(timestamp),
                    "-i", str(video_path),
                    "-frames:v", "1",
                    "-q:v", "5",
                    str(frame_path)
                ],
                capture_output=True,
                timeout=60
            )

            if frame_path.exists():
                encoded = base64.b64encode(
                    frame_path.read_bytes()
                ).decode("utf-8")

                frames.append((timestamp, encoded))

        if not frames:
            raise HTTPException(
                status_code=400,
                detail="Could not extract video frames."
            )

        content: list[dict[str, Any]] = [
            {
                "type": "text",
                "text": (
                    "Analyze these video frames for possible gaming highlights. "
                    "Only use visual evidence. Audio and dialogue are unknown. "
                    "Return JSON with a moments array. Each moment must contain "
                    "start_seconds, end_seconds, moment_type, reason, hook, "
                    "on_screen_caption, title and hashtags. "
                    "Keep suggested clips between 10 and 60 seconds where possible. "
                    "Only suggest timestamps within the video duration. "
                    "Mention that confidence is limited because only six frames "
                    "were sampled."
                )
            }
        ]

        for timestamp, encoded in frames:
            content.append({
                "type": "text",
                "text": f"Frame timestamp: {timestamp:.1f} seconds"
            })

            content.append({
                "type": "image_url",
                "image_url": {
                    "url": f"data:image/jpeg;base64,{encoded}"
                }
            })

        try:
            response = client.chat.completions.create(
                model=os.getenv("OPENAI_MODEL", "gpt-4.1-mini"),
                messages=[
                    {
                        "role": "user",
                        "content": content
                    }
                ],
                response_format={"type": "json_object"}
            )

            result = json.loads(
                response.choices[0].message.content or "{}"
            )

        except Exception as error:
            raise HTTPException(
                status_code=502,
                detail=f"AI analysis failed: {str(error)[:300]}"
            )

        return {
            "status": "analysis_complete",
            "duration_seconds": round(duration, 2),
            "analysis": result,
            "notice": (
                "These are approximate highlight suggestions based on "
                "six sampled frames. No MP4 clips have been generated yet. "
                "Review suggestions before publishing."
            )
        }

