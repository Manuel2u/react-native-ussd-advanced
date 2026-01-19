const { withAndroidManifest, withDangerousMod } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

/**
 * Expo config plugin for react-native-ussd-advanced
 * Adds required permissions and accessibility service configuration
 */
const withUssdAdvanced = (config) => {
  // Add Android permissions and service
  config = withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults;
    const { manifest } = androidManifest;

    if (!manifest) {
      return config;
    }

    // Add permissions
    if (!manifest['uses-permission']) {
      manifest['uses-permission'] = [];
    }

    const permissions = manifest['uses-permission'];
    const permissionNames = permissions.map((p) => p.$['android:name']);

    // Add CALL_PHONE permission
    if (!permissionNames.includes('android.permission.CALL_PHONE')) {
      permissions.push({
        $: { 'android:name': 'android.permission.CALL_PHONE' },
      });
    }

    // Add READ_PHONE_STATE permission
    if (!permissionNames.includes('android.permission.READ_PHONE_STATE')) {
      permissions.push({
        $: { 'android:name': 'android.permission.READ_PHONE_STATE' },
      });
    }

    // Add accessibility service
    if (!manifest.application) {
      manifest.application = [{}];
    }

    const application = manifest.application[0];
    if (!application.service) {
      application.service = [];
    }

    // Check if service already exists
    const services = application.service;
    const serviceExists = services.some(
      (service) => service.$ && service.$['android:name'] === '.USSDService'
    );

    if (!serviceExists) {
      services.push({
        $: {
          'android:name': '.USSDService',
          'android:permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE',
          'android:exported': 'false',
        },
        'intent-filter': [
          {
            action: [
              {
                $: {
                  'android:name': 'android.accessibilityservice.AccessibilityService',
                },
              },
            ],
          },
        ],
        'meta-data': [
          {
            $: {
              'android:name': 'android.accessibilityservice',
              'android:resource': '@xml/ussd_service',
            },
          },
        ],
      });
    }

    return config;
  });

  // Copy XML resource files
  config = withDangerousMod(config, [
    'android',
    async (config) => {
      const projectRoot = config.modRequest.projectRoot;
      const androidResPath = path.join(
        projectRoot,
        'android',
        'app',
        'src',
        'main',
        'res'
      );

      // Find the plugin directory (could be in node_modules or local)
      let pluginRoot = __dirname;
      const nodeModulesPath = path.join(projectRoot, 'node_modules', 'react-native-ussd-advanced');
      if (fs.existsSync(nodeModulesPath)) {
        pluginRoot = nodeModulesPath;
      }

      // Ensure res directory exists
      const xmlDir = path.join(androidResPath, 'xml');
      const valuesDir = path.join(androidResPath, 'values');

      if (!fs.existsSync(xmlDir)) {
        fs.mkdirSync(xmlDir, { recursive: true });
      }
      if (!fs.existsSync(valuesDir)) {
        fs.mkdirSync(valuesDir, { recursive: true });
      }

      // Copy ussd_service.xml
      const pluginXmlPath = path.join(
        pluginRoot,
        'android',
        'src',
        'main',
        'res',
        'xml',
        'ussd_service.xml'
      );
      const appXmlPath = path.join(xmlDir, 'ussd_service.xml');

      if (fs.existsSync(pluginXmlPath)) {
        fs.copyFileSync(pluginXmlPath, appXmlPath);
      }

      // Merge strings.xml
      const pluginStringsPath = path.join(
        pluginRoot,
        'android',
        'src',
        'main',
        'res',
        'values',
        'strings.xml'
      );
      const appStringsPath = path.join(valuesDir, 'strings.xml');

      if (fs.existsSync(pluginStringsPath)) {
        const pluginContent = fs.readFileSync(pluginStringsPath, 'utf-8');
        const ussdStringMatch = pluginContent.match(
          /<string name="ussd_accessibility_service_description">.*?<\/string>/s
        );

        if (ussdStringMatch) {
          if (fs.existsSync(appStringsPath)) {
            // Read existing strings.xml and merge
            const existingContent = fs.readFileSync(appStringsPath, 'utf-8');
            
            // Check if ussd_accessibility_service_description already exists
            if (!existingContent.includes('ussd_accessibility_service_description')) {
              // Append the USSD-related string before </resources>
              const updatedContent = existingContent.replace(
                '</resources>',
                `    ${ussdStringMatch[0]}\n</resources>`
              );
              fs.writeFileSync(appStringsPath, updatedContent);
            }
          } else {
            // Create new strings.xml with the USSD string
            const newContent = `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    ${ussdStringMatch[0]}\n</resources>\n`;
            fs.writeFileSync(appStringsPath, newContent);
          }
        }
      }

      return config;
    },
  ]);

  return config;
};

module.exports = withUssdAdvanced;
