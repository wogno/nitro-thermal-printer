cmake_minimum_required(VERSION 3.22.1)
project(NitroThermalPrinter)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find packages
find_package(ReactAndroid REQUIRED CONFIG)
find_package(fbjni REQUIRED CONFIG)
find_package(react-native-nitro-modules REQUIRED CONFIG)

# Get nitrogen generated sources
file(GLOB_RECURSE NITROGEN_SOURCES
    "${CMAKE_SOURCE_DIR}/../../../../../nitrogen/generated/android/c++/*.cpp"
)

# Create shared library
add_library(${CMAKE_PROJECT_NAME} SHARED
    ${NITROGEN_SOURCES}
    cpp-adapter.cpp
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
