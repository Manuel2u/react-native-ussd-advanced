import { NativeModules } from 'react-native';
import type { DeviceInfo } from './types';

const { UssdModule } = NativeModules;

export async function sendUssd(code: string, subscriptionId: number = -1): Promise<boolean> {
  if (!UssdModule) {
    throw new Error('UssdModule not available');
  }
  return UssdModule.sendUssd(code, subscriptionId);
}

export async function sendAdvancedUssd(code: string, subscriptionId: number = -1): Promise<string | null> {
  if (!UssdModule) {
    throw new Error('UssdModule not available');
  }
  return UssdModule.sendAdvancedUssd(code, subscriptionId);
}

export async function multisessionUssd(code: string, subscriptionId: number = -1): Promise<string | null> {
  if (!UssdModule) {
    throw new Error('UssdModule not available');
  }
  return UssdModule.multisessionUssd(code, subscriptionId);
}

export async function sendMessage(text: string): Promise<string | null> {
  if (!UssdModule) {
    throw new Error('UssdModule not available');
  }
  return UssdModule.sendUssdResponse(text);
}

export async function cancelSession(): Promise<void> {
  if (!UssdModule) {
    throw new Error('UssdModule not available');
  }
  return UssdModule.cancelUssdSession();
}

export async function isAccessibilityEnabled(): Promise<boolean> {
  if (!UssdModule) {
    return false;
  }
  return UssdModule.isAccessibilityEnabled();
}

export async function openAccessibilitySettings(): Promise<void> {
  if (!UssdModule) {
    throw new Error('UssdModule not available');
  }
  return UssdModule.openAccessibilitySettings();
}

export async function getDeviceInfo(): Promise<DeviceInfo> {
  if (!UssdModule) {
    return {
      manufacturer: 'unknown',
      brand: 'unknown',
      model: 'unknown',
      isMIUI: false,
    };
  }
  return UssdModule.getDeviceInfo();
}
