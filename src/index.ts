export { useUssd } from './useUssd';
export { parseUssdCode } from './helpers';
export {
  sendUssd,
  sendAdvancedUssd,
  multisessionUssd,
  sendMessage,
  cancelSession,
  isAccessibilityEnabled,
  openAccessibilitySettings,
  getDeviceInfo,
} from './UssdModule';
export type { UssdOptions, DeviceInfo } from './types';

import {
  sendUssd,
  sendAdvancedUssd,
  multisessionUssd,
  sendMessage,
  cancelSession,
  isAccessibilityEnabled,
  openAccessibilitySettings,
  getDeviceInfo,
} from './UssdModule';
import { parseUssdCode } from './helpers';

export default {
  sendUssd,
  sendAdvancedUssd,
  multisessionUssd,
  sendMessage,
  cancelSession,
  isAccessibilityEnabled,
  openAccessibilitySettings,
  getDeviceInfo,
  parseUssdCode,
};
