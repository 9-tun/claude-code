# AI Council (Slack bot)

Slack에 질문하면 **Claude · GPT · Gemini**가 각자 답하고, 의장 모델이 **합의점 / 이견 / 최종 권고**를 종합하는 bot입니다.

- API가 아니라 **각 사의 공식 CLI**(`claude`, `codex`, `gemini`)를 호출하므로 이미 쓰는 **구독 요금제 범위 안에서** 동작합니다. 추가 API 과금이 없습니다.
- **Socket Mode**를 사용하므로 공개 서버나 포트 개방 없이 **내 PC에서 바로** 실행됩니다.
- 스레드에 답글을 달면 **후속 질문**이 됩니다. 이때 모든 AI가 서로의 이전 답변까지 보고 답합니다.

```
#ai-council
 └ 나: CAA에서 Harness 경로 최적화 어떻게 접근할까?
    ├ 🟠 Claude (18s)  ...
    ├ 🟢 GPT (25s)     ...
    ├ 🔵 Gemini (12s)  ...
    ├ ⚖️ 종합 (Claude) 1. 합의 2. 이견 3. 최종 권고
    └ 나: Gemini 의견에 대해 다른 AI들은 어떻게 봐?   ← 후속 질문
```

---

## 1. 사전 준비 (bot을 돌릴 PC)

Node.js 20 이상이 필요합니다. 세 CLI를 설치하고 **각각 한 번씩 로그인**하세요.

```bash
npm i -g @anthropic-ai/claude-code   # Claude Pro/Max 구독
npm i -g @openai/codex               # ChatGPT Plus/Pro 구독
npm i -g @google/gemini-cli          # Google 계정 (무료 사용량 있음)

claude      # 로그인 후 종료
codex       # "Sign in with ChatGPT" 선택
gemini      # "Login with Google" 선택
```

## 2. Slack App 만들기 (약 5분)

1. https://api.slack.com/apps → **Create New App** → **From a manifest** → 워크스페이스 선택
2. `slack-app-manifest.yaml` 내용을 붙여넣고 생성
3. **Basic Information → App-Level Tokens → Generate**: scope는 `connections:write`, 발급된 `xapp-...` 값이 `SLACK_APP_TOKEN`
4. **Install App → Install to Workspace**: 발급된 `xoxb-...` 값이 `SLACK_BOT_TOKEN`
5. Slack에서 `#ai-council` 채널을 만들고 `/invite @AI Council` 실행
6. 채널 ID 확인: 채널 이름 클릭 → 맨 아래 `C0...` / 내 user ID 확인: 프로필 → ⋮ → **Copy member ID**

## 3. 실행

```bash
cd ai-council
npm install
cp .env.example .env      # Windows: copy .env.example .env
# .env 에 토큰, COUNCIL_CHANNEL_IDS, ALLOWED_USER_IDS 입력

npm run check             # 세 CLI가 모두 PASS 인지 확인
npm start
```

`#ai-council` 채널에 질문하면 됩니다. 다른 채널에서는 `@AI Council 질문` 형태로 부르거나, bot에게 DM을 보내도 됩니다.

## 4. 설정 (`.env`)

| 변수 | 설명 |
|---|---|
| `COUNCIL_CHANNEL_IDS` | 모든 메시지에 반응할 채널 (쉼표로 구분). 비우면 DM과 @mention에만 반응 |
| `ALLOWED_USER_IDS` | **반드시 설정 권장.** 내 PC의 CLI를 실행하므로 본인만 사용하도록 제한 |
| `COUNCIL_MEMBERS` | `claude,gpt,gemini` 중 사용할 모델 |
| `COUNCIL_CHAIR` | 최종 종합을 맡을 모델 (기본 `claude`) |
| `COUNCIL_REVIEW` | `true`면 익명 교차 평가 단계를 추가. 품질은 올라가지만 호출이 약 2배, 시간도 늘어남 |
| `CLI_TIMEOUT_SEC` | 모델별 응답 제한 시간 (기본 300초) |
| `*_BIN` | CLI가 PATH에 없을 때 전체 경로 지정 |

## 5. 동작 방식

1. **병렬 답변**: 세 CLI에 같은 프롬프트를 stdin으로 전달합니다 (긴 한글 프롬프트도 Windows에서 안전). 답변이 도착하는 대로 "⏳ 답변 생성 중..." 메시지가 실제 답변으로 바뀝니다.
2. **(선택) 교차 평가**: 답변을 A/B/C로 익명화한 뒤 각 모델이 서로를 평가합니다.
3. **종합**: 의장 모델이 합의점, 이견, 최종 권고, 남은 검증 과제를 정리합니다.
4. **후속 질문**: 스레드 전체를 대화 기록으로 재구성해 전달합니다. 최근 `MAX_CONTEXT_CHARS`자까지만 넘깁니다.

각 CLI는 빈 `workspace/` 폴더에서 실행되고, 로컬 파일을 읽지 않도록 지시합니다. Codex는 `--sandbox read-only`로 실행됩니다.

## 6. 문제 해결

| 증상 | 조치 |
|---|---|
| `'codex' not found` | `.env`에 `CODEX_BIN`을 전체 경로로 지정 (Windows: `where codex`로 확인) |
| `Timed out` | 해당 CLI에 로그인되어 있는지 직접 실행해 확인하고, 필요하면 `CLI_TIMEOUT_SEC`를 늘림 |
| Gemini `Please set an Auth method` | `gemini`를 한 번 실행해 Google 로그인 |
| 채널에서 반응 없음 | bot이 채널에 초대됐는지, 채널 ID와 `ALLOWED_USER_IDS`가 맞는지 확인 |
| CLI 업데이트 후 깨짐 | `<cli> --help`로 headless 옵션 확인 후 `src/config.ts`의 `args` 수정 |

## 7. 주의 사항

- **사용량 제한**: 각 구독 요금제에는 시간당·일일 사용량 제한이 있습니다. 질문 1회마다 4번 호출되고, 교차 평가를 켜면 7번 호출됩니다.
- **PC가 켜져 있어야** 동작합니다. 상시 운영이 필요하면 나중에 미니 PC나 서버로 옮기면 됩니다.
- CLI의 headless 옵션은 버전마다 바뀔 수 있습니다. 검증한 버전: Gemini CLI 0.65, Codex CLI 0.164.

## 개발

```bash
npm test          # unit tests (no Slack / CLI needed)
npm run typecheck
```

구조: `src/config.ts` (모델·CLI 설정) → `src/runner.ts` (CLI 실행) → `src/council.ts` (3단계 흐름) → `src/index.ts` (Slack 연결)
