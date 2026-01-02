"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.connectToHost = void 0;
var _reactNativePing = _interopRequireDefault(require("react-native-ping"));
function _interopRequireDefault(e) { return e && e.__esModule ? e : { default: e }; }
// @ts-ignore

const connectToHost = (ipAddress, timeout = 4000) => {
  return new Promise(async (resolve, reject) => {
    try {
      /**
       *
       * Get RTT (Round-trip delay time)
       *
       * @static
       * @param {string} ipAddress - For example : 8.8.8.8
       * @param {Object} option - Some optional operations
       * @param {number} option.timeout - timeout
       * @returns
       * @memberof Ping
       */
      await _reactNativePing.default.start(ipAddress, {
        timeout: timeout
      });
      resolve(true);
    } catch (error) {
      reject(error);
    }
  });
};
exports.connectToHost = connectToHost;
//# sourceMappingURL=net-connect.js.map