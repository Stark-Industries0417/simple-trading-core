rootProject.name = "simple-trading-core"

// 모듈 포함 - 의존성 순서대로 정렬
include(
    "common-library",
    "market-data-generator",
    "order",
    "matching",
    "account",
    "app"  // 메인 애플리케이션 모듈
)

// 빌드 캐시 활성화 (빌드 속도 개선)
buildCache {
    local {
        isEnabled = true
    }
}