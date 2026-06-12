cmake_minimum_required(VERSION 3.22.1)

project("skiashift")

# Add ByteHook AAR header include path
# The header and lib are provided by com.bytedance.android:bytehook
# CMake will link them via PREFAB if we enable it in build.gradle
# Wait, Prefab needs to be enabled in build.gradle.kts for ByteHook

add_library(
        skiashift
        SHARED
        skiashift.cpp)

find_library(
        log-lib
        log)

find_package(bytehook REQUIRED CONFIG)
find_package(shadowhook REQUIRED CONFIG)

# We also need to link against bytehook and shadowhook
target_link_libraries(
        skiashift
        ${log-lib}
        bytehook::bytehook
        shadowhook::shadowhook)
