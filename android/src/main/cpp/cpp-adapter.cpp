#include <jni.h>
#include <fbjni/fbjni.h>
#include <NitroModules/HybridObjectRegistry.hpp>

// Include generated headers (will be created by nitrogen codegen)
// #include "HybridBLEPrinter.hpp"
// #include "HybridNetPrinter.hpp"
// #include "HybridUSBPrinter.hpp"

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    return facebook::jni::initialize(vm, [] {
        // Register hybrid objects here after nitrogen codegen
        // margelo::nitro::HybridObjectRegistry::registerHybridObjectConstructor(
        //     "HybridBLEPrinter",
        //     []() -> std::shared_ptr<margelo::nitro::HybridObject> {
        //         return std::make_shared<HybridBLEPrinter>();
        //     }
        // );
    });
}
