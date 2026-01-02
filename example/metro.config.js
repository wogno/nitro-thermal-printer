const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const projectRoot = __dirname;
const monorepoRoot = path.resolve(projectRoot, '..');

const config = getDefaultConfig(projectRoot);

// Watch the local library
config.watchFolders = [monorepoRoot];

// Resolve modules from both locations
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
  path.resolve(monorepoRoot, 'node_modules'),
];

// Ensure the library resolves correctly
config.resolver.extraNodeModules = {
  'react-native-thermal-receipt-printer-image-qr': monorepoRoot,
};

module.exports = config;
