cmake_minimum_required(VERSION 3.22.1)
project(bergz LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe comes in as a prefab package from Gradle (com.google.oboe:oboe).
find_package(oboe REQUIRED CONFIG)

add_library(bergz SHARED AudioEngine.cpp)
target_compile_options(bergz PRIVATE -O3 -ffast-math -Wall)
target_link_libraries(bergz oboe::oboe log android)
