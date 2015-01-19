# sana.plugin-library-android
Android library for building poc plugin apps that work with the Android mobile
client.

What's in here
--------------
The libary code is in the PluginAndroidLibrary folder.

Prerequisite
------------
* Android Studio
* JDK 7
* Android SDK 19
* Android Build Tools 19.1

How to import the library
-------------------------
This section assumes that the Android app's project directory and the Library's
project directory are in the same parent directory.

1. In the app's settings.gradle file, add the following lines:
```gradle
include ':PluginAndroidLibrary:plugin_android_library'
project (':PluginAndroidLibrary:plugin_android_library').projectDir = new File(settingsDir, '../PluginAndroidLibrary/plugin_android_library')
```
2. In app folder, build.gradle
Add to the dependencies
```gradle
compile project(path: ':PluginAndroidLibrary:plugin_android_library')
```
