# Xaker Ovchisi

Android himoya ilovasi: telefon yordamida xakerlarni va shubhali faoliyatni aniqlaydi.

## Bo'limlar
- **Tuzoq (honeypot):** soxta SSH/Telnet/HTTP/ADB portlarini ochadi, ulanishga uringan IP'ni qayd etadi.
- **Tarmoq:** Wi-Fi tarmog'idagi qurilmalarni topadi, yangi qurilma chiqsa ogohlantiradi.
- **Ilovalar:** xavfli ruxsatlar, maxsus imkoniyatlar, yashirin va rasmiy do'kondan tashqari ilovalar.
- **APK:** biror kimdir yuborgan APK yoki boshqa faylni VirusTotal (60+ antivirus) orqali tekshiradi.
  Faylni ilova ichidan tanlash mumkin, yoki boshqa ilovadan (Telegram, fayl menejeri) to'g'ridan-to'g'ri
  "Ulashish" orqali yuborish mumkin. Bepul VirusTotal API kalit kerak (virustotal.com > Profil > API Key).
- **Log:** server loglarini tahlil qilib, hujumchi IP'larni topadi.
- **Jurnal:** barcha hodisalar, Telegram ogohlantirish sozlamasi.

## APK olish
1. Fayllarni yangi GitHub repozitoriyga yuklang (`main` branch).
2. GitHub > Actions > "Build APK" ishga tushadi.
3. Tugagach, Artifacts ichidan `XakerOvchisi-APK` ni yuklab, o'rnating.
