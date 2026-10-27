# Dafmix Android

Dafmix sitesi için yerel Android uygulaması. İçerik indir, internet yokken uygulamadan izlemeye devam et.

## APK'yı derleme (GitHub Actions)
1. github.com'da yeni (private olabilir) bir repo aç.
2. Bu klasörün İÇERİĞİNİ (`.github` klasörü dahil) repoya yükle.
3. Repo → **Actions** → "APK derle" → **Run workflow**. (main'e push edince de otomatik çalışır.)
4. Bitince çalışmanın altındaki **dafmix-apk** artifact'ını indir, zip'ten APK'yı çıkar.
5. APK'yı telefona at, "bilinmeyen kaynaklardan yükleme"ye izin ver, kur.

## Kullanım
- Giriş ekranında site hesabınla giriş yap (sunucu adresi varsayılan https://dafmix.xyz, değiştirilebilir).
- Detay sayfasında film/bölümün yanındaki indir simgesine bas. İndirilenler sekmesinde ilerleme görünür.
- İnternet yoksa uygulama İndirilenler sekmesinde açılır; kaldığın yerden devam eder.
- Profil sekmesinden: sadece Wi-Fi'da indir, indirme kalitesi, hepsini sil.

## Not
İndirme için aktif abonelik (onaylı) gerekir; sunucu kaynak vermezse indirme başlamaz.
