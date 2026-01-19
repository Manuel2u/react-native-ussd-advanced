# react-native-ussd-advanced

Advanced USSD automation for React Native Android - The React Native equivalent of Flutter's `ussd_advanced`

## Features

- ✅ **Multi-session USSD** with automatic dialog handling
- ✅ **Accessibility Service** integration for reading responses
- ✅ **Dual SIM support** - Select which SIM to use
- ✅ **Retry logic** with configurable attempts
- ✅ **Event-driven** architecture with real-time callbacks
- ✅ **Full automation** - Send multiple messages in sequence
- ✅ **TypeScript** - Full type safety included
- ✅ **MIUI support** - Enhanced detection for Xiaomi devices

## Installation

```bash
npm install react-native-ussd-advanced
```

or

```bash
yarn add react-native-ussd-advanced
```

### Expo Installation

If you're using Expo, the setup is automatic! Just add the plugin to your `app.json` or `app.config.js`:

```json
{
  "expo": {
    "plugins": [
      "react-native-ussd-advanced"
    ]
  }
}
```

Then rebuild your app:

```bash
npx expo prebuild
# or
npx expo run:android
```

The Expo plugin will automatically:
- ✅ Add required Android permissions (`CALL_PHONE`, `READ_PHONE_STATE`)
- ✅ Configure the accessibility service
- ✅ Copy necessary XML resource files

**Note:** Make sure you have `@expo/config-plugins` installed in your Expo project (it's usually included by default).

## Android Setup (Bare React Native Only)

If you're using a bare React Native project (not Expo), follow these manual setup steps:

### 1. Add permissions to `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

### 2. Add accessibility service

Inside `<application>` tag in `android/app/src/main/AndroidManifest.xml`:

```xml
<service
  android:name="com.ussdadvanced.USSDService"
  android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
  android:exported="false">
  <intent-filter>
    <action android:name="android.accessibilityservice.AccessibilityService" />
  </intent-filter>
  <meta-data
    android:name="android.accessibilityservice"
    android:resource="@xml/ussd_service" />
</service>
```

**Note:** Expo users can skip this entire section as the plugin handles everything automatically.

## Usage

### Basic USSD (no response)

```typescript
import UssdAdvanced from 'react-native-ussd-advanced';

await UssdAdvanced.sendUssd('*123#', -1);
```

### Single Session (with response)

```typescript
const response = await UssdAdvanced.sendAdvancedUssd('*123*1*4*3#', -1);
console.log(response);
```

### Multi-session USSD

```typescript
const initialResponse = await UssdAdvanced.multisessionUssd('*170#', -1);
console.log(initialResponse);

const nextResponse = await UssdAdvanced.sendMessage('1');
console.log(nextResponse);

await UssdAdvanced.cancelSession();
```

### React Hook (Recommended)

```typescript
import { useUssd } from 'react-native-ussd-advanced';

function MyComponent() {
  const {
    runFullUssdSession,
    isSessionActive,
    currentResponse,
    isAccessibilityEnabled,
  } = useUssd({
    onUssdResponse: (response) => console.log('Response:', response),
    onUssdError: (error) => console.error('Error:', error),
    onSessionEnd: (message) => console.log('Session ended:', message),
  });

  const handleUssd = async () => {
    const result = await runFullUssdSession('*123*1*2*3#', 0);
  };

  return (
    <View>
      <Text>Session Active: {isSessionActive ? 'Yes' : 'No'}</Text>
      <Text>Response: {currentResponse}</Text>
      <Button onPress={handleUssd} title="Run USSD" />
    </View>
  );
}
```

### Parsing Complex USSD Codes

```typescript
import { parseUssdCode } from 'react-native-ussd-advanced';

const { code, messages } = parseUssdCode('*123*1*2*3#');

console.log(code);
console.log(messages);
```

## API Reference

### Methods

#### `sendUssd(code: string, subscriptionId?: number): Promise<boolean>`
Basic USSD without response.

#### `sendAdvancedUssd(code: string, subscriptionId?: number): Promise<string | null>`
Single session with response (Android 8+).

#### `multisessionUssd(code: string, subscriptionId?: number): Promise<string | null>`
Start multi-session USSD.

#### `sendMessage(text: string): Promise<string | null>`
Send response in active session.

#### `cancelSession(): Promise<void>`
Cancel active USSD session.

#### `isAccessibilityEnabled(): Promise<boolean>`
Check if accessibility service is enabled.

#### `openAccessibilitySettings(): Promise<void>`
Open accessibility settings.

#### `getDeviceInfo(): Promise<DeviceInfo>`
Get device information (useful for MIUI detection).

### Helpers

#### `parseUssdCode(fullCode: string): { code: string; messages: string[] }`
Parse complex USSD codes into base code and messages.

### SIM Selection

Use `subscriptionId` parameter:
- `-1`: Default SIM (system setting)
- `0`: First SIM
- `1`: Second SIM

## Xiaomi/MIUI Devices

For Xiaomi devices, users need to:
1. Enable accessibility service
2. Disable battery optimization for your app
3. Enable autostart permission

## License

MIT

## Credits

Inspired by Flutter's [ussd_advanced](https://github.com/EddieKamau/ussd_advanced) package.
