# JARVIS 0.2.0 patch

This patch overlays the Flutter source extracted by the Android release workflow.
It adds an OpenAI-compatible EVREN endpoint configuration, secure API-key storage,
local persistent conversation history, image/file selection, image content support,
and PDF/text extraction for chat context.

The build workflow must copy this directory over build-source/jarvis_ai/app before
running scripts/build-android.sh.
