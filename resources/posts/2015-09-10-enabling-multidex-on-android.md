As the codebase of an Android app continues to grow, it will eventually hit “The 65K limit”. This limit is characterised by the following build error:

```java
trouble writing output:
Too many field references: 131000; max is 65536.
You may try using --multi-dex option.
```

<!--more-->

65,536 is the total number of method that can be referenced by the code within a single Dalvik Executable (dex) bytecode file. This includes Android framework methods, library methods, and of course your own methods.

What pushes most apps over "The 65k limit" is the inclusion of external libraries. Often when importing external library, only a few methods are actually used. However, all the unused methods in that library will still count to "The 65k limit".

The majority of apps can still be built as long as a code shrinking tool like [ProGuard](http://proguard.sourceforge.net/) is used. ProGuard removes any unused method references, meaning they don't end up counting towards the limit.

The other option, and the only option for apps that exceed the limit even after using tools like ProGuard, is MultiDex. MultiDex monkey patches the application context class loader in order to allow the loading of classes from more than one dex file. This allows the app to overcome "The 65k limit" by splitting it into multiple dex files.

If your app has hit “The 65K limit”, don't panic. Enabling MultiDex is relatively simple.

### Step 1: Install
Add the following to the application class:

```java
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    MultiDex.install(this);
}
```

### Step 2: Enable and download dependency
Add the following to the app **build.gradle** file:

```groovy
defaultConfig {
    ...
    multiDexEnabled true
}

dependencies {
    ...
    compile 'com.android.support:multidex:1.0.0'
}
```

*Note: Make sure you are using buildToolsVersion '21.1.2' or higher.*

### Step 3: Sync
Sync the project with the gradle files and MultiDex will now be enabled. No more 65k limit for your app!

Check out [this project](https://github.com/andersmurphy/chain/commit/4462327da5849f6ac7c4a41e290d84dc6f016b21) for an example of how to set up MultiDex.
