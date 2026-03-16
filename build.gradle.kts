plugins {
    id("java-library")
    id("com.google.protobuf") version "0.9.4"
}

group = "com.waveshare"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    google()
    mavenCentral()
}

val grpcVersion = "1.72.0"
val protocVersion = "28.3"
val reactorGrpcVersion = "1.2.4"

dependencies {
    api("io.grpc:grpc-protobuf:$grpcVersion")
    api("io.grpc:grpc-stub:$grpcVersion")
    api("com.google.protobuf:protobuf-java:$protocVersion")

    // reactor-grpc stub generation
    api("com.salesforce.servicelibs:reactor-grpc-stub:$reactorGrpcVersion")

    // Required for Java 9+ (javax.annotation)
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protocVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        create("reactor") {
            artifact = "com.salesforce.servicelibs:reactor-grpc:$reactorGrpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
                create("reactor")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("proto")
        }
    }
}
