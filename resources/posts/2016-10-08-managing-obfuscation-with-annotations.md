Title: Managing obfuscation with annotations

Obfuscation is when you deliberately make source code difficult to read. Often code is obfuscated to conceal its purpose and deter reverse engineering. Most obfuscation tools do this by replacing class, method and field names with gibberish.

<!--more-->

For example:

```java
public final class TwitterFeedJson {
  public List<Tweet> tweets
  ...
}
```

Becomes:

```java
public final class a {
  public b<c> d
  ...
}
```

If you use [ProGuard](https://proguard.sourceforge.net/) to obfuscate the code in your project you have most likely had your app crash when it needs to use reflection. The stack trace will look something like this:

```java
java.lang.NullPointerException: Attempt to read from field 'java.util.List com.example.a.b' on a null object reference
```

This is because ProGuard has obfuscate the class name, method name or field name that you are trying to use reflectively. A common example is when using GSON to parse JSON.

The simplest way to get around this problem is to add the appropriate `-keep` line to your ProGuard file. However, this is tedious and error prone and is often forgotten.  Instead this post will cover another solution that is my opinion more practical.

## Step 1: Create a DontObfuscate annotation
Create an annotation called **DontObfuscate** in your project. The retention policy should be set to CLASS, as we don't need the annotation at runtime, but will need it for bytecode-level post-processing as that's when ProGuard performs obfuscation.

```java
@Retention(RetentionPolicy.CLASS)
public @interface DontObfuscate {
}
```

## Step 2: Update your projects ProGuard file
Add the following lines to your project **proguard-rules.pro** file.

```base
-keep class com.your.package.name.DontObfuscate
-keep @com.your.package.name.DontObfuscate class * { *; }
```

This will ensure ProGuard doesn't obfuscate any class that has the DonObfuscate annotation.

## Step 3: Annotate the classes that you don't want obfuscated
Add the DontObfuscate annotation to the classes you don't want ProGuard to obfuscate.

```java
@DontObfuscate
public final class TwitterFeedJson {
    ...
}
```

That's all there is to it, now you can prevent classes from being obfuscated with an annotation without having to modify your **proguard-rules.pro** file, it also helps document your code.

It is worth pointing out that this annotation will not prevent inner classes from being obfuscated. So if you don't want a class' inner class to be obfuscated make sure to annotate them as well.

Check out [this project](https://github.com/andersmurphy/demo-app/commit/5a89952a4d6cd7bf2ca7119b8468b763fe9ead87) for an example of how to set up the DontObfuscate annotation in your app.
