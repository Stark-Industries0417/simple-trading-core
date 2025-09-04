rootProject.name = "simple-trading-core"

// 모듈 포함 - 의존성 순서대로 정렬
include(
    "common-library",
    "market-data-generator",
    "order",
    "matching",
    "account",
    "cdc",
    "app"
)

buildCache {
    local {
        isEnabled = true
    }
}