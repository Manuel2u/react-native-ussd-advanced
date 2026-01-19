import { useEffect, useState, useCallback, useRef } from 'react';
import { NativeModules, NativeEventEmitter, Platform, AppState, AppStateStatus } from 'react-native';
import type { UssdOptions } from './types';
import { parseUssdCode } from './helpers';

const { UssdModule } = NativeModules;

export function useUssd(options: UssdOptions = {}) {
  const { onUssdResponse, onUssdError, onSessionEnd } = options;

  const [isSessionActive, setIsSessionActive] = useState(false);
  const [currentResponse, setCurrentResponse] = useState<string>('');
  const [isAccessibilityEnabled, setIsAccessibilityEnabled] = useState(false);

  const eventEmitterRef = useRef<NativeEventEmitter | null>(null);

  const checkAccessibilityEnabled = useCallback(async (): Promise<boolean> => {
    if (Platform.OS !== 'android' || !UssdModule) return false;

    try {
      const enabled = await UssdModule.isAccessibilityEnabled();
      setIsAccessibilityEnabled(enabled);
      return enabled;
    } catch (error) {
      console.error('Error checking accessibility service:', error);
      return false;
    }
  }, []);

  const openAccessibilitySettings = useCallback(async (): Promise<void> => {
    if (Platform.OS !== 'android' || !UssdModule) return;

    try {
      await UssdModule.openAccessibilitySettings();
    } catch (error) {
      console.error('Error opening accessibility settings:', error);
    }
  }, []);

  const sendUssd = useCallback(
    async (code: string, subscriptionId: number = -1): Promise<boolean> => {
      if (Platform.OS !== 'android' || !UssdModule) return false;

      try {
        await UssdModule.sendUssd(code, subscriptionId);
        return true;
      } catch (error) {
        console.error('Error sending USSD:', error);
        onUssdError?.(String(error));
        return false;
      }
    },
    [onUssdError]
  );

  const sendAdvancedUssd = useCallback(
    async (code: string, subscriptionId: number = -1): Promise<string | null> => {
      if (Platform.OS !== 'android' || !UssdModule) return null;

      try {
        const response = await UssdModule.sendAdvancedUssd(code, subscriptionId);
        if (response) {
          setCurrentResponse(response);
          onUssdResponse?.(response);
        }
        return response;
      } catch (error) {
        console.error('Error sending advanced USSD:', error);
        onUssdError?.(String(error));
        return null;
      }
    },
    [onUssdResponse, onUssdError]
  );

  const startMultisessionUssd = useCallback(
    async (code: string, subscriptionId: number = -1): Promise<string | null> => {
      if (Platform.OS !== 'android' || !UssdModule) return null;

      try {
        setIsSessionActive(true);
        const response = await UssdModule.multisessionUssd(code, subscriptionId);
        if (response) {
          setCurrentResponse(response);
          onUssdResponse?.(response);
        }
        return response;
      } catch (error: any) {
        console.error('Error starting multi-session USSD:', error);
        setIsSessionActive(false);
        if (error?.code === 'ACCESSIBILITY_NOT_ENABLED') {
          onUssdError?.('Please enable accessibility service for USSD');
        } else {
          onUssdError?.(String(error?.message || error));
        }
        return null;
      }
    },
    [onUssdResponse, onUssdError]
  );

  const sendUssdResponse = useCallback(
    async (text: string): Promise<string | null> => {
      if (Platform.OS !== 'android' || !UssdModule) return null;

      try {
        const response = await UssdModule.sendUssdResponse(text);
        if (response) {
          setCurrentResponse(response);
          onUssdResponse?.(response);
        }
        return response;
      } catch (error) {
        console.error('Error sending USSD response:', error);
        onUssdError?.(String(error));
        return null;
      }
    },
    [onUssdResponse, onUssdError]
  );

  const cancelSession = useCallback(async (): Promise<void> => {
    if (Platform.OS !== 'android' || !UssdModule) return;

    try {
      await UssdModule.cancelUssdSession();
      setIsSessionActive(false);
      setCurrentResponse('');
    } catch (error) {
      console.error('Error canceling USSD session:', error);
    }
  }, []);

  const sendMessageWithRetry = useCallback(
    async (message: string, maxRetries: number = 3, retryDelay: number = 1500): Promise<string | null> => {
      for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
          const response = await sendUssdResponse(message);
          if (response) {
            return response;
          }
          return null;
        } catch (error: any) {
          const errorMsg = String(error?.message || error);

          if (errorMsg.includes('input') || errorMsg.includes('session') || errorMsg.includes('No active') || errorMsg.includes('TIMEOUT')) {
            if (attempt < maxRetries) {
              await new Promise(resolve => setTimeout(resolve, retryDelay));
              continue;
            }
          }
          throw error;
        }
      }
      return null;
    },
    [sendUssdResponse]
  );

  const sendMultipleMessages = useCallback(
    async (messages: string[]): Promise<string> => {
      let fullResponse = '';

      for (let i = 0; i < messages.length; i++) {
        const message = messages[i];
        const waitTime = i === 0 ? 1500 : 1000;
        await new Promise(resolve => setTimeout(resolve, waitTime));

        const response = await sendMessageWithRetry(message, 3, 1500);
        if (response) {
          fullResponse += '\n' + response;
        }
      }

      return fullResponse;
    },
    [sendMessageWithRetry]
  );

  const runFullUssdSession = useCallback(
    async (fullCode: string, subscriptionId: number = -1): Promise<string | null> => {
      if (Platform.OS !== 'android' || !UssdModule) return null;

      try {
        const { code, messages } = parseUssdCode(fullCode);

        setIsSessionActive(true);
        const initialResponse = await UssdModule.multisessionUssd(code, subscriptionId);

        if (initialResponse) {
          setCurrentResponse(initialResponse);
          onUssdResponse?.(initialResponse);
        }

        if (messages.length > 0) {
          const messagesResponse = await sendMultipleMessages(messages);
          return initialResponse + messagesResponse;
        }

        return initialResponse;
      } catch (error: any) {
        console.error('Error in full USSD session:', error);
        setIsSessionActive(false);
        onUssdError?.(String(error?.message || error));
        return null;
      }
    },
    [onUssdResponse, onUssdError, sendMultipleMessages]
  );

  useEffect(() => {
    if (Platform.OS !== 'android' || !UssdModule) return;

    try {
      eventEmitterRef.current = new NativeEventEmitter(UssdModule);

      const responseSubscription = eventEmitterRef.current.addListener('UssdResponse', (event: { message: string; sessionActive: boolean }) => {
        setCurrentResponse(event.message);
        setIsSessionActive(event.sessionActive);
        onUssdResponse?.(event.message);
      });

      const sessionEndSubscription = eventEmitterRef.current.addListener('UssdSessionEnd', (event: { message: string; sessionActive: boolean }) => {
        setIsSessionActive(false);
        onSessionEnd?.(event.message);
      });

      checkAccessibilityEnabled();

      const appStateSubscription = AppState.addEventListener('change', (nextAppState: AppStateStatus) => {
        if (nextAppState === 'active') {
          checkAccessibilityEnabled();
        }
      });

      return () => {
        responseSubscription.remove();
        sessionEndSubscription.remove();
        appStateSubscription.remove();
      };
    } catch (error) {
      console.error('Error setting up USSD event listeners:', error);
    }
  }, [checkAccessibilityEnabled, onUssdResponse, onSessionEnd]);

  return {
    isSessionActive,
    currentResponse,
    isAccessibilityEnabled,
    checkAccessibilityEnabled,
    openAccessibilitySettings,
    sendUssd,
    sendAdvancedUssd,
    startMultisessionUssd,
    sendUssdResponse,
    sendMultipleMessages,
    runFullUssdSession,
    cancelSession,
  };
}
