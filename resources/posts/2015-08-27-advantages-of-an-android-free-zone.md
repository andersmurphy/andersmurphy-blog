In Android projects I like to set up an “Android Free Zone”. This is a Java module that doesn’t have any dependencies on the Android libraries. This is where the business logic of the app lives.

Shielding your business logic from changes to any third party library is always a good thing. But the “Android Free Zone” has other benefits. No need to launch an emulator or use a framework like [Robolectric](http://robolectric.org/) to test your business logic. This helps keep Red-Green-Refactor cycles short, for a more productive TDD workflow.

<!--more-->

Test writing becomes easier as there are no dependencies on Android objects. These are often tightly coupled making them laborious to instantiate, leading to unnecessarily complicated tests.

What about testing the modules that do depend on the Android libraries?  The simple answer? Don’t. Instead keep the code, that depends on Android libraries, simple and without logic ([Humble Object Pattern](http://xunitpatterns.com/Humble%20Object.html)). The android layer of the app effectively becomes a thin adapter layer, that doesn’t need to be brought under test.

Check out [this project](https://github.com/andersmurphy/chain) for an example of how to set up an "Android Free Zone".
