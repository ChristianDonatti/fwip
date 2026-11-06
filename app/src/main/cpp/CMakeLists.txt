# MOBI and AZW3 books: libmobi (LGPL-3.0-or-later, https://github.com/bfabiszewski/libmobi, v0.12)
# rebuilds the book's HTML, styles, images and OPF; Kotlin packs them as an EPUB.
cmake_minimum_required(VERSION 3.22.1)
project(fwipmobi C)

file(GLOB MOBI_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/libmobi/*.c)
list(REMOVE_ITEM MOBI_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/libmobi/miniz.c)

add_library(miniz OBJECT ${CMAKE_CURRENT_SOURCE_DIR}/libmobi/miniz.c)
target_compile_definitions(miniz PRIVATE MINIZ_NO_STDIO MINIZ_NO_ZLIB_COMPATIBLE_NAMES MINIZ_NO_TIME
        MINIZ_NO_ARCHIVE_APIS MINIZ_NO_ARCHIVE_WRITING_APIS _POSIX_C_SOURCE=200112L)
set_target_properties(miniz PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(fwipmobi SHARED mobi_jni.c ${MOBI_SOURCES} $<TARGET_OBJECTS:miniz>)
target_compile_definitions(fwipmobi PRIVATE
        PACKAGE_VERSION="0.12" USE_XMLWRITER USE_MINIZ HAVE_UNISTD_H HAVE_STRDUP
        MOBI_INLINE=inline HAVE_ATTRIBUTE_NORETURN)
target_compile_options(fwipmobi PRIVATE -O2 -w)
target_include_directories(fwipmobi PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/libmobi)
target_link_options(fwipmobi PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(fwipmobi log)
