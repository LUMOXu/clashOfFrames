# cof-resource

Static assets for Clash of Frames (cards, UI images, audio, fonts).

## Layout

```
cof-resource/
  cards/{libraryFolder}/manifest.json
  cards/{libraryFolder}/cards/*.jpg
  cards/{libraryFolder}/back.png
  assets/bell.png
  assets/logo.png
  assets/bg1.jpg .. bg3.jpg
  assets/fonts/
  audio/ding.wav
  audio/sendcard.mp3
```

## Sync from legacy tree

From repo root (PowerShell), legacy tree under `old/`:

```powershell
Copy-Item -Recurse -Force old\cards\* cof-resource\cards\ -ErrorAction SilentlyContinue
Copy-Item old\bell.png,old\logo.png,old\bg1.jpg,old\bg2.jpg,old\bg3.jpg cof-resource\assets\ -ErrorAction SilentlyContinue
Copy-Item old\ding.wav,old\sendcard.mp3 cof-resource\audio\ -ErrorAction SilentlyContinue
```

Backend serves files from `COF_RESOURCE_ROOT` (default `../cof-resource`).
