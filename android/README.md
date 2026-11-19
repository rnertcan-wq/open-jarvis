# M.G Wp Avcı — Android

WhatsApp gruplarındaki fırsat bildirimlerini yerel olarak okuyup fiyat/indirim analiz eden, yüksek indirimlerde gelen arama benzeri tam ekran alarm çalan Android uygulaması.

- Sürüm: **1.2.0** (`versionCode 3`)
- Paket: `com.firsatavcisi.app`
- İnternet izni yoktur; tüm analiz ve OCR cihazda yapılır.

## Bu dizin

Bu klasör, GitHub Actions ile APK üretmek için doğrudan derlenen Android projesidir.
Derleme: `.github/workflows/build-mg-wp-avci.yml` → `gradle assembleDebug`.

Yerel derleme için JDK 17 + Android SDK 35 gerekir:

```bash
cd android
gradle assembleDebug
```

## 1.2.0 özeti

- Bildirim yakalama: `EXTRA_MESSAGES` desteği (çok mesajlı özet bildirimlerde metin kaybolmaz), çoklu grup / tüm sohbetler modu, kalp atışı (son okunan olay).
- Ses garantisi: `STREAM_ALARM` geçici yükseltme, `USAGE_ALARM`, 90 sn otomatik susma (ayarlanabilir), fırsat kuyruğu.
- Parser: `₺28` önek biçimi, kargo/taksit/alt limit filtreleme, eski fiyat bağlamı, kullanıcı tanımlı eşikler.
- Kurulum sihirbazı: bildirim izni, tam ekran alarm, kesin alarm, pil optimizasyonu durumları.
- Uyku modu: belirlenen saatlerde alarm/bildirim durur, fırsatlar sessizce kaydedilir.
- OCR: ML Kit ile tamamen çevrimdışı görsel metin okuma.
