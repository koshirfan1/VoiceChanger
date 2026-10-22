# VoiceChanger

## 1. Android app
Android Studio mein `VoiceChanger` folder open karein, real phone par Run karein.
- AI switch OFF = phone par pitch change (0.5x - 2.0x).
- AI switch ON  = awaaz server par jati hai, convert hokar wapas aati hai.

## 2. Server (PC)
    cd server
    pip install -r requirements.txt
    python server.py
Phone aur PC ek hi WiFi par hon. App mein URL daalein: ws://<PC-IP>:8765
(PC IP: Windows `ipconfig`, Linux/Mac `ifconfig`). Firewall mein port 8765 allow karein.

## 3. ElevenLabs (AI awaaz)
    export ELEVENLABS_API_KEY=xxxxx
    export ELEVENLABS_VOICE_ID=xxxxx     # Windows: set VAR=value
    python server.py
Server awaaz ko jumlon mein kaat kar ElevenLabs Voice Changer (POST /v1/voice-changer/{voice_id}) ko bhejta hai.
Ye live stream nahi, har jumle par kuch second ka delay aata hai, aur har bheji hui awaaz API credits kharch karti hai.
Env variables na hon to passthrough mode chalta hai (connection test).
Sirf apni awaaz ya jin ki ijazat ho, unki awaaz use karein.

## 4. Profile matching tool (offline)
    cd tools && pip install -r requirements.txt
    python voice_profile.py analyze female.wav profile.json
    python voice_profile.py convert my_voice.wav profile.json out.wav --formant 1.15
Female recording se pitch aur tone ka profile nikaal kar aapki recording ko us ki taraf morph karta hai.
