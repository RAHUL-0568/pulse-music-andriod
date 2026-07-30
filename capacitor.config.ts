import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'tf.pulsemusic.music',
    appName: 'Pulse Music',
    webDir: '../web-frontend/dist',
    assets: {
        iconBackgroundColor: '#000000',
        iconBackgroundColorDark: '#000000',
        splashBackgroundColor: '#000000',
        splashBackgroundColorDark: '#000000',
    },
    server: {
        cleartext: true
    },
    plugins: {
        GoogleAuth: {
            scopes: ['profile', 'email'],
            serverClientId: '217687474888-5151b2u1grl7a62omop5ui48995f4eto.apps.googleusercontent.com',
            forceCodeForRefreshToken: true,
        }
    }
};

export default config;

