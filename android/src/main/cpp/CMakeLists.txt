cmake_minimum_required(VERSION 3.22.1)
project(NitroThermalPrinter)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find packages
find_package(ReactAndroid REQUIRED CONFIG)
find_package(fbjni REQUIRED CONFIG)
find_package(react-native-nitro-modules REQUIRED CONFIG)

# Get nitrogen generated sources - Android C++
file(GLOB_RECURSE NITROGEN_ANDROID_SOURCES
    "${CMAKE_SOURCE_DIR}/../../../../../nitrogen/generated/android/c++/*.cpp"
)

# Get nitrogen generated sources - Shared C++
file(GLOB_RECURSE NITROGEN_SHARED_SOURCES
    "${CMAKE_SOURCE_DIR}/../../../../../nitrogen/generated/shared/c++/*.cpp"
)

# Include directories for nitrogen headers
set(NITROGEN_ANDROID_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/../../../../../nitrogen/generated/android/c++")
set(NITROGEN_SHARED_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/../../../../../nitrogen/generated/shared/c++")

# Create shared library
add_library(${CMAKE_PROJECT_NAME} SHARED
    ${NITROGEN_ANDROID_SOURCES}
    ${NITROGEN_SHARED_SOURCES}
    cpp-adapter.cpp
)

# Include directories
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${NITROGEN_ANDROID_INCLUDE_DIR}
    ${NITROGEN_SHARED_INCLUDE_DIR}
)

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
    ReactAndroid::jsi
    ReactAndroid::react_nativemodule_core
    ReactAndroid::turbomodulejsijni
    fbjni::fbjni
    react-native-nitro-modules::NitroModules
    android
    log
)
