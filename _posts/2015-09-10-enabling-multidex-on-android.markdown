---
layout: post
title:  "Enabling MultiDex on Android"
---

As the codebase of an Android app continues to grow, it will eventually hit “The 65K limit”. This limit is characterised by the following build error:

{% highlight java %}
trouble writing output:
Too many field references: 131000; max is 65536.
You may try using --multi-dex option.
{% endhighlight %}

65,536 is the total number of method that can be referenced by the code within a single Dalvik Executable (dex) bytecode file. This includes Android framework methods, library methods, and of course your own methods.

What pushes most apps over "The 65k limit" is the inclusion of external libraries. Often when importing external library, only a few methods are actually used. However, all the unused methods in that library will still count to "The 65k limit".

The majority of apps can still be built as long as a code shrinking tool like [ProGuard] is used. ProGuard removes any unused methods, meaning they don't end up counting towards the limit. The downside is that the build time of the application increases considerably. This can be problematic if you like developing in short iterations with a tight feedback loop.

The other option, and the only option for apps that exceed the limit even after using tools like ProGuard, is MultiDex. MultiDex monkey patches the application context class loader in order to allow the loading of classes from more than one dex file. This allows the app to overcome "The 65k limit" by splitting it into multiple dex files. The downside is increased build time. However, this increase is significantly smaller than the one incurred by ProGuard.

If your app has hit “The 65K limit”, don't panic. Enabling MultiDex is relatively simple.

First add the following to the application class:

{% highlight java %}
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    MultiDex.install(this);
}
{% endhighlight %}

Then add the following to the app **build.gradle** file:

{% highlight groovy %}
defaultConfig {
    ...
    multiDexEnabled true
}

dependencies {
    ...
    compile 'com.android.support:multidex:1.0.0'
}
{% endhighlight %}

*Note: Make sure you are using buildToolsVersion '21.1.2' or higher.*

Finally, sync the project with the gradle files and MultiDex will now be enabled. No more 65k limit for your app!


[ProGuard]: http://proguard.sourceforge.net/
