cmake_minimum_required(VERSION 3.25...4.2)

project(waveshare_grpc_interface
    VERSION 0.1.0
    DESCRIPTION "Shared protobuf/gRPC definitions for Waveshare Modbus Commander"
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

include(FetchContent)

# ── Find or fetch gRPC (brings protobuf + absl transitively) ────
find_package(gRPC CONFIG QUIET)
if(NOT gRPC_FOUND)
    message(STATUS "gRPC not found via find_package, fetching via FetchContent...")
    FetchContent_Declare(
        grpc_proj
        GIT_REPOSITORY https://github.com/grpc/grpc.git
        GIT_TAG v1.72.0
        GIT_SHALLOW TRUE
    )
    set(gRPC_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(gRPC_BUILD_CSHARP_EXT OFF CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(grpc_proj)

    # When built from source, the plugin targets have different names
    if(NOT TARGET gRPC::grpc_cpp_plugin)
        add_executable(gRPC::grpc_cpp_plugin ALIAS grpc_cpp_plugin)
    endif()
endif()

# ── Locate protoc and grpc_cpp_plugin ───────────────────────────
if(TARGET protobuf::protoc)
    get_target_property(_PROTOC_EXECUTABLE protobuf::protoc IMPORTED_LOCATION)
    if(NOT _PROTOC_EXECUTABLE)
        set(_PROTOC_EXECUTABLE $<TARGET_FILE:protobuf::protoc>)
    endif()
else()
    set(_PROTOC_EXECUTABLE $<TARGET_FILE:protoc>)
endif()

if(TARGET gRPC::grpc_cpp_plugin)
    get_target_property(_GRPC_CPP_PLUGIN gRPC::grpc_cpp_plugin IMPORTED_LOCATION)
    if(NOT _GRPC_CPP_PLUGIN)
        set(_GRPC_CPP_PLUGIN $<TARGET_FILE:gRPC::grpc_cpp_plugin>)
    endif()
else()
    set(_GRPC_CPP_PLUGIN $<TARGET_FILE:grpc_cpp_plugin>)
endif()

# ── Proto source ────────────────────────────────────────────────
set(PROTO_FILE "${CMAKE_CURRENT_SOURCE_DIR}/proto/waveshare/v1/waveshare_commander.proto")
set(PROTO_IMPORT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/proto")

set(PROTO_GEN_DIR "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${PROTO_GEN_DIR}")

set(PROTO_GENERATED_SOURCES
    "${PROTO_GEN_DIR}/waveshare/v1/waveshare_commander.pb.cc"
    "${PROTO_GEN_DIR}/waveshare/v1/waveshare_commander.pb.h"
    "${PROTO_GEN_DIR}/waveshare/v1/waveshare_commander.grpc.pb.cc"
    "${PROTO_GEN_DIR}/waveshare/v1/waveshare_commander.grpc.pb.h"
)

add_custom_command(
    OUTPUT ${PROTO_GENERATED_SOURCES}
    COMMAND ${_PROTOC_EXECUTABLE}
        --proto_path=${PROTO_IMPORT_DIR}
        --cpp_out=${PROTO_GEN_DIR}
        --grpc_out=${PROTO_GEN_DIR}
        --plugin=protoc-gen-grpc=${_GRPC_CPP_PLUGIN}
        waveshare/v1/waveshare_commander.proto
    DEPENDS ${PROTO_FILE}
    COMMENT "Generating C++ protobuf/gRPC sources from waveshare_commander.proto"
    VERBATIM
)

# ── Library target ──────────────────────────────────────────────
add_library(waveshare_grpc_proto
    ${PROTO_GENERATED_SOURCES}
)

target_include_directories(waveshare_grpc_proto
    PUBLIC
        ${PROTO_GEN_DIR}
)

# Link against gRPC and protobuf
if(TARGET gRPC::grpc++)
    target_link_libraries(waveshare_grpc_proto
        PUBLIC
            gRPC::grpc++
            protobuf::libprotobuf
    )
else()
    target_link_libraries(waveshare_grpc_proto
        PUBLIC
            grpc++
            libprotobuf
    )
endif()

# Suppress warnings in generated code
target_compile_options(waveshare_grpc_proto PRIVATE -w)
