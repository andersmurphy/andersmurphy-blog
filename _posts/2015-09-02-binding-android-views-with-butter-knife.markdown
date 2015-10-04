---
layout: post
title:  "Binding Android views with Butter Knife"
---

As Android apps get more complex activities tend to get cluttered with calls to `findViewById(id)`. This unnecessary boilerplate takes time to write and makes code harder to read. [Butter Knife] to the rescue!

Butter Knife is a field and method binding library for Android views which uses annotations to reduce boilerplate.

<!--more-->

This is a quick tutorial for getting started with Butter Knife.

###Step 1: Download dependency
Add the following dependency to the app's **build.gradle** file:

{% highlight groovy %}
compile 'com.jakewharton:butterknife:7.0.1'
{% endhighlight %}

###Step 2: Add annotation
In the activity, add the `@Bind` annotation before the declaration of the view field you want to bind to, passing in the corresponding view `R.id` as an argument:

{% highlight java %}
@Bind(R.id.true_button)
Button trueButton;
{% endhighlight %}

###Step 3: Remove boilerplate
Now remove the old view binding:

{% highlight java %}
trueButton = (Button) findViewById(R.id.true_button);
{% endhighlight %}


###Step 4: Call bind
In the `onCreate()` method of the activity, before using the view, call bind on the Butterknife class:

*Note: you only have to do this once, regardless of how many views you are binding.*

{% highlight java %}
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  setContentView(R.layout.activity_quiz);
  ButterKnife.bind(this);

  trueButton.setOnClickListener(trueButtonOnClickListener());

  ...
{% endhighlight %}

We're done. Butter Knife will now handle the instantiation of the view for you.

*Note: In versions of Butter Knife prior to 7.0.0 the `@Bind` annotation was called `@InjectView` and the `Butterknife.bind(this)` method was called `Butterknide.inject(this)`.*


[Butter Knife]: https://github.com/JakeWharton/butterknife
