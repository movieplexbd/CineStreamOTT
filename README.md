# CineStream — OTT Movie Streaming App

A production-ready Android OTT app built with Kotlin, Firebase, ExoPlayer, and MVVM architecture.

---

## 🚀 Setup Instructions

### 1. Firebase Configuration
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select project **movies-bee24**
3. Add an Android app with package name `com.ottapp.moviestream`
4. Download `google-services.json` and replace the one in `app/`
5. Enable **Google Sign-In** in Authentication → Sign-in method
6. Copy your **Web Client ID** from Authentication → Sign-in method → Google → Web SDK config
7. Open `app/src/main/java/com/ottapp/moviestream/util/Constants.kt`
8. Replace `YOUR_WEB_CLIENT_ID.apps.googleusercontent.com` with your actual Web Client ID

### 2. Gradle Wrapper JAR
The `gradle-wrapper.jar` is not included (binary file). Generate it:
```bash
# Option A — if you have Gradle installed:
gradle wrapper --gradle-version 8.2

# Option B — Android Studio will auto-generate it when you open the project
```

### 3. Open in Android Studio
1. Open Android Studio → File → Open → select `OTTStreamingApp/` folder
2. Let Gradle sync complete
3. Run on device or emulator (API 24+)

---

## 📱 Features
- ✅ Google Sign-In (Firebase Auth)
- ✅ Free / Premium subscription system
- ✅ 2 Test movies (free access)
- ✅ ExoPlayer with seek, speed, PiP, resume
- ✅ Background download to private storage
- ✅ Offline playback
- ✅ Real-time Firebase search with filters
- ✅ MVVM + Navigation Component
- ✅ GitHub Actions CI/CD → APK artifact

## 🔥 Firebase Database Structure
```
movies/
  {id}/
    title, description, bannerImageUrl
    videoStreamUrl, downloadUrl
    category ("Bangla Dubbed" / "Hindi Dubbed")
    imdbRating, trending, testMovie

users/
  {uid}/
    email, displayName, photoUrl
    subscriptionStatus ("free" / "premium")
    subscriptionExpiry (unix timestamp ms)
```

## ⚙️ GitHub Actions
Push to `main` branch → auto-builds Debug + Release APK → available as Artifacts.

To use `google-services.json` securely in CI:
1. Base64 encode it: `base64 app/google-services.json`
2. Add as GitHub Secret: `GOOGLE_SERVICES_JSON`
3. Uncomment the decode step in `.github/workflows/android-build.yml`

---

## 📁 Project Structure
```
app/src/main/
├── java/com/ottapp/moviestream/
│   ├── data/model/          Movie, User, DownloadedMovie
│   ├── data/repository/     Auth, Movie, User, Download repos
│   ├── ui/home/             HomeFragment + ViewModel
│   ├── ui/movies/           MoviesFragment + ViewModel
│   ├── ui/search/           SearchFragment + ViewModel
│   ├── ui/download/         DownloadFragment + ViewModel
│   ├── ui/profile/          ProfileFragment + ViewModel
│   ├── ui/detail/           MovieDetailBottomSheet
│   ├── ui/player/           PlayerActivity (ExoPlayer)
│   ├── adapter/             Banner, MovieGrid, Download adapters
│   ├── service/             DownloadService (foreground)
│   └── util/                Constants, Extensions
└── res/
    ├── layout/              All XML layouts
    ├── navigation/          nav_graph.xml
    ├── drawable/            Shapes, gradients, icons
    └── values/              colors, strings, themes, dimens
```
