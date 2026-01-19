export interface UssdOptions {
  onUssdResponse?: (response: string) => void;
  onUssdError?: (error: string) => void;
  onSessionEnd?: (message: string) => void;
}

export interface DeviceInfo {
  manufacturer: string;
  brand: string;
  model: string;
  isMIUI: boolean;
}
