# AI Clipper Pro (starter project)

Android + cloud backend starter for analyzing **videos you own or are authorized to use**.

## What is included
- Android Kotlin/Jetpack Compose starter UI.
- Paste a YouTube/Twitch URL or choose a local video.
- Backend API contract for health check and analysis.
- Cloud analysis scaffold: extracts representative frames with FFmpeg, sends frame descriptions to an OpenAI-compatible vision endpoint, and returns suggested timestamps/titles/captions.
- Clear note: URL ingestion is intentionally not implemented. A pasted platform URL is metadata only; use a permitted export/download or upload a video file you have rights to use. This avoids bypassing platform access controls or downloading restricted streams.

## Build Android APK
1. Install Android Studio (current stable) and Android SDK.
2. Open `android/` in Android Studio.
3. Let Gradle sync. If prompted, install Android Gradle Plugin/SDK versions.
4. Set `BASE_URL` in `android/app/src/main/java/com/aiclipperpro/MainActivity.kt` to your backend HTTPS URL.
5. Build > Build Bundle(s) / APK(s) > Build APK(s).

This is a starter source project, not a compiled APK. An APK needs an Android SDK/Gradle build environment.

## Run backend
Requires Python 3.11+, FFmpeg installed, and an OpenAI API key.
```bash
cd backend
python -m venv .venv
# activate the venv, then:
pip install -r requirements.txt
cp .env.example .env
# add OPENAI_API_KEY to .env
uvicorn main:app --host 0.0.0.0 --port 8000
```
Do not expose the backend publicly without HTTPS, authentication, rate limits, and upload-size limits.

## Current prototype limits
- The cloud AI endpoint is a starter implementation; visual moments are suggestions, not guaranteed "viral" predictions.
- Captions/transcription and final MP4 rendering are planned next steps.
- Never submit copyrighted creator footage unless you have permission or another valid legal basis. Campaign eligibility is separate from whether a clip can be processed.
