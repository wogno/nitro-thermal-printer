#include <jni.h>
#include <fbjni/fbjni.h>
#include <NitroModules/HybridObjectRegistry.hpp>
#include <NitroModules/DefaultConstructableObject.hpp>

// Include generated JNI headers from nitrogen
#include "JHybridBLEPrinterSpec.hpp"
#include "JFunc_void_ConnectionState.hpp"
#include "JHybridNetPrinterSpec.hpp"
#include "JFunc_void_double.hpp"
#include "JHybridUSBPrinterSpec.hpp"
#include "JFunc_void_USBDevice.hpp"
#include "JFunc_void.hpp"

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    return facebook::jni::initialize(vm, [] {
        using namespace margelo::nitro;
        using namespace margelo::nitro::thermalprinter;

        // Register native JNI methods
        JHybridBLEPrinterSpec::registerNatives();
        JFunc_void_ConnectionState_cxx::registerNatives();
        JHybridNetPrinterSpec::registerNatives();
        JFunc_void_double_cxx::registerNatives();
        JHybridUSBPrinterSpec::registerNatives();
        JFunc_void_USBDevice_cxx::registerNatives();
        JFunc_void_cxx::registerNatives();

        // Register Nitro Hybrid Objects in the HybridObjectRegistry
        HybridObjectRegistry::registerHybridObjectConstructor(
            "BLEPrinter",
            []() -> std::shared_ptr<HybridObject> {
                static DefaultConstructableObject<JHybridBLEPrinterSpec::javaobject> object("com/thermalprinter/HybridBLEPrinter");
                auto instance = object.create();
                return instance->cthis()->shared();
            }
        );
        HybridObjectRegistry::registerHybridObjectConstructor(
            "NetPrinter",
            []() -> std::shared_ptr<HybridObject> {
                static DefaultConstructableObject<JHybridNetPrinterSpec::javaobject> object("com/thermalprinter/HybridNetPrinter");
                auto instance = object.create();
                return instance->cthis()->shared();
            }
        );
        HybridObjectRegistry::registerHybridObjectConstructor(
            "USBPrinter",
            []() -> std::shared_ptr<HybridObject> {
                static DefaultConstructableObject<JHybridUSBPrinterSpec::javaobject> object("com/thermalprinter/HybridUSBPrinter");
                auto instance = object.create();
                return instance->cthis()->shared();
            }
        );
    });
}
