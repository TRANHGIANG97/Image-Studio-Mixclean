cmake_minimum_required(VERSION 3.22.1)
project("portraitblend")

# Required for Android 15+ (16 KB page size compatibility)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_library(
        portraitblend
        SHARED
        portrait_blend.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
        portraitblend
        ${jnigraphics-lib}
        ${log-lib}
)
