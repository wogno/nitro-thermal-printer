require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = package['name']
  s.version      = package['version']
  s.summary      = package['description']
  s.license      = package['license']

  s.authors      = package['author']
  s.homepage     = package['homepage']
  s.platforms    = { :ios => '15.0' }

  s.source       = { :git => "https://github.com/thiendangit/react-native-thermal-receipt-printer-image-qr", :tag => "v#{s.version}" }

  # Source files
  s.source_files = [
    "ios/**/*.{h,m,mm,swift}",
    "nitrogen/generated/ios/**/*.{h,hpp,cpp,swift}",
    "nitrogen/generated/shared/**/*.{h,hpp,cpp}"
  ]

  # Public headers
  s.public_header_files = "ios/**/*.h"

  # Swift version
  s.swift_version = '5.9'

  # Vendored libraries (PrinterSDK)
  s.ios.vendored_libraries = "ios/PrinterSDK/libPrinterSDK.a"

  # Build settings
  s.pod_target_xcconfig = {
    'CLANG_CXX_LANGUAGE_STANDARD' => 'c++20',
    'HEADER_SEARCH_PATHS' => '"$(PODS_TARGET_SRCROOT)/ios/PrinterSDK" "$(PODS_TARGET_SRCROOT)/nitrogen/generated/ios" "$(PODS_TARGET_SRCROOT)/nitrogen/generated/shared"',
    'DEFINES_MODULE' => 'YES',
    'SWIFT_OBJC_BRIDGING_HEADER' => '$(PODS_TARGET_SRCROOT)/ios/NitroThermalPrinter-Bridging-Header.h'
  }

  s.user_target_xcconfig = {
    'HEADER_SEARCH_PATHS' => '"$(PODS_ROOT)/Headers/Public/react-native-thermal-receipt-printer-image-qr"'
  }

  # Dependencies
  s.dependency 'React-Core'
  s.dependency 'NitroModules'

  # Frameworks
  s.frameworks = 'CoreBluetooth', 'Foundation', 'UIKit'

  # Requires ARC
  s.requires_arc = true
end
