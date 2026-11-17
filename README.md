# PhoneMirrorControl — Custom Screen Mirroring & Remote Touch Control App

यह आपका कस्टम **Cross-Device Screen Mirroring और Remote Touch Control** एप्लीकेशन प्रोजेक्ट है। इसमें 3 मुख्य हिस्से हैं:

1. **Android App (`android-app/`)** — Kotlin में बना Android ऐप जो दोनों मोड में काम करता है:
   - **Host Mode (जिस फोन को देखना/कंट्रोल करना है):** `MediaProjection` से स्क्रीन कैप्चर करके भेजता है और `AccessibilityService` (`dispatchGesture`) से रिमोट टच, स्वाइप, Back, Home, Recents कमांड चलाता है।
   - **Viewer Mode (दूसरा फोन):** लाइव स्क्रीन दिखाता है और आपकी उंगली के टच/स्वाइप को Host फोन पर भेजता है।
2. **Relay Server (`server/server.js` और `server/server.py`)** — एक हल्का WebSocket सर्वर जो 6-अंकों के Room Code के ज़रिए दोनों फोन को आपस में जोड़ता है।
3. **Web Viewer (`server/viewer.html`)** — अगर आप दूसरे फोन के ब्राउज़र (Chrome) से ही स्क्रीन देखना और टच कंट्रोल करना चाहें, तो सीधे इसे इस्तेमाल कर सकते हैं।

---

## Step 1: Relay Server चलाना (PC या Cloud पर)

### Node.js से:
```bash
cd server
npm install
node server.js
```
सर्वर `ws://0.0.0.0:8080` पर शुरू हो जाएगा।

### या Python से:
```bash
cd server
pip install websockets
python server.py
```

---

## Step 2: Android App (APK) बनाना

1. **Android Studio** खोलें और `android-app/` फ़ोल्डर को **Open** करें।
2. Gradle Sync पूरा होने दें।
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)** पर क्लिक करें।
4. बनी हुई `app-debug.apk` को दोनों Android फोन्स में इंस्टॉल करें।

---

## Step 3: दोनों फोन्स को कनेक्ट करना

1. **Host Phone (जिसका डिस्प्ले देखना और कंट्रोल करना है):**
   - ऐप खोलें, Server URL डालें (जैसे `ws://192.168.1.5:8080` — एक ही Wi-Fi पर PC का IP)।
   - एक Room Code डालें या Generate करें (जैसे `482910`)।
   - **"Enable Touch Control (Accessibility)"** पर टैप करें और सेटिंग्स में **PhoneMirrorControl** की Accessibility Service को ON करें।
   - **"Start Host (Share Screen)"** पर टैप करें और Screen Capture की अनुमति दें।

2. **Viewer Phone (जिस पर देखना और टच करना है):**
   - ऐप खोलें (या `viewer.html` ब्राउज़र में खोलें), वही Server URL और Room Code (`482910`) डालें।
   - **"Start Viewer (Control)"** पर टैप करें। अब आप पहले फोन की स्क्रीन देख सकते हैं और टच/स्वाइप से उसे चला सकते हैं।
