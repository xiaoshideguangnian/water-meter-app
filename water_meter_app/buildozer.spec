[app]

# (str) Title of your application
title = WaterMeter

# (str) Package name
package.name = watermeter

# (str) Package domain (needed for android/ios packaging)
package.domain = com.example

# (str) Source code where the main.py live
source.dir = .

# (list) Source files to include (let empty to include all the files)
source.include_exts = py,png,jpg,kv,atlas

# (list) Application requirements
requirements = python3,kivy,plyer

# (str) Supported orientation (one of landscape, portrait or all)
orientation = portrait

# (list) Permissions
android.permissions = INTERNET,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE

# (int) Android API to use
android.api = 30

# (int) Minimum API required
android.minapi = 21

# (str) Android NDK version to use
android.ndk = 23b

# (bool) If True, then openssl will be built
android.openssl = True

# (bool) Enable AndroidX support
android.use_androidx = true

# (str) Log level (debug, info, warning, error, critical)
log_level = 2

# (str) Android arch to build for (armeabi-v7a, arm64-v8a, x86, x86_64)
android.arch = arm64-v8a

# (bool) Allow backup
android.allow_backup = True

# (str) Android Gradle dependencies
android.gradle_dependencies = 'com.google.android.material:material:1.6.1'

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug)
log_level = 2

# (str) Path to build output directory
build_dir = ./build

# (bool) Accept SDK license automatically
android.accept_sdk_license = True