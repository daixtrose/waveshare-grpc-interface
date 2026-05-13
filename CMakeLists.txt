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

# ── Cross-compile fix-up (Trixie aarch64 sysroot) ───────────────
# libgrpc++-dev in the Trixie sysroot ships the libs but NOT the
# host-side codegen tools that gRPCConfig.cmake's imported targets
# expect at /opt/trixie-arm64-sysroot/usr/bin/{grpc_cpp_plugin,protoc}.
# Symlink them in from the host's apt-installed binaries, then
# override IMPORTED_LOCATION after find_package so add_custom_command's
# protoc invocation runs the host x86_64 binary directly.
if(CMAKE_CROSSCOMPILING AND DEFINED CMAKE_SYSROOT
    AND EXISTS "${CMAKE_SYSROOT}/usr/lib/aarch64-linux-gnu")
    foreach(_tool protoc grpc_cpp_plugin)
        find_program(_host_${_tool} ${_tool}
            PATHS /usr/bin /usr/local/bin
            NO_DEFAULT_PATH NO_CMAKE_FIND_ROOT_PATH)
        if(_host_${_tool})
            set(_sysroot_${_tool} "${CMAKE_SYSROOT}/usr/bin/${_tool}")
            if(NOT EXISTS "${_sysroot_${_tool}}")
                file(CREATE_LINK "${_host_${_tool}}"
                                 "${_sysroot_${_tool}}" SYMBOLIC)
                message(STATUS "Cross-compile: symlinked ${_sysroot_${_tool}} → ${_host_${_tool}}")
            endif()
        else()
            message(WARNING "Cross-compile: host ${_tool} not found in /usr/bin")
        endif()
    endforeach()

    # Touch placeholders for unused gRPC plugins that gRPCTargets.cmake
    # validates the existence of (php/csharp/etc. — we only need C++).
    foreach(_unused_plugin grpc_csharp_plugin grpc_node_plugin
            grpc_objective_c_plugin grpc_php_plugin grpc_python_plugin
            grpc_ruby_plugin)
        set(_path "${CMAKE_SYSROOT}/usr/bin/${_unused_plugin}")
        if(NOT EXISTS "${_path}")
            file(TOUCH "${_path}")
        endif()
    endforeach()
endif()

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
    # Disable install targets — protobuf's install(EXPORT) references abseil
    # targets that aren't in any export set when built as a FetchContent subproject
    set(gRPC_INSTALL OFF CACHE BOOL "" FORCE)
    set(protobuf_INSTALL OFF CACHE BOOL "" FORCE)
    set(utf8_range_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(grpc_proj)

    # When built from source, the plugin targets have different names
    if(NOT TARGET gRPC::grpc_cpp_plugin)
        add_executable(gRPC::grpc_cpp_plugin ALIAS grpc_cpp_plugin)
    endif()
endif()

# ── Cross-compile: redirect imported codegen targets to host binaries ──
if(CMAKE_CROSSCOMPILING)
    if(TARGET protobuf::protoc AND DEFINED _host_protoc)
        set_property(TARGET protobuf::protoc
                     PROPERTY IMPORTED_LOCATION "${_host_protoc}")
        message(STATUS "Cross-compile: protobuf::protoc → ${_host_protoc}")
    endif()
    if(TARGET gRPC::grpc_cpp_plugin AND DEFINED _host_grpc_cpp_plugin)
        set_property(TARGET gRPC::grpc_cpp_plugin
                     PROPERTY IMPORTED_LOCATION "${_host_grpc_cpp_plugin}")
        message(STATUS "Cross-compile: gRPC::grpc_cpp_plugin → ${_host_grpc_cpp_plugin}")
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
