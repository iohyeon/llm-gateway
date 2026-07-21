package com.llmgateway.core.decode

/**
 * 디코딩 파라미터. 출력층 확률 분포에서 토큰을 고르는 방식을 통제한다.
 * 학습 노트 08 (디코딩 전략) 참조.
 */
data class DecodeParams(
    val temperature: Double,
    val topP: Double,
    val topK: Int?,
)

/**
 * 작업 성격별 디코딩 프리셋. 팀·호출부마다 파라미터가 난립하지 않도록
 * 게이트웨이가 표준 프리셋을 소유한다.
 */
enum class DecodePreset(val params: DecodeParams) {
    /** 사실 QA·코드·추출. 재현성·정확성 우선. */
    FACTUAL_QA(DecodeParams(temperature = 0.0, topP = 1.0, topK = null)),

    /** 일반 대화. 균형. */
    BALANCED(DecodeParams(temperature = 0.7, topP = 0.95, topK = null)),

    /** 창작·발상. 다양성 우선. */
    CREATIVE(DecodeParams(temperature = 1.0, topP = 0.95, topK = 40)),
}
