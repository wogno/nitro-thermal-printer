#include <jni.h>
#include <fbjni/fbjni.h>

// Include generated JNI headers from nitrogen
#include "JHybridBLEPrinterSpec.hpp"
#include "JHybridNetPrinterSpec.hpp"
#include "JHybridUSBPrinterSpec.hpp"

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    return facebook::jni::initialize(vm, [] {
        // Register all Nitro hybrid objects
        margelo::nitro::thermalprinter::JHybridBLEPrinterSpec::registerNatives();
        margelo::nitro::thermalprinter::JHybridNetPrinterSpec::registerNatives();
        margelo::nitro::thermalprinter::JHybridUSBPrinterSpec::registerNatives();
    });
}
