<div align="center">

# Prosanti Nibash

### A personal Android app for managing tenants and building expenses.

An independent project built to keep the day-to-day records of our building together: flats, tenancies, rent, payments, deposits, expenses and maintenance.

**Personal project · Built for our building · Shared as open source**

</div>

---

## App demo

This repository documents a personal Android app used to manage tenants and building expenses. Screenshots are not included; the project is shared as source code and setup documentation.

## What it does

- Keep track of flats, tenants, and tenancy details.
- Record rent, payments, deposits, and building expenses.
- Track maintenance and recurring expenses.
- Keep supporting documents and export book records.
- Use a local PIN or device biometrics to protect the app interface.

## About this project

Prosanti Nibash is a **personal project** made for managing tenants and expenses in our own building. It is shared publicly in case the code or approach is useful to others. It is not a commercial property-management service, and it is not presented as production-ready for other buildings.

The app currently has no cloud user authentication. The local PIN/biometric lock protects the device interface only; it does **not** authenticate access to the shared Supabase backend. Review the access model and configure your own isolated backend before using real tenant or financial information. See [`docs/supabase-setup.md`](docs/supabase-setup.md) for important security details and setup instructions.

## Built with

- Kotlin and Jetpack Compose
- Android Gradle Plugin and Gradle
- Supabase for shared data and file storage

## Build

Requires Android Studio or a compatible Android SDK, JDK 17, and Android SDK platform 36.

1. Clone this repository.
2. Configure your own development Supabase project as described in [`docs/supabase-setup.md`](docs/supabase-setup.md). Keep credentials local; never commit service-role keys or database passwords.
3. Build and run the `app` configuration from Android Studio, or run:

   ```sh
   ./gradlew assembleDebug
   ```

## License

Licensed under the [Apache License 2.0](LICENSE).
