require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = package['name']
  s.version      = package['version']
  s.summary      = package['description']
  s.license      = package['license']

  # Module name must match nitro.json iosModuleName for Swift/C++ interop
  s.module_name  = 'NitroThermalPrinter'

  s.authors      = package['author']
  s.homepage     = package['homepage']
  s.platforms    = { :ios => '15.0' }

  s.source       = { :git => "https://github.com/thiendangit/react-native-thermal-receipt-printer-image-qr", :tag => "v#{s.version}" }

  # Source files - Swift sources and PrinterSDK headers
  s.source_files = [
    "ios/Sources/**/*.swift",
    "ios/PrinterSDK/*.h"
  ]

  # Preserve modulemap for Swift/C interop
  s.preserve_paths = "ios/PrinterSDK/module.modulemap"

  # Public headers
  s.public_header_files = "ios/PrinterSDK/*.h"

  # Swift version
  s.swift_version = '5.9'

  # Build settings
  s.pod_target_xcconfig = {
    'CLANG_CXX_LANGUAGE_STANDARD' => 'c++20',
    'CLANG_CXX_LIBRARY' => 'libc++',
    'HEADER_SEARCH_PATHS' => '"$(PODS_TARGET_SRCROOT)/ios/PrinterSDK"',
    'DEFINES_MODULE' => 'YES',
    'SWIFT_INCLUDE_PATHS' => '"$(PODS_TARGET_SRCROOT)/ios/PrinterSDK"',
    'OTHER_SWIFT_FLAGS' => '-no-verify-emitted-module-interface',
    # Only link PrinterSDK on device builds
    'OTHER_LDFLAGS[sdk=iphoneos*]' => '-lPrinterSDK',
    'LIBRARY_SEARCH_PATHS[sdk=iphoneos*]' => '$(PODS_TARGET_SRCROOT)/ios/PrinterSDK'
  }

  # Vendored libraries - static library for device builds
  s.vendored_libraries = 'ios/PrinterSDK/libPrinterSDK.a'

  # Dependencies
  s.dependency 'React-Core'

  # Frameworks
  s.frameworks = 'CoreBluetooth', 'Foundation', 'UIKit'

  # Add C++ standard library
  s.libraries = 'c++'

  # Requires ARC
  s.requires_arc = true

  # Load and apply Nitrogen autolinking
  load 'nitrogen/generated/ios/NitroThermalPrinter+autolinking.rb'
  add_nitrogen_files(s)
end
