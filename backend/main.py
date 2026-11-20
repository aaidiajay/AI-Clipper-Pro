import os, re, tempfile, subprocess
from pathlib import Path
from typing import Any
from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel, HttpUrl
from dotenv import load_dotenv
from openai import OpenAI

load_dotenv()
app = FastAPI(title="AI Clipper Pro API", version="0.1.0")
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY")) if os.getenv("OPENAI_API_KEY") else None

class LinkRequest(BaseModel):
    source_url: HttpUrl

@app.get("/health")
def health():
    return {"ok": True, "service": "AI Clipper Pro"}

@app.post("/analyze-link")
def analyze_link(req: LinkRequest):
    host = req.source_url.host.lower()
    if not (host.endswith("youtube.com") or host == "youtu.be" or host.endswith("twitch.tv")):
        raise HTTPException(400, "Only YouTube and Twitch URLs are accepted.")
    return {
        "status": "link_validated",
        "message": "Link accepted for reference. This starter does not fetch or bypass platform streams.",
        "next_step": "Upload a video file you own or are authorized to process.",
        "source_url": str(req.source_url)
    }

@app.post("/analyze-video")
async def analyze_video(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("video/"):
        raise HTTPException(415, "Please upload a video file.")
    if not client:
        raise HTTPException(503, "Server admin must configure OPENAI_API_KEY.")
    suffix = Path(file.filename or "video.mp4").suffix or ".mp4"
    with tempfile.TemporaryDirectory() as td:
        path = Path(td) / ("source" + suffix)
        data = await file.read()
        if len(data) > 500 * 1024 * 1024:
            raise HTTPException(413, "Maximum upload size is 500 MB.")
        path.write_bytes(data)
        # Extract 6 evenly spaced preview frames. Full scene detection/transcription is a later milestone.
        probe = subprocess.run(
            ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
            capture_output=True, text=True
        )
        if probe.returncode != 0:
            raise HTTPException(400, "Could not read video. Ensure FFmpeg/ffprobe is installed.")
        duration = max(float(probe.stdout.strip() or 0), 1.0)
        frames = []
        for i in range(6):
            sec = min(duration - 0.1, duration * (i + 1) / 7)
            out = Path(td) / f"frame{i}.jpg"
            subprocess.run(["ffmpeg", "-y", "-ss", str(sec), "-i", str(path), "-frames:v", "1", "-q:v", "5", str(out)],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if out.exists():
                import base64
                frames.append((sec, base64.b64encode(out.read_bytes()).decode()))
        if not frames:
            raise HTTPException(400, "No preview frames could be extracted.")
        content: list[dict[str, Any]] = [{"type":"text","text":
            "Review these timestamped video frames. Identify only visually supported possible highlight windows for a short-form clip. "
            "Do not claim audio/dialogue is known. Return JSON with moments array; each item: start_seconds, end_seconds, moment_type, reason, hook, on_screen_caption, title, hashtags. "
            "Keep suggested windows short and mark confidence low because only sparse frames are available."}]
        for sec, b64 in frames:
            content.append({"type":"text","text":f"Frame timestamp: {sec:.1f} seconds"})
            content.append({"type":"image_url","image_url":{"url":f"data:image/jpeg;base64,{b64}"}})
        response = client.chat.completions.create(
            model=os.getenv("OPENAI_MODEL","gpt-4.1-mini"),
            messages=[{"role":"user","content":content}],
            response_format={"type":"json_object"}
        )
        import json
        result = json.loads(response.choices[0].message.content or "{}")
        return {"duration_seconds": duration, "analysis": result,
                "notice": "AI suggestions are approximate. Review every clip manually before publishing."}
