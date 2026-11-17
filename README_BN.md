# ULTRA AI Assistant — Android project (updated)

এই project Android 8.0+ (minSdk 26), JDK 17 এবং Android Gradle Plugin 8.6.1 দিয়ে তৈরি। Android 13–16 ডিভাইসে পরীক্ষা করার লক্ষ্য রাখা হয়েছে। Cloudflare Worker, D1, KV, R2, API endpoint বা deployment workflow-এর কোনো পরিবর্তন এই ZIP-এ করা হয়নি।

## এই সংস্করণে পরিবর্তন
- AndroidX Core এবং WorkManager dependency যোগ করা হয়েছে; আগের অসম্পূর্ণ dependency setup সংশোধন।
- GitHub Actions workflow-তে Gradle 8.7 নির্দিষ্ট করা হয়েছে এবং clean debug build চালানো হয়।
- বাংলা/English/हिन्दी পছন্দ অনুযায়ী SpeechRecognizer ও Text-to-Speech locale নির্বাচন।
- Wake phrase transcript handling ঠিক করা হয়েছে, যাতে শুধু “Hey Ultra” বললে পরের utterance-এ command নেওয়ার mode চালু থাকে।
- Foreground microphone service, persistent notification এবং optional floating overlay রাখা হয়েছে। Microphone permission ও notification permission ব্যবহারকারীকেই দিতে হবে।
- App Manager-এ launcher apps খোলা এবং Android App Info খোলার সুবিধা। Android-এর সীমাবদ্ধতার কারণে অন্য অ্যাপ জোর করে বন্ধ/নিয়ন্ত্রণ করা হয় না।
- WorkManager-ভিত্তিক delayed reminder scheduling, notification এবং reminder cancel করার সুবিধা।
- Device Administrator receiver, metadata এবং active-state check যোগ করা হয়েছে। `force-lock` policy কেবল ব্যবহারকারী Android-এর official confirmation screen-এ অনুমতি দিলে সক্রিয় হতে পারে; voice command `lock phone` / `ফোন লক` তখনই কাজ করবে।

## গুরুত্বপূর্ণ সীমাবদ্ধতা / Build blocker
- ZIP-এ Cloudflare activation-code verification endpoint, request format বা response schema নেই। তাই Device Administrator activation code verification এখনো সংযুক্ত করা হয়নি এবং verification ছাড়া official admin activation screen চালু করা হয় না। বিদ্যমান API-র সঠিক endpoint/contract পাওয়া গেলে backend পরিবর্তন না করেই Android client-এ যুক্ত করা যাবে।
- Wake word এখনো Android SpeechRecognizer-এর transcript phrase matcher; এটি dedicated offline acoustic hotword model নয়। Continuous microphone recognition Android version, speech provider, battery settings এবং OEM restrictions-এর অধীন।
- Automation-এর arbitrary saved rules এখনো arbitrary phone actions চালায় না। Scheduled reminder-গুলো WorkManager দিয়ে বাস্তবে execute হয়।
- App Manager শুধু Android launcher-এ দৃশ্যমান apps enumerate করে; Android package visibility ও security policy মানা হয়।
- Floating overlay-এর জন্য আলাদা “Display over other apps” permission প্রয়োজন।
- এই environment-এ Gradle executable / Android SDK নেই, তাই এখানে APK build চালিয়ে যাচাই করা সম্ভব হয়নি। GitHub Actions-এ প্রথম build-এর ফলাফল না পাওয়া পর্যন্ত APK-কে build-verified বলা যাবে না।

## GitHub build
1. ZIP extract করে সব ফাইল নতুন repository-র root-এ upload/commit করুন।
2. GitHub → Actions → **Build ULTRA AI APK** → Run workflow।
3. Workflow সফল হলে `ULTRA-AI-debug-apk` artifact download করে `app-debug.apk` install করুন।
4. প্রথমবার Microphone এবং Android 13+ Notification permission দিন। Floating U চাইলে Overlay permission-ও দিন।

## Project structure
- `MainActivity.kt` — navigation এবং permission flow
- `VoiceAssistantService.kt` — foreground voice service, command handling, TTS
- `automation/TaskScheduler.kt` + `TaskReminderWorker.kt` — scheduled reminders
- `admin/UltraDeviceAdminReceiver.kt` — Android Device Administrator receiver
- `permissions/PermissionManager.kt` — runtime permission checks
- `.github/workflows/build-apk.yml` — GitHub Actions APK build
