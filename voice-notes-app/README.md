# Obsidian Voice Notes (Android)

녹음 → **온디바이스(오프라인) 음성 인식** → Obsidian 볼트(동기화 폴더)에 Markdown 노트 저장.
저장된 노트는 Obsidian Sync로 PC까지 자동 동기화되고, PC에서 회의·강의 스크립트를 요약 정리하면 됩니다.

```
[녹음] ──▶ 마이크 수음 ──▶ Vosk 오프라인 STT ──▶ 실시간 스크립트
                                                     │
                                          [저장] 버튼 ▼
                                동기화 폴더에 yyyy-MM-dd HHmm 제목.md 기록
                                                     │
                                      Obsidian Sync / Syncthing ▼
                                              PC의 Obsidian 볼트
```

## 특징

- **완전 오프라인 인식**: 음성이 서버로 전송되지 않습니다. Vosk 한국어 모델이 기기에서 직접 변환합니다.
- **긴 녹음 지원**: 마이크 포그라운드 서비스로 화면이 꺼져도 회의·강의 내내 인식이 유지됩니다.
- **Obsidian 친화적 저장**: 사용자가 고른 동기화 폴더에 프론트매터 + `## 요약` + `## 스크립트` 구조의 `.md`를 씁니다.

## 요구 사항

- Android Studio (Koala 이상 권장)
- Android SDK 34, 최소 지원 Android 8.0 (API 26)
- 실기기 권장 (마이크 필요)

## 빌드 방법

1. Android Studio에서 **`voice-notes-app/` 폴더를 프로젝트로 엽니다.**
2. 최초 열기 시 Gradle Sync가 필요한 의존성(Compose, Vosk 등)을 내려받습니다.
3. 실기기를 연결하고 **Run ▶** 을 누릅니다.

> **참고 — Gradle Wrapper JAR**
> 저장소에는 `gradlew` 스크립트만 포함되어 있고 바이너리인 `gradle/wrapper/gradle-wrapper.jar` 는 포함하지 않았습니다.
> Android Studio로 프로젝트를 열면 자동으로 생성됩니다. 커맨드라인에서 빌드하려면 먼저 한 번만 실행하세요:
> ```
> cd voice-notes-app
> gradle wrapper --gradle-version 8.9
> ./gradlew assembleDebug
> ```
> (시스템에 Gradle이 설치돼 있어야 합니다.)

## 최초 실행 시

1. **마이크 권한**과 (Android 13+) **알림 권한**을 허용합니다.
2. **오프라인 한국어 음성 모델(약 80MB)** 이 자동으로 내려받아집니다. 화면 상단 진행바로 확인.
   - 모델: `vosk-model-small-ko-0.22` — 다른 언어는 `app/.../stt/ModelManager.kt` 의 `MODEL_URL` 을 교체하세요. (https://alphacephei.com/vosk/models)
3. **폴더 선택** 버튼으로 Obsidian 볼트(또는 그 하위 폴더)를 지정합니다. 한 번 지정하면 기억됩니다.

## 사용 흐름

1. **녹음** 버튼 → 실시간으로 스크립트가 쌓입니다.
2. **정지** 버튼 → 인식을 마칩니다.
3. (선택) 제목 입력 → **저장** 버튼 → 볼트 폴더에 `.md` 노트가 생성됩니다.

## Obsidian 동기화 연결 방법 (택1)

- **Obsidian Sync (유료)**: 폰과 PC의 Obsidian이 같은 볼트를 Sync로 공유하면, 이 앱이 볼트 폴더에 쓴 노트가 그대로 PC로 전달됩니다.
  - 폰에서 폴더 선택 시 **Obsidian 볼트 폴더(또는 하위의 `Voice` 같은 폴더)** 를 지정하세요.
- **Syncthing / Dropbox 등 폴더 동기화**: 앱이 쓰는 폴더를 PC 볼트와 동기화하도록 설정하면 Obsidian Sync 없이도 됩니다.

## PC에서 요약 정리

노트의 `## 요약` 섹션은 비어 있습니다. PC에서 Obsidian으로 노트를 연 뒤,
`## 스크립트` 내용을 Claude(또는 원하는 LLM)에 넣어 요약을 만들어 `## 요약` 아래에 붙여넣으면
"항상 최신 요약본을 얻는" 워크플로가 완성됩니다.
(원하면 이 요약 단계를 앱 안에서 Claude API로 자동화하는 것도 다음 단계로 추가할 수 있습니다.)

## 프로젝트 구조

```
app/src/main/java/com/obsidianvoice/notes/
├─ MainActivity.kt              # 권한 요청, 폴더 선택(SAF), 화면 구성
├─ VoiceNotesApplication.kt     # 알림 채널
├─ data/SettingsStore.kt        # 볼트 폴더 URI 영구 저장 (DataStore)
├─ stt/ModelManager.kt          # 오프라인 모델 다운로드/해제
├─ recording/
│  ├─ RecordingBus.kt           # 전사 상태 공유 (Service ↔ UI)
│  └─ RecordingService.kt       # 마이크 포그라운드 서비스 + Vosk 인식
├─ storage/VaultWriter.kt       # 동기화 폴더에 Markdown 기록 (SAF)
└─ ui/
   ├─ RecordViewModel.kt        # 모델/녹음/저장 오케스트레이션
   ├─ RecordScreen.kt           # Compose 화면
   └─ theme/Theme.kt
```

## 알려진 제약 / 다음 단계 후보

- 온디바이스 소형 모델은 클라우드 STT보다 정확도가 낮을 수 있습니다. 더 큰 Vosk 모델로 교체하면 정확도가 올라갑니다(용량↑).
- 화자 분리(누가 말했는지)는 포함하지 않았습니다.
- 앱 내 자동 요약(Claude API), 오디오 원본 저장, 노트 히스토리 화면 등은 원하면 이어서 추가할 수 있습니다.
