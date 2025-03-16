# FilterCamera - Native库CMake配置
# 编译美颜算法和位图处理Native库

cmake_minimum_required(VERSION 3.22.1)

project("filter")

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加Native库
add_library(
    filter
    SHARED
    # JNI接口
    MagicJni.cpp
    # 美颜算法
    beautify/MagicBeautify.cpp
    # 位图处理
    bitmap/BitmapOperation.cpp
    bitmap/Conversion.cpp
)

# 查找NDK系统库
find_library(
    log-lib
    log
)

find_library(
    jnigraphics-lib
    jnigraphics
)

find_library(
    android-lib
    android
)

# 链接库
target_link_libraries(
    filter
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib}
)

# 编译选项
target_compile_options(
    filter
    PRIVATE
    -Wall
    -O3
    -ffast-math
)
