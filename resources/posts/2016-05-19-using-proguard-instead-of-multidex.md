Title: Using Proguard instead of multidex

One of the downsides of using MultiDex to overcome "The 65k limit" is that build times can increase significantly.
Another option is to use [ProGuard](http://proguard.sourceforge.net/). ProGuard overcomes "The 65k limit" by removing unused method references,
this can make a big difference if you are using large third party libraries like [Guava](https://github.com/google/guava). If configured
correctly (disabling optimization/obfuscation) ProGuard can have little to no negative [impact on your build times](https://image.slidesharecdn.com/jackandjilldroidconlondon2015-160314154239/95/eric-lafortune-the-jack-and-jill-build-system-16-638.jpg?cb=1457972343) (in the case of larger project it can even decrease build time).

<!--more-->

### Step 1: Create ProGuard main file
Create a file named **proguard-main.pro** in the app level of your project directory. This file is where you add proguard settings that are shared between your debug and release builds.

### Step 2: Create ProGuard debug header file
Create a file named **proguard-header-debug.pro** in the app level of your project directory. This file is where you add proguard settings that are specific to debug builds.

```bash
-dontoptimize
-dontobfuscate
```

We have added `-dontoptimize` to disable code optimization and `-dontobfuscate` to disable obfuscation. If these were not disabled the debugger would struggle to find the executable code that maps to your source and if it did it would be hard to decipher as the code would be obfuscated. Disabling optimization/obfuscation also means that ProGuard will have little to no impact on build times.

### Step 3: Create ProGuard release header file
Create a file named **proguard-header-release.pro** in the app level of your project directory. This file is where you add proguard settings that are specific to release builds.

```bash
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
```

We have specified the number of optimization passes and which optimizations ProGuard should do. More importantly we have removed `-dontoptimize` and `-dontobfuscate` meaning this build will be optimized and obfuscated.

### Step 4: Update the app build.gradle
Update your app's the buildTypes block in your **build.gradle** file as shown below.

```groovy
buildTypes {
    release {
        minifyEnabled true
        signingConfig signingConfigs.release
        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-header-release.pro', 'proguard-main.pro'
    }
    debug {
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-header-debug.pro', 'proguard-main.pro'
    }
}
```

The release build uses the release header file and the debug build uses the debug header file. We have also set `minifyEnabled true` in both builds. This means that regardless of which build we are using ProGuard will strip out any unused method references. This should keep your app bellow "The 65k limit" even if you are using large third party libraries. More importantly, if set up in this way, ProGuard will have little to no negative [impact on your build times](https://image.slidesharecdn.com/jackandjilldroidconlondon2015-160314154239/95/eric-lafortune-the-jack-and-jill-build-system-16-638.jpg?cb=1457972343) (unlike MultiDex), keeping your development feedback loop tight.

Check out [this project](https://github.com/andersmurphy/chain/commit/9d2241a2a6d2571696a1d3ad5ba37e521d8641f5) for an example of how to set up ProGuard in your app.
