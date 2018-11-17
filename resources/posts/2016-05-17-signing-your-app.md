Android requires that all apps be digitally signed with a certificate before they can be installed. To install a release version of your app on a device it will need to signed. Thankfully signing an app is relatively straightforward.

<!--more-->

### Step 1: Generate a private key
To generate a private key enter the following into your terminal, making sure to replace `myexample` with the
desired keystore name, and `examplekey` with the desired alias. You will then be prompted to answer some questions
in order to generate the key.

`keytool -genkey -v -keystore myexample.keystore -alias examplekey -keyalg RSA -keysize 2048 -validity 10000`

### Step 2: Update the app build.gradle

Add the `signingConfig` block to your app's **build.gradle** file. Set the `storeFile` field to the location of your keystore. Set the `storePassword`, `keyAlias`, `keyPassword` fields to their respective values. Finally set the `signingConfig` field in the `buildTypes` `release` block to `signingConfigs.release`.

```groovy
android {
    ...
    signingConfigs {
        release {
            storeFile file("/Users/anders/myexample.keystore")
            storePassword "password"
            keyAlias "examplekey"
            keyPassword "password"
        }
    }

    buildTypes {
        release {
            ...
            signingConfig signingConfigs.release
            ...
        }
    }
    ...
}
```

## Keeping your signing credentials secure
If you use a hosted git repository service like GitHub you might not want to commit your keystore and password details for security reasons. You can do this by using a **gradle.properties** file.

### Step 1: Create a gradle.properties file
Create a file named **gradle.properties** in the root level of your project directory.

### Step 2: Specify the fields in the gradle.properties file
Add the `myExampleKeystore`, `myExampleKeystorePassword`,  `myExampleKeyAlias` and `myExampleKeyPassword` fields to the **gradle.properties** file, and set them to their respective values.
```bash
myExampleKeystore=/Users/anders/Projects/myexample.keystore
myExampleKeystorePassword=password
myExampleKeyAlias=myexample
myExampleKeyPassword=password
```

### Step 3: Update the app build.gradle
Change the values of the `storeFile` , `storePassword`, `keyAlias` and `keyPassword` to reference the values stored in the **gradle.properties** file

```groovy
    signingConfigs {
        release {
            storeFile file(myExampleKeystore)
            storePassword myExampleKeystorePassword
            keyAlias myExampleKeyAlias
            keyPassword myExampleKeyPassword
        }
    }
```

### Step 4: Update .gitignore
Add  **gradle.properties** to your projects **.gitignore** file. So that it doesn't get tracked by git. This ensures that your signing credentials will only be stored locally on your machine.

Check out [this project](https://github.com/andersmurphy/chain/commit/531597724d68cf27d6e9fdd2e88f54fe4082c841) for an example of how to sign your app's release build.
