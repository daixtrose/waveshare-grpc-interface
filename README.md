# waveshare-grpc-interface

Shared Protocol Buffers definitions for the Waveshare gRPC service and clients.

## Contents

- `proto/waveshare/v1/waveshare_commander.proto` — bidirectional streaming gRPC service definition for Waveshare Modbus devices

## Consumption

### C++ (CMake FetchContent)

```cmake
FetchContent_Declare(
    waveshare_grpc_interface
    GIT_REPOSITORY https://github.com/daixtrose/waveshare-grpc-interface.git
    GIT_TAG main
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(waveshare_grpc_interface)

# Links the generated C++ protobuf/gRPC sources
target_link_libraries(your_target PRIVATE waveshare_grpc_proto)
```

### Java (Gradle composite build)

Clone this repo adjacent to the Java project, then in `settings.gradle.kts`:

```kotlin
includeBuild("../waveshare-grpc-interface")
```

In `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.waveshare:waveshare-grpc-interface")
}
```

## License

MIT License — Copyright (c) 2026 Markus Werle. See [LICENSE](LICENSE).
