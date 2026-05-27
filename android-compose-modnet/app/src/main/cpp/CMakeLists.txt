cmake_minimum_required(VERSION 3.22.1)
project("modnet_hybrid_lab")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fomit-frame-pointer -Wall -Wextra -Wno-unused-parameter")

find_library(log-lib log)

add_library(
        bg_refiner
        SHARED
        onnx_decryptor.cpp
        mask_fusion.cpp
)

target_link_libraries(
        bg_refiner
        ${log-lib}
)
