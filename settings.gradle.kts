rootProject.name = "simple-trading-core"

// 모듈 포함 - 의존성 순서대로 정렬
include(
    "common-library"
    // "market-data", 추후 분리
    // "order", 추후 분리
    // "matching", 추후 분리
    // "account", 추후 분리
)

// 빌드 캐시 활성화 (빌드 속도 개선)
buildCache {
    local {
        isEnabled = true
    }
}