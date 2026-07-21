# llm-gateway

멀티 공급자 LLM 게이트웨이. 모든 LLM 호출이 통과하는 표준 진입점으로, 토큰 카운팅·컨텍스트 예산·디코딩 파라미터·스트리밍·사용량 관측을 한 곳에서 담당한다.

사용자가 **로컬에서 자기 API 키로 실행한다(BYO-key)**. 이 레포에는 어떤 공급자 키도 포함되지 않는다.

## 왜 만드나

- 팀·서비스마다 공급자 SDK를 직접 호출하면 토큰 카운팅, 컨텍스트 초과 처리, 디코딩 파라미터, 비용 집계가 호출부마다 흩어진다.
- 이 정책들을 공급자와 무관한 코어로 모으고, 공급자(Claude/GPT/Gemini)는 교체 가능한 어댑터로 분리한다.

## 아키텍처

헥사고날(포트 & 어댑터) + Gradle 멀티모듈. 의존성은 항상 `core-domain`을 향한다.

```
bootstrap ─┬─▶ adapter-web ───────┐
           ├─▶ adapter-tokenizer-* ┼─▶ core-domain ◀── (의존 없음)
           └─▶ application ────────┘
```

| 모듈 | 책임 | 프레임워크 |
|------|------|-----------|
| `core-domain` | 도메인 모델·정책·포트. 토큰·컨텍스트 예산·디코딩 프리셋·공급자 포트 | 순수 Kotlin |
| `application` | 유스케이스 오케스트레이션 (카운트→예산→공급자→스트림→집계) | 코루틴 |
| `adapter-web` | 인바운드 REST/SSE, TTFT/TPOT 관측 | Spring WebFlux |
| `adapter-tokenizer-bpe` | 토크나이저 구현 (`Tokenizer` 포트) | 순수 Kotlin |
| `bootstrap` | Spring Boot 진입점, DI 조립, 키 로딩 | Spring Boot |

설계 상세: [docs/architecture.md](docs/architecture.md)

## 실행

```bash
cp .env.example .env      # 값을 채운다. .env 는 커밋되지 않는다.
./gradlew :bootstrap:bootRun
```

기본 공급자는 `fake`다. 키 없이 스트리밍 배선을 확인할 수 있다.

실제 Anthropic 으로 전환하려면 `.env` 에 본인 키를 넣고 공급자를 바꾼다.

```bash
# .env  (anthropic)
LLM_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...   # 본인 키. 커밋 금지
ANTHROPIC_MODEL=claude-opus-4-8

# 또는 openai
# LLM_PROVIDER=openai
# OPENAI_API_KEY=sk-...
# OPENAI_MODEL=gpt-4o-mini
```

### 스트리밍 호출 (SSE)

```bash
curl -N -X POST http://localhost:8080/v1/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"USER","content":"안녕"}],"preset":"FACTUAL_QA"}'
```

### 비스트리밍 호출

```bash
curl -X POST http://localhost:8080/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"USER","content":"안녕"}]}'
```

## 보안

- 키는 `.env`(gitignore)와 환경변수로만 주입한다. 레포에는 `.env.example`(빈 값)만 있다.
- 어떤 공급자 키도 소스·설정·테스트에 하드코딩하지 않는다.
- 자기 키로 이 서비스를 공개 배포하지 않는다. 각자 로컬에서 자기 키로 실행한다.

## 상태

- [x] 멀티모듈 뼈대 + 공개 안전 기본 파일
- [x] core-domain (토큰·컨텍스트 예산·디코딩 프리셋·포트)
- [x] walking skeleton: fake 공급자로 SSE 스트리밍 배선 검증
- [x] adapter-provider-anthropic (Anthropic Messages API 스트리밍, BYO-key)
- [x] adapter-tokenizer-bpe 실제 byte-level BPE 구현 (학습 + 인코딩/디코딩)
- [x] adapter-provider-openai (Chat Completions 스트리밍, 프리셋→temperature/top_p 번역)
- [x] Rate Limiting (인메모리 토큰 버킷, X-Client-Id 단위) + Fail-Open/Fail-Closed 정책
- [ ] adapter-provider-gemini
- [ ] Redis 분산 레이트 리밋 · 시맨틱 캐시

## 학습 배경

LLM 내부 개념(토크나이제이션·디코딩·prefill/decode 스트리밍)을 프로덕션 형태로 구현해 체득하는 프로젝트다. 개념 노트와 개발로그는 [docs/](docs/)에 축적한다.

## 라이선스

MIT
