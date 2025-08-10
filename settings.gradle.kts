plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "simple-trading-core"

include("common-library")
include("market-data-generator")
include("order-module")
include("matching-module")
include("account-module")